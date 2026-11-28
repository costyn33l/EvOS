#!/bin/bash
# Prueba de humo en un emulador: instala la versión de prueba, abre cada pantalla, toma capturas y revisa
# que no haya cierres. Uso: smoke.sh API APK CARPETA
API=$1; APK=$2; OUT=$3
PKG=com.evos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
mkdir -p "$OUT"
adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 10
adb shell input keyevent 82 || true

# Pantalla de carro: 1024x600, densidad 160
if [ "$API" -ge 18 ]; then adb shell wm size 1024x600; adb shell wm density 160
else adb shell am display-size 1024x600; adb shell am display-density 160; fi
sleep 5

shot() {
  # screencap en /data/local/tmp (en Android 4.4–5 la /sdcard del emulador es de solo lectura). Si sale en blanco
  # (pasa en emuladores de Android 4.1 con GPU por software), se usa la captura del propio emulador.
  rm -f /tmp/shot.png; rm -rf /tmp/emushot; mkdir -p /tmp/emushot
  adb shell screencap -p /data/local/tmp/s.png >/dev/null 2>&1 && adb pull /data/local/tmp/s.png /tmp/shot.png >/dev/null 2>&1
  if [ ! -s /tmp/shot.png ] || [ "$(stat -c %s /tmp/shot.png)" -lt 15000 ]; then
    adb emu screenrecord screenshot /tmp/emushot >/dev/null 2>&1; sleep 2
    f=$(ls -t /tmp/emushot/*.png 2>/dev/null | head -1)
    [ -n "$f" ] && cp "$f" /tmp/shot.png
  fi
  [ -s /tmp/shot.png ] && cp /tmp/shot.png "$OUT/$1.png"
}
go() {  # abre StradaOS con un valor de prueba y espera
  adb shell am start -n $ACT --es prueba "$1" >/dev/null
  sleep "${2:-6}"
}
back() { adb shell input keyevent 4; sleep 2; }

if [ "$API" -ge 23 ]; then adb install -r -g "$APK"; else adb install -r "$APK"; fi
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
adb logcat -c

adb shell am start -n $ACT >/dev/null; sleep 12
shot 01-asistente
back
go saltar 10;      shot 02-inicio
# temas EvOS (1.0): los cinco temas, sin música, 4 datos, auto simulado y la página de datos en vivo
for t in aero monolith pulse neon inferno; do go tema-ev:$t 8; shot 60-ev-$t; done
go musica-ev:0 6;  shot 61-ev-inferno-sin-musica
go obd-demo 25
go tema-ev:inferno 10; shot 62-ev-inferno-obd
go ev-n:4 8;       shot 63-ev-inferno-4-datos
go ev-datos 8;     shot 64-ev-datos-en-vivo
back
go tema-ev:pulse 10; shot 65-ev-pulse-obd
go tema-ev:aero 10;  shot 66-ev-aero-obd
go tema-ev:neon 10;  shot 67-ev-neon-obd
go tema-ev:monolith 10; shot 68-ev-monolith-obd
go ajustes:11 6;   shot 69-ev-personalizacion
go musica-ev:1 4
go ev-n:2 4
# Showroom (1.1): todas las secciones con el auto simulado, los modelos y sin escáner
go tema-ev:showroom 10; shot 70-sr-inicio
go sr:2 6;     shot 72-sr-musica
go sr:3:0 6;   shot 73-sr-presentacion
go sr:3:1 6;   shot 74-sr-instrumentos
go sr:3:2 8;   shot 75-sr-parametros
go obdb:Volkswagen-Passat 12
go ev-datos 8;    shot 75b-ev-todos-datos-dpf
go ev-datos-fin 6; shot 75c-ev-otras-senales-j1979; back
go sr:3:3 6;   shot 76-sr-diagnostico
go sr:3:5 6;   shot 78-sr-viaje
# cuadros de instrumentos (1.2): en Instrumentos, en el inicio del Showroom y en el de un tema EvOS
for c in telemetry sport classic; do go cl:instr:$c 8; go sr:3:1 6; shot 90-cl-instr-$c; done
go cl:instr:evos 4
for c in telemetry sport classic; do go cl:home:$c 8; go sr:0 5; shot 91-cl-sr-$c; done
go tema-ev:inferno 8
for c in telemetry sport classic; do go cl:home:$c 8; shot 92-cl-inferno-$c; done
go tema-ev:aero 8; shot 93-cl-aero-classic
go cl:home:theme 4
go tema-ev:showroom 8
go sr-modelo:suv:blue 8; go sr:3:6 6; shot 79-sr-modelo
for m in sedan wagon coupe mpv; do go sr-modelo:$m:red 6; go sr:0 4; shot 80-sr-$m; done
go sr-modelo:hatch:silver 6
go ajustes:11 6; shot 85-sr-personalizacion
go obd-off 6; go sr:0 6; shot 86-sr-sin-obd
go sr:3:2 6;   shot 87-sr-parametros-sin-obd
go sr:1 8;     shot 71-sr-mapa
# tráfico de Alemania (Autobahn, InfraNode): un carro en la A3 cerca de Limburg, datos reales y un atasco de prueba
go infra-demo 6; go mapa-zoom:14 22; shot 71b-sr-mapa-trafico-de
go mapa-rapido 4; shot 71c-mapa-ajustes-rapidos; back
go sr:3:4 6;   shot 77-sr-mantenimiento
go sr:0 4
go tema-ev:aero 6
go boton:3;        shot 03-menu-boton
back
go elegir:3;       shot 04-elegir-app
back
go ajustes:4;      shot 05-ajustes-barra
go ajustes:8;      shot 06-ajustes-musica
go ajustes:7;      shot 07-ajustes-mapa
go ajustes:8;      shot 08-ajustes-acerca
go musica;         shot 09-elegir-musica
back
go cajon;          shot 10-todas-las-apps
go inicio 4
# pantalla de inicio: aviso, lista o ventana del sistema para elegirla, y Ajustes → Sistema
go aviso-inicio;   shot 18-aviso-inicio
back
go fijar-inicio 5; shot 19-elegir-inicio
back; back
go ajustes:7;      shot 20-ajustes-sistema
go ajustes:5;      shot 21-ajustes-mapa-rendimiento
# diagnóstico del mapa con la causa del GPS
go diag-mapa;      shot 22-diag-mapa
back
# simulación de pantalla ancha (versión de prueba) y pantalla real ancha 1920x720
go sim:3 6;        shot 23-sim-1920x720
go sim:4 6;        shot 24-sim-2000x1200
# diseño del inicio (Bloque 2)
go sim:0 4
go diseno:izq 6;      shot 43-diseno-reproductor-izquierda
go diseno:sinauto 6;  shot 44-diseno-sin-vehiculo
go diseno:compacta 6; shot 45-diseno-compacta
go diseno:plegar 6;   shot 46-diseno-aa-plegado
go diseno:lateral 6;  shot 47-diseno-lateral
go pista-demo 3;     shot 53-lateral-pista
go ajustes:2 5;      shot 51-lateral-ajustes
back
go cajon 6;          shot 52-lateral-cajon
back
go sim:5 6;           shot 48-sim-1280x480-lateral
go diseno:base 6;     shot 49-sim-1280x480-normal
go diseno:lateral 6;  shot 50-sim-1280x480-lateral-completa
go diseno:base 4
go sim:0 4
if [ "$API" -ge 18 ]; then adb shell wm size 1920x720; adb shell wm density 240; else adb shell am display-size 1920x720; adb shell am display-density 240; fi
sleep 5
go inicio 8;       shot 25-ancha-1920x720-inicio
go ajustes:2;      shot 26-ancha-1920x720-ajustes
go inicio 3
if [ "$API" -ge 18 ]; then adb shell wm size 1024x600; adb shell wm density 160; else adb shell am display-size 1024x600; adb shell am display-density 160; fi
sleep 5
go verificar-apk 4
# idiomas: inicio y ajustes en inglés y portugués
go idioma:en 6;    shot 12-en-inicio
go ajustes:4;      shot 13-en-ajustes-accesos
go elegir:3;       shot 14-en-elegir-app
back
go idioma:pt 6;    shot 15-pt-inicio
go ajustes:2;      shot 16-pt-ajustes-apariencia
go ajustes:8;      shot 17-pt-acerca
go idioma:es 6
# italiano y alemán
go idioma:it 6;    shot 27-it-inicio
go ajustes:2;      shot 28-it-ajustes-apariencia
go idioma:de 6;    shot 29-de-inicio
go ajustes:5;      shot 30-de-ajustes-mapa
go idioma:es 6
# recordatorio de cambio de aceite (odómetro estimado con el GPS)
go demo-aceite 8;  shot 31-aceite-inicio
go odo-sim 4
go menu-aceite 4;  shot 32-aceite-menu
back
go ajustes:1;      shot 33-aceite-ajustes
go aviso-aceite 8; shot 34-aceite-aviso
back
go unidad:mph 8;   shot 35-aceite-mph
go unidad:auto 6
go ultimo-aceite 4; shot 38-aceite-ultimo
back
go ultimo-alto 4;  shot 39-aceite-revisar
back
go unidad-aceite 4; shot 40-aceite-unidad
back
go unidad:auto 4
# apps ocultas del cajón
go ocultar-demo 8; shot 41-cajon-con-ocultas
go ocultas 5;      shot 42-apps-ocultas
back
go ocultar-limpiar 3
# mapa según la dirección
go rumbo:1 10;     shot 36-mapa-rumbo
go ajustes:5;      shot 37-ajustes-orientacion
go rumbo:0 6
# botón 3 cambiado a «Música» y botón 4 a una app con nombre propio
go demo-barra 10;  shot 11-inicio-barra-cambiada
go inicio 3

# textos mostrados sin traducción (versión de prueba): todas las categorías de Ajustes y el panel del vehículo
for c in 1 2 3 4 5 6 7 8 9 10 11; do go ajustes:$c 4; done
go obd-panel 5
go i18n-faltan 4
adb shell "run-as $PKG cat files/i18n-faltan.txt" > "$OUT/i18n-faltan.txt" 2>/dev/null || true

adb logcat -d > "$OUT/logcat.txt"
adb shell "run-as $PKG cat files/fallos.txt" > "$OUT/fallos.txt" 2>/dev/null || true
grep -E "FATAL EXCEPTION|ANR in com.stradaos" -A 30 "$OUT/logcat.txt" > "$OUT/CIERRES.txt"
if grep -q "com.stradaos" "$OUT/CIERRES.txt"; then
  echo "::error::API $API: StradaOS se cerró o se trabó (ver CIERRES.txt)"
  exit 1
fi
if ! grep -q "PRUEBA verificar-apk: propio=true otro=false" "$OUT/fallos.txt"; then
  echo "::error::API $API: la verificación del APK no dio el resultado esperado"; grep "verificar-apk" "$OUT/fallos.txt"; exit 1
fi
if ! grep -q "PRUEBA odo-sim: ok=true" "$OUT/fallos.txt"; then
  echo "::error::API $API: el odómetro estimado no sumó bien la distancia"; grep "odo-sim" "$OUT/fallos.txt"; exit 1
fi
rm -f "$OUT/CIERRES.txt"
echo "API $API: sin cierres"
