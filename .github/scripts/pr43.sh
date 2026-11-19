#!/bin/bash
# Revisión del PR #43 (pantallas FYT): que nada cambie sin FYT y que lo nuevo funcione.
# Uso: pr43.sh API APK CARPETA
API=$1; APK=$2; OUT=$3; mkdir -p $OUT
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
size() {  # tamaño de la pantalla, densidad 160
  if [ "$API" -ge 18 ]; then adb shell wm size $1; adb shell wm density 160
  else adb shell am display-size $1; adb shell am display-density 160; fi
  sleep 6
}
size 1024x600
# El emulador anuncia IPv6 pero la máquina de GitHub no tiene salida IPv6: sin esto no responden los servidores de rutas
if [ "$API" -ge 21 ]; then
  adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
  adb shell sysctl -w net.ipv6.conf.all.disable_ipv6=1 >/dev/null 2>&1
  adb shell sysctl -w net.ipv6.conf.default.disable_ipv6=1 >/dev/null 2>&1
  sleep 3
fi
if [ "$API" -ge 23 ]; then adb install -r -g "$APK" | tail -1; else adb install -r "$APK" | tail -1; fi
shot() {
  rm -f /tmp/shot.png; rm -rf /tmp/emushot; mkdir -p /tmp/emushot
  adb shell screencap -p /data/local/tmp/s.png >/dev/null 2>&1 && adb pull /data/local/tmp/s.png /tmp/shot.png >/dev/null 2>&1
  if [ ! -s /tmp/shot.png ] || [ "$(stat -c %s /tmp/shot.png)" -lt 15000 ]; then
    adb emu screenrecord screenshot /tmp/emushot >/dev/null 2>&1; sleep 2
    f=$(ls -t /tmp/emushot/*.png 2>/dev/null | head -1)
    [ -n "$f" ] && cp "$f" /tmp/shot.png
  fi
  [ -s /tmp/shot.png ] && cp /tmp/shot.png "$OUT/$1.png" && echo "captura $1"
}
go() { adb shell am start -n $ACT --es prueba "$1" >/dev/null; sleep "${2:-5}"; }
sc() { adb shell input swipe 600 520 600 120 400; sleep 2; }
( while true; do adb emu geo fix -75.5680 6.2442 >/dev/null 2>&1; adb emu geo fix -75.5680 6.2442 1495 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
demo() { go inicio 3; go pista-demo:Night_Drive 3; }
sleep 15
# 1) Sin FYT (lo de siempre): inicio y ajustes no cambian salvo la sección Equipo
demo; shot a01-generica-inicio
go ajustes:1 5; sc; sc; shot a02-generica-tu-vehiculo
go pref:theme=system 5; go ajustes:5 5; shot a03-generica-tema-sistema
go pref:theme_delay_on=true 5; go ajustes:5 5; shot a04-espera-activada
go ajustes:3 5; sc; sc; shot a05-reproductor-izq-der
go ajustes:9 5; sc; sc; sc; sc; shot a06-compatibilidad
go pref:theme_delay_on=false 4; go tema:oscuro 5
# 2) FYT elegida a mano (el emulador no tiene com.syu.ms): datos de prueba en la barra
go pref:hw=fyt 5; go pref:fyt_data=true 5; go pref:fyt_volt=true 4; go pref:fyt_temp=true 4
go fyt-demo:139,1170 3; demo; shot b01-fyt-inicio-datos
go ajustes:1 5; sc; sc; shot b02-fyt-tu-vehiculo
go tema-premium:aurora 6; go fyt-demo:139,1170 3; demo; shot b03-fyt-aurora
go tema-premium:cobalto 6; go fyt-demo:139,1170 3; demo; shot b04-fyt-cobalto
go tema-premium:faro 8; go fyt-demo:139,1170 3; demo; shot b05-fyt-faro
go pref:temp_unit=F 4; go fyt-demo:139,1170 3; demo; shot b06-fyt-faro-fahrenheit
go pref:temp_unit=C 4
# 3) «Sistema» sigue las luces (car_mode) con espera de 5 s para volver a claro
if [ "$API" -ge 17 ]; then
  go tema-gratis:1 6
  adb shell settings put system car_mode 0; sleep 1
  go pref:theme=system 5; go pref:theme_delay_on=true 3; go pref:theme_delay_dark=0 3; go pref:theme_delay_light=5 4
  go inicio 4; shot c01-luces-apagadas-claro
  adb shell settings put system car_mode 1; sleep 3; shot c02-luces-encendidas-oscuro
  adb shell settings put system car_mode 0; sleep 2; shot c03-luces-apagadas-2s-sigue-oscuro
  sleep 6; shot c04-luces-apagadas-8s-claro
  go ajustes:5 5; shot c05-tema-fyt
  # sin FYT, car_mode no influye
  go pref:hw=generic 5; adb shell settings put system car_mode 1; sleep 3; go inicio 4; shot c06-generica-ignora-luces
  adb shell settings put system car_mode 0
fi
go pref:hw=generic 4; go pref:theme=dark 4; go inicio 5
kill $GEO 2>/dev/null
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
adb logcat -d | grep -iE "FYT|syu" | tail -40 > $OUT/logcat-fyt.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
