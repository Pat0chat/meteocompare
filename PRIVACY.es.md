# Política de privacidad — MeteoCompare

*Última actualización: 22 de octubre de 2026*

**Langues / Languages / Sprachen / Idiomas / Lingue:** [Français](PRIVACY.md) · [English](PRIVACY.en.md) · [Deutsch](PRIVACY.de.md) · [Español](PRIVACY.es.md) · [Italiano](PRIVACY.it.md)

## Resumen

**MeteoCompare no utiliza tus datos para elaborar perfiles, mostrar publicidad ni realizar análisis de uso.** La aplicación no incluye SDK publicitarios, herramientas de analítica, informes de fallos remotos ni sistemas de cuentas.

Los favoritos, las preferencias y las cachés de la aplicación se guardan localmente. Sin embargo, algunos datos necesarios para las funciones meteorológicas salen del dispositivo: las búsquedas de ciudades se envían al servicio de geocodificación de Open-Meteo y las coordenadas de los lugares consultados se envían a las API meteorológicas de Open-Meteo. Para las alertas oficiales francesas, se puede enviar un código de departamento al Worker público de MeteoCompare. Cuando abres expresamente el radar de lluvia, también se utilizan las coordenadas del lugar para cargar imágenes de RainViewer y teselas cartográficas de OpenStreetMap.

---

## 1. Datos tratados por MeteoCompare

MeteoCompare no crea una base de datos de usuarios ni utiliza datos para elaborar perfiles, medir audiencias, mostrar publicidad o rastrear la actividad entre aplicaciones.

En concreto:

- ❌ Sin Google Analytics, Firebase Analytics ni SDK equivalentes
- ❌ Sin Crashlytics ni otros informes remotos de fallos
- ❌ Sin anuncios ni redes publicitarias
- ❌ Sin identificador publicitario (`AD_ID`)
- ❌ Sin cuenta de MeteoCompare
- ❌ Sin cookies de seguimiento ni WebView para rastreo
- ❌ Sin identificación deliberada del dispositivo mediante huella digital
- ❌ Sin seguimiento entre aplicaciones

Las únicas transmisiones de red realizadas por la aplicación son las necesarias para las funciones descritas en las secciones 2, 3 y 4.

## 2. Open-Meteo

MeteoCompare utiliza las API públicas de [Open-Meteo](https://open-meteo.com/) para buscar lugares, consultar previsiones meteorológicas y obtener determinados datos históricos y marinos.

### Información enviada a Open-Meteo

Según la acción realizada en la aplicación:

- **Búsqueda de una ciudad:** el texto de búsqueda introducido se envía a la API de geocodificación de Open-Meteo.
- **Carga o actualización de un lugar:** las coordenadas del lugar consultado (latitud y longitud), las variables meteorológicas solicitadas, los modelos seleccionados y los parámetros técnicos necesarios se envían a las API de Open-Meteo.
- **Cargas automáticas o widgets:** pueden reutilizarse las coordenadas de una ciudad favorita para actualizar sus datos meteorológicos sin que tengas que introducirla de nuevo cada vez.

Como ocurre con cualquier conexión a Internet, Open-Meteo también recibe la dirección IP de origen y la información técnica HTTP necesaria para tramitar la solicitud.

MeteoCompare no envía a Open-Meteo tu cuenta de Google, identificador publicitario, identificador Android específico de la aplicación, nombre, dirección de correo electrónico ni lista completa de favoritos.

### Conservación de datos por Open-Meteo

La política actual de Open-Meteo indica que su servicio de API gratuito puede conservar **registros del servidor durante 90 días** con fines de diagnóstico, mantenimiento y prevención de abusos. Estos registros pueden contener información sensible incluida en las solicitudes, como **coordenadas geográficas**. Open-Meteo indica que elimina los registros pasados 90 días y que no los comparte con terceros.

Esta conservación la realiza Open-Meteo de acuerdo con sus propias condiciones y política de privacidad: [open-meteo.com/en/terms](https://open-meteo.com/en/terms).

Los datos obtenidos a través de Open-Meteo se proporcionan bajo la licencia **CC BY 4.0**. MeteoCompare los agrega, compara y transforma antes de mostrarlos. Atribución y licencia: [open-meteo.com/en/license](https://open-meteo.com/en/license).

## 3. Alertas de Météo-France mediante el Worker de MeteoCompare

Para las ciudades francesas, la aplicación puede consultar el Worker público `https://meteocompare.app/_mcx/vigilance` para mostrar la Vigilance oficial de Météo-France.

La aplicación envía únicamente estos parámetros funcionales:

- el **código de departamento** (por ejemplo, `91`);
- `coast=1` cuando la localidad se identifica como costera, para incluir las alertas del litoral.

El Worker gestiona la autenticación de Météo-France y la caché en el servidor. La aplicación no incluye ningún identificador, contraseña ni token de Météo-France.

Como ocurre con cualquier solicitud HTTPS, la infraestructura de red que aloja o protege el Worker recibe la dirección IP de origen y los parámetros de la solicitud. MeteoCompare no utiliza deliberadamente esta información para elaborar perfiles, rastrear ni identificar a los usuarios.

Las ciudades situadas fuera de Francia no generan llamadas a Vigilance. Cuando se elimina una ciudad de los favoritos, se borra de memoria su estado de alerta y se limpia la caché persistente correspondiente según el funcionamiento de la aplicación.

## 4. Radar de lluvia: RainViewer y OpenStreetMap

La vista de radar es opcional y solo contacta con estos servicios después de que la abras expresamente desde la ficha de una ciudad.

- **RainViewer** proporciona metadatos e imágenes de observaciones de radar. Las coordenadas (latitud y longitud) del lugar consultado se incluyen en la solicitud de imagen para centrar el mapa del radar.
- **OpenStreetMap** proporciona únicamente las teselas del mapa base necesarias para la zona mostrada. Las teselas ya consultadas se conservan en una caché HTTP local para evitar descargarlas innecesariamente cada vez. Esta caché respeta las directivas HTTP del servidor y Android puede borrarla.

Como ocurre con cualquier solicitud HTTPS, estos servicios también reciben la dirección IP de origen y la información técnica HTTP necesaria para tramitarla. MeteoCompare no les envía cuentas, identificadores publicitarios ni listas de favoritos.

Las proyecciones a +15, +30, +45 y +60 minutos se calculan **localmente en el dispositivo** a partir de las últimas imágenes de RainViewer: segmentación de las zonas de lluvia, estimación de su desplazamiento y extrapolación con incertidumbre creciente. No se solicitan a RainViewer datos futuros adicionales para producir esta predicción inmediata.

## 5. Almacenamiento local en el dispositivo

MeteoCompare conserva localmente los siguientes datos:

| Datos | Almacenamiento local | Finalidad | Transmisión por red |
|---|---|---|---|
| Ciudades favoritas y sus coordenadas | DataStore Preferences | Recuperar los lugares seleccionados | Las coordenadas del lugar se envían a Open-Meteo cuando se carga una previsión |
| Modelos meteorológicos activados | DataStore Preferences | Conservar la configuración | Los modelos solicitados forman parte de los parámetros de la petición meteorológica |
| Tema, frecuencia de actualización, visualización de detalles y otras preferencias | DataStore Preferences | Personalizar la aplicación | No, salvo cuando una preferencia modifica directamente los parámetros de una petición meteorológica |
| Idioma de la aplicación | SharedPreferences internas | Elegir el idioma de la interfaz y de las búsquedas | El código de idioma puede enviarse al servicio de geocodificación para localizar los resultados |
| Caché de previsiones e historial de cálculos | Room SQLite | Inicio rápido, uso sin conexión, comparaciones | El contenido de la caché no se sube como tal |
| Caché de Vigilance | DataStore Preferences | Reducir las peticiones de red y ofrecer una alternativa temporal | No como caché; pueden hacerse nuevas solicitudes al Worker |
| Caché HTTP de radar y mapas | `cacheDir` de Android (OkHttp, máximo 64 MiB) | Reutilizar respuestas HTTP y teselas OSM según sus cabeceras de caché | No como caché; solo se vuelven a solicitar a RainViewer / OpenStreetMap los recursos ausentes o caducados |
| Preferencias de los widgets | DataStore Glance | Guardar ciudad, colores y opciones del widget | La ciudad configurada puede generar las mismas solicitudes meteorológicas que dentro de la aplicación |

«Almacenado localmente» significa que MeteoCompare no sincroniza estas bases de datos o archivos con un servidor de MeteoCompare. No significa que la información que contienen nunca se utilice en una solicitud de red: por ejemplo, las coordenadas de un lugar favorito deben enviarse al servicio meteorológico para obtener su previsión.

### Copia de seguridad de Android

La copia de seguridad automática de Android está activada de forma **selectiva**. Las reglas de MeteoCompare permiten guardar únicamente:

- las preferencias generales de la aplicación;
- la lista de ciudades favoritas;
- la preferencia de idioma.

Android puede hacer una copia de estos elementos o transferirlos a tu cuenta o dispositivo según la configuración del sistema. MeteoCompare no tiene acceso a esas copias.

Las bases Room de caché meteorológica, la caché HTTP de `cacheDir` y los demás archivos no incluidos expresamente en las reglas de copia de seguridad no forman parte de esta copia selectiva.

Al desinstalar la aplicación se eliminan los datos locales presentes en el dispositivo. Las copias de seguridad de Android ya creadas siguen gestionadas por Android y por la configuración de tu cuenta.

## 6. Permisos de Android

| Permiso | Finalidad |
|---|---|
| `INTERNET` | Solicitudes HTTPS a Open-Meteo, el Worker de MeteoCompare, RainViewer y OpenStreetMap |
| `ACCESS_NETWORK_STATE` | Detectar la falta de conexión y adaptar la interfaz / las solicitudes |
| `RECEIVE_BOOT_COMPLETED` | Reprogramar correctamente las actualizaciones periódicas de los widgets tras reiniciar el dispositivo o actualizar la aplicación |
| `POST_NOTIFICATIONS` | Mostrar notificaciones meteorológicas locales (resumen diario, divergencia de modelos, cambios de previsión) solo si las activas en Ajustes. Se calculan en el dispositivo: no se transmite ningún identificador ni dato adicional y no se utiliza un servicio push |
| `ACCESS_COARSE_LOCATION` | Función opcional «Mi ubicación»: proveedor de red/fusionado con consentimiento explícito |
| `ACCESS_FINE_LOCATION` | Permiso opcional: si se concede, obtención puntual de la posición GPS (especialmente para ubicaciones inyectadas en un emulador). Nunca se utiliza en segundo plano y no se almacena la posición exacta |

MeteoCompare **no solicita**:

- ubicación en segundo plano (`ACCESS_BACKGROUND_LOCATION`); la ubicación precisa (`ACCESS_FINE_LOCATION`) es opcional y solo se consulta tras el consentimiento en primer plano;
- acceso a contactos, fotos o archivos externos;
- acceso al teléfono o a SMS;
- Bluetooth o NFC;
- el identificador publicitario.

De forma predeterminada, **tú eliges la ciudad**. La función opcional «Mi ubicación» obtiene una posición aproximada solo después del consentimiento, mientras hay una actividad visible; consulta el apartado específico más adelante.

## 7. Seguridad de las transmisiones

Los servicios de red configurados por MeteoCompare utilizan HTTPS. Por tanto, los datos que salen del dispositivo se cifran en tránsito entre la aplicación y los servidores consultados mediante los mecanismos TLS estándar de Android.

## 8. Público destinatario

La aplicación no está dirigida específicamente a menores de 13 años. No crea cuentas, perfiles publicitarios ni historiales en servidores de MeteoCompare asociados a la identidad de una persona.

## 9. Modificaciones de esta política

Si una actualización incorpora una nueva recopilación de datos, un sistema de cuentas, sincronización en la nube, analítica, publicidad u otra transmisión significativa, esta política se actualizará antes de publicar dicha versión.

El compromiso para la serie 1.x sigue siendo no utilizar analítica, publicidad, seguimiento ni elaboración de perfiles por parte de MeteoCompare.

## 10. Contacto

Si tienes preguntas sobre esta política: [github.com/Pat0chat/MeteoCompare/issues](https://github.com/Pat0chat/MeteoCompare/issues)

## 11. Ubicación aproximada (v1.19.0, función opcional)

«Mi ubicación» está desactivada de forma predeterminada. Cuando eliges esta opción, MeteoCompare solicita conjuntamente `ACCESS_COARSE_LOCATION` y `ACCESS_FINE_LOCATION`: en Android 12+ puedes seleccionar «Precisa» o «Aproximada». Si eliges «Aproximada», MeteoCompare **nunca** consulta `GPS_PROVIDER`. Elegir la ubicación precisa permite una adquisición puntual por GPS, especialmente útil en algunos emuladores. La oferta de conceder precisión adicional no se repite después del primer consentimiento: en preferencias que no se incluyen en la copia de seguridad solo se conserva una marca local «oferta mostrada» (sin datos de posición). Puedes cambiar la elección en los ajustes de Android. No existe el permiso `ACCESS_BACKGROUND_LOCATION` ni un SDK de rastreo. La adquisición solo ocurre mientras una actividad está visible (activación, regreso al primer plano según la política de vigencia o actualización explícita).

La ubicación sin procesar permanece únicamente en memoria durante la resolución y después se reduce localmente a una cuadrícula de 0,05 grados. Solo las coordenadas de esa celda y la fecha/hora de actualización se guardan en un DataStore privado `current_location`, sin copias de seguridad ni transferencias. No se registra ningún historial de posiciones ni trayectorias. Para esta ubicación no se guardan historiales de evolución de las previsiones; la caché anterior se limpia cuando cambia la celda o se desactiva la función. Las solicitudes de modelos meteorológicos envían la celda aproximada a Open-Meteo, que también ve tu dirección IP. Una ubicación aproximada sigue siendo un dato de localización.

Las teselas de radar consultadas pueden permanecer temporalmente en la caché HTTP compartida hasta su expulsión. No se incluyen en copias de seguridad ni se transfieren, pero siguen siendo datos geográficos técnicos en el almacenamiento del dispositivo.

Los widgets **nunca** activan el GPS ni la obtención de ubicación cuando Android los actualiza en segundo plano. Utilizan la última celda guardada, que puede haber quedado desactualizada después de un desplazamiento. Para actualizar tu ubicación, abre MeteoCompare o toca la acción ↻ de un widget «Mi ubicación»: se abrirá una actividad visible que puede solicitar permisos de ubicación en primer plano y permitirte elegir la precisión, sin seguimiento en segundo plano. Una adquisición correcta se reutiliza durante 30 minutos en los regresos automáticos al primer plano; se limitan los intentos demasiado frecuentes. Al eliminar la tarjeta «Mi ubicación» se borran las preferencias locales de ubicación y la caché asociada.
