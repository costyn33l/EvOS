# Privacidad

StradaOS **no tiene cuentas, anuncios, rastreadores ni analíticas**, y no envía tus datos a servidores propios.

## Qué usa y para qué

| Dato | Para qué | ¿Sale del equipo? |
|---|---|---|
| Ubicación (GPS) | Velocímetro, mapa, clima y, si lo activas, el recordatorio de cambio de aceite (solo se guarda en el equipo la distancia recorrida, no los lugares) | Para el mapa, los servidores de mapas reciben qué zona descargar, como en cualquier mapa web. Para el clima, la latitud y longitud aproximadas se envían a [Open-Meteo](https://open-meteo.com) y, si el equipo no sabe el nombre de la ciudad, a [BigDataCloud](https://www.bigdatacloud.com). |
| Pista en reproducción | Mostrar la canción en el reproductor | No, salvo que actives *Ajustes → Música → Buscar carátulas en internet*: entonces, si una canción llega sin carátula, su título y artista se envían a [MusicBrainz](https://musicbrainz.org) y, si ahí no está, a iTunes (Apple) para encontrar la portada. |
| Foto del vehículo | Mostrarla en la pantalla de inicio | No. Se procesa y se guarda en el equipo. |
| Lista de apps instaladas | Accesos directos y cajón de apps | No. |
| Datos del motor (OBD-II), VIN y códigos de falla | Pantalla «Vehículo» y alertas por voz, si conectas un escáner | No, salvo que configures una IA: entonces, al explicar un código, se envían a tu proveedor (Anthropic u otro compatible con OpenAI, con tu propia API key) el código, el vehículo que escribiste y los datos del motor de ese momento. El VIN solo se envía si lo activas en *⚙ → Inteligencia artificial*. Nunca la ubicación. Las respuestas se guardan en el equipo. |

## Conexiones de red

- **Mapas:** OpenStreetMap (servidores comunitarios), OpenTopoMap y Esri.
- **Clima:** Open-Meteo (y BigDataCloud para el nombre de la ciudad).
- **Mapa sin conexión:** solo si eliges *Descargar un mapa*, el archivo del país se baja de `download.mapsforge.org`. Después el mapa se dibuja en el equipo, sin conexión.
- **IA para códigos de falla (opcional):** solo si pones tu API key en *Vehículo → ⚙ → Inteligencia artificial*: `api.anthropic.com` o la dirección que escribas.
- **Carátulas (opcional):** solo si activas *Buscar carátulas en internet*: `musicbrainz.org` (título y artista) y `coverartarchive.org` / `archive.org` (la imagen); si ahí no está, `itunes.apple.com` (título y artista) y `mzstatic.com` (la imagen), y si tampoco, `api.deezer.com` (título y artista) y `dzcdn.net` (la imagen). Las carátulas se guardan en el equipo. En Android 4.x, si la pantalla no logra la conexión segura con esos servidores, esa consulta se repite sin cifrar (http): viajan los mismos datos, el título y el artista o la imagen.
- **Tráfico en Alemania (opcional, *Ajustes → Mapa → Tráfico en Alemania*, solo estando en Alemania):** `verkehr.autobahn.de` recibe solo el nombre de las autopistas que se consultan (por ejemplo «A3»), no tu posición; `infranode.dev` recibe solo el nombre de la ciudad cercana. **Precios de combustible:** solo si escribes tu propia clave de Tankerkönig y tocas el botón del surtidor en el mapa, `creativecommons.tankerkoenig.de` recibe tu clave y la posición del auto (redondeada a ~100 m) con el radio de búsqueda. Nada de esto se guarda ni se reenvía.
- **Datos propios del modelo (opcional):** solo si eliges tu modelo en *Vehículo → ⚙ → Modelo para los datos propios (OBDb)*, el archivo de ese modelo se baja de GitHub (`api.github.com` y `raw.githubusercontent.com`, que reciben el nombre del modelo). Después se usa desde el equipo.
- **Actualizaciones:** GitHub (`api.github.com`, `raw.githubusercontent.com`) y jsDelivr, para saber si hay una versión nueva. Se puede desactivar en *Ajustes → Acerca de*.

## Permisos

- **Accesibilidad** («Carátula de Android Auto»): solo escucha las notificaciones de Open Headunit para leer la carátula. Es opcional.
- **Acceso a notificaciones** (Android 5+): solo para leer la canción que suena. Es opcional.
- **Almacenamiento:** solo para buscar o guardar el archivo del mapa sin conexión (`Download/StradaOS`). Es opcional.

El diagnóstico de *Ajustes → Sistema → Exportar diagnóstico* se guarda en la memoria del equipo y solo lo compartes tú, si quieres.
