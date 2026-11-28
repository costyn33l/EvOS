package com.stradaos.launcher;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Escáner ELM327 por Bluetooth de bajo consumo (BLE, Android 4.3+): Vgate iCar Pro BLE, Veepeak OBDCheck BLE, OBDLink
 * CX, vLinker MC+/FS, Konnwei y los clones «ELM327 V1.5 BLE». No se emparejan: se buscan y se conectan directo.
 * Por dentro son un puerto serie: una característica para escribir y otra que avisa (notify) con lo que contesta el
 * escáner. Aquí se convierten en un {@link InputStream} / {@link OutputStream} como los del Bluetooth clásico.
 */
@TargetApi(18)
final class BleSerial {
    /** Servicio, característica que avisa, característica para escribir (null = la misma). */
    private static final String[][] KNOWN = {
        {"0000fff0-0000-1000-8000-00805f9b34fb", "0000fff1-0000-1000-8000-00805f9b34fb", "0000fff2-0000-1000-8000-00805f9b34fb"},
        {"0000ffe0-0000-1000-8000-00805f9b34fb", "0000ffe1-0000-1000-8000-00805f9b34fb", null},
        {"000018f0-0000-1000-8000-00805f9b34fb", "00002af0-0000-1000-8000-00805f9b34fb", "00002af1-0000-1000-8000-00805f9b34fb"},
        {"e7810a71-73ae-499d-8c15-faa9aef0c3f2", "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f", null},
    };
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    final InputStream in;
    final OutputStream out;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rx, tx;
    private volatile boolean closed;
    private final byte[] buf = new byte[16384];
    private int head, tail;           // cola circular de lo que llegó
    private final CountDownLatch ready = new CountDownLatch(1);
    private final Semaphore writing = new Semaphore(1);
    private volatile String failure;

    private BleSerial() {
        in = new InputStream() {
            @Override public int available() { synchronized (buf) { return (tail - head + buf.length) % buf.length; } }
            @Override public int read() throws IOException {
                long end = System.currentTimeMillis() + 10000;
                synchronized (buf) {
                    while (head == tail) {
                        if (closed) return -1;
                        long left = end - System.currentTimeMillis();
                        if (left <= 0) throw new java.net.SocketTimeoutException("BLE");
                        try { buf.wait(left); } catch (InterruptedException e) { throw new IOException("Interrumpido"); }
                    }
                    int b = buf[head] & 0xFF;
                    head = (head + 1) % buf.length;
                    return b;
                }
            }
        };
        out = new OutputStream() {
            @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}, 0, 1); }
            @Override public void write(byte[] b, int off, int len) throws IOException {
                // de a 20 bytes (lo que cabe en un paquete BLE sin cambiar el MTU)
                for (int i = 0; i < len; i += 20) {
                    int n = Math.min(20, len - i);
                    byte[] chunk = new byte[n];
                    System.arraycopy(b, off + i, chunk, 0, n);
                    send(chunk);
                }
            }
        };
    }

    static BleSerial open(Context c, String mac, int timeoutMs) throws IOException {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) throw new IOException("Este equipo no tiene Bluetooth");
        if (!a.isEnabled()) throw new IOException("El Bluetooth está apagado");
        BluetoothDevice d = a.getRemoteDevice(mac);
        final BleSerial s = new BleSerial();
        BluetoothGattCallback cb = new BluetoothGattCallback() {
            @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
                if (state == BluetoothGatt.STATE_CONNECTED) {
                    g.discoverServices();
                } else if (state == BluetoothGatt.STATE_DISCONNECTED) {
                    s.failure = "El escáner Bluetooth LE se desconectó";
                    s.closed = true;
                    synchronized (s.buf) { s.buf.notifyAll(); }
                    s.ready.countDown();
                    s.writing.release();
                }
            }
            @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
                if (!s.pick(g)) s.failure = "Este equipo Bluetooth LE no parece un escáner OBD (no tiene un puerto serie)";
                else if (!s.subscribe(g)) s.failure = "No se pudo escuchar al escáner Bluetooth LE";
                else return;     // listo cuando se confirme la suscripción
                s.ready.countDown();
            }
            @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor de, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) s.failure = "No se pudo escuchar al escáner Bluetooth LE";
                s.ready.countDown();
            }
            @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
                s.received(ch.getValue());
            }
            @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
                if (s.writing.availablePermits() == 0) s.writing.release();
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= 23) s.gatt = d.connectGatt(c, false, cb, BluetoothDevice.TRANSPORT_LE);
        else s.gatt = d.connectGatt(c, false, cb);
        if (s.gatt == null) throw new IOException("No se pudo conectar con el escáner Bluetooth LE");
        try {
            if (!s.ready.await(timeoutMs, TimeUnit.MILLISECONDS)) s.failure = "El escáner Bluetooth LE no contesta: ¿está enchufado y cerca?";
        } catch (InterruptedException e) { s.failure = "Interrumpido"; }
        if (s.failure != null) {
            s.close();
            throw new IOException(s.failure);
        }
        return s;
    }

    /** Busca el par de características del puerto serie: primero las conocidas, después cualquiera que sirva. */
    private boolean pick(BluetoothGatt g) {
        for (String[] k : KNOWN) {
            BluetoothGattService sv = g.getService(UUID.fromString(k[0]));
            if (sv == null) continue;
            BluetoothGattCharacteristic r = sv.getCharacteristic(UUID.fromString(k[1]));
            BluetoothGattCharacteristic w = k[2] != null ? sv.getCharacteristic(UUID.fromString(k[2])) : r;
            if (r != null && w != null) { rx = r; tx = w; return true; }
        }
        for (BluetoothGattService sv : g.getServices()) {
            String u = sv.getUuid().toString();
            if (u.startsWith("00001800") || u.startsWith("00001801") || u.startsWith("0000180a") || u.startsWith("0000180f")) continue;
            BluetoothGattCharacteristic r = null, w = null;
            for (BluetoothGattCharacteristic ch : sv.getCharacteristics()) {
                int p = ch.getProperties();
                if (r == null && (p & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) r = ch;
                if (w == null && (p & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) w = ch;
            }
            if (r != null && w != null) { rx = r; tx = w; return true; }
        }
        return false;
    }

    private boolean subscribe(BluetoothGatt g) {
        if (!g.setCharacteristicNotification(rx, true)) return false;
        BluetoothGattDescriptor de = rx.getDescriptor(CCCD);
        if (de == null) { ready.countDown(); return true; }      // algunos avisan sin descriptor
        boolean indicate = (rx.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0;
        de.setValue(indicate ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return g.writeDescriptor(de);
    }

    private void received(byte[] v) {
        if (v == null) return;
        synchronized (buf) {
            for (byte b : v) {
                int next = (tail + 1) % buf.length;
                if (next == head) head = (head + 1) % buf.length;    // lleno: se pierde lo más viejo
                buf[tail] = b;
                tail = next;
            }
            buf.notifyAll();
        }
    }

    private void send(byte[] chunk) throws IOException {
        if (closed) throw new IOException(failure != null ? failure : "El escáner Bluetooth LE se desconectó");
        boolean noResp = (tx.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
        try {
            if (!writing.tryAcquire(2000, TimeUnit.MILLISECONDS)) writing.drainPermits();
        } catch (InterruptedException e) { throw new IOException("Interrumpido"); }
        tx.setWriteType(noResp ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        tx.setValue(chunk);
        boolean ok = false;
        for (int i = 0; i < 5 && !ok; i++) {          // el Bluetooth de Android a veces está ocupado un instante
            ok = gatt.writeCharacteristic(tx);
            if (!ok) try { Thread.sleep(20); } catch (InterruptedException e) { throw new IOException("Interrumpido"); }
        }
        if (!ok) { writing.release(); throw new IOException("No se pudo escribir al escáner Bluetooth LE"); }
        if (noResp) { try { Thread.sleep(8); } catch (InterruptedException ignored) { } if (writing.availablePermits() == 0) writing.release(); }
    }

    void close() {
        closed = true;
        synchronized (buf) { buf.notifyAll(); }
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Throwable ignored) { }
        gatt = null;
    }
}
