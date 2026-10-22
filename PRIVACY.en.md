# Privacy Policy — MeteoCompare

*Last updated: October 22, 2026*

**Langues / Languages / Sprachen / Idiomas / Lingue:** [Français](PRIVACY.md) · [English](PRIVACY.en.md) · [Deutsch](PRIVACY.de.md) · [Español](PRIVACY.es.md) · [Italiano](PRIVACY.it.md)

## TL;DR

**MeteoCompare does not use your data for profiling, advertising, or analytics.** The app contains no advertising SDK, analytics, crash reporting, or account system.

Favorites, preferences, and app caches are stored locally. However, some information needed for weather features leaves the device: city searches are sent to the Open-Meteo geocoding service, and coordinates of viewed places are sent to Open-Meteo weather APIs. For French weather alerts, a department code may be sent to the public MeteoCompare Worker. When you explicitly open the rain radar, the location's coordinates are also used to load RainViewer imagery and OpenStreetMap map tiles.

---

## 1. Data processed by MeteoCompare

MeteoCompare does not build a user database and does not use any data for profiling, audience measurement, advertising, or cross-app tracking.

Specifically:

- ❌ No Google Analytics, Firebase Analytics, or equivalent SDK
- ❌ No Crashlytics or other remote crash reporting
- ❌ No ads or advertising network
- ❌ No advertising ID (`AD_ID`)
- ❌ No MeteoCompare account
- ❌ No tracking cookies or tracking WebView
- ❌ No deliberate device fingerprinting
- ❌ No cross-app tracking

The app's only network transmissions are those needed for the features described in sections 2, 3, and 4.

## 2. Open-Meteo

MeteoCompare uses the public [Open-Meteo](https://open-meteo.com/) APIs to search for places and retrieve weather forecasts, certain historical data, and marine data.

### Information sent to Open-Meteo

Depending on what you do in the app:

- **Search for a city:** the search text you enter is sent to the Open-Meteo geocoding API.
- **Load or refresh a place:** the viewed place's coordinates (latitude and longitude), requested weather variables, selected models, and technical request parameters are sent to the Open-Meteo APIs.
- **Automatic loading or widgets:** the coordinates of a favorite city may be reused to update its weather data without requiring you to enter the city again.

As with any Internet connection, Open-Meteo also receives the source IP address and technical HTTP information needed to route the request.

MeteoCompare does not send Open-Meteo your Google account, advertising identifier, app-specific Android identifier, name, email address, or entire list of favorites.

### Retention by Open-Meteo

Open-Meteo's current policy states that its free API service may retain **server logs for 90 days** for diagnostics, maintenance, and abuse prevention. These logs may contain sensitive information included in requests, such as **geographic coordinates**. Open-Meteo states that the logs are deleted after 90 days and are not shared with third parties.

This retention is carried out by Open-Meteo under its own terms and privacy policy: [open-meteo.com/en/terms](https://open-meteo.com/en/terms).

Data obtained from Open-Meteo is provided under the **CC BY 4.0** license. MeteoCompare aggregates, compares, and transforms it before display. Attribution and license: [open-meteo.com/en/license](https://open-meteo.com/en/license).

## 3. Météo-France weather alerts via the MeteoCompare Worker

For French cities, the app may query the public Worker at `https://meteocompare.app/_mcx/vigilance` to display official Météo-France weather alerts (Vigilance).

The app sends only these functional parameters:

- the **department code** (for example, `91`);
- `coast=1` when the place is identified as coastal, to include coastal alerts.

The Worker handles Météo-France authentication and caching on the server side. No Météo-France identifier, password, or token is embedded in the app.

As with any HTTPS request, the network infrastructure hosting or protecting the Worker receives the source IP address and request parameters. MeteoCompare does not deliberately use this information to profile, track, or identify users.

Cities outside France do not trigger Vigilance calls. When you remove a city from favorites, its in-memory alert state is removed and the corresponding persistent cache is cleared as part of the app's normal operation.

## 4. Rain radar: RainViewer and OpenStreetMap

The radar view is optional. It contacts these services only when you explicitly open it from a city's detail page.

- **RainViewer** supplies radar observation metadata and images. The viewed place's coordinates (latitude and longitude) are included in the image request to center the radar map.
- **OpenStreetMap** supplies only the background map tiles required for the displayed area. Previously viewed tiles are kept in a local HTTP cache to avoid downloading them unnecessarily each time. This cache follows the server's HTTP caching directives and may be cleared by Android.

As with any HTTPS request, these services also receive the source IP address and the technical HTTP information needed for delivery. MeteoCompare does not send them any account, advertising ID, or list of favorites.

The +15, +30, +45, and +60 minute projections are calculated **locally on the device** from the latest RainViewer images: segmentation of rain areas, estimation of their movement, and extrapolation with increasing uncertainty. No additional future data is requested from RainViewer to generate this nowcast.

## 5. Local storage on the device

MeteoCompare stores the following information locally:

| Data | Local storage | Purpose | Network transmission |
|---|---|---|---|
| Favorite cities and their coordinates | DataStore Preferences | Restore the list of selected places | The place's coordinates are sent to Open-Meteo when a forecast is loaded |
| Enabled weather models | DataStore Preferences | Preserve the configuration | Requested models are part of the weather request parameters |
| Theme, refresh interval, detail display, and other preferences | DataStore Preferences | Customize the app | No, except when a preference directly changes weather request parameters |
| App language | Internal SharedPreferences | Select the interface and search language | The language code may be sent to geocoding to localize results |
| Forecast cache and calculation history | Room SQLite | Fast startup, offline mode, comparisons | Cache contents are not uploaded as such |
| Vigilance cache | DataStore Preferences | Reduce network calls and provide a short fallback | Not as a cache; fresh requests may be sent to the Worker |
| Radar/map HTTP cache | Android `cacheDir` (OkHttp, 64 MiB maximum) | Reuse HTTP responses and OSM tiles according to their cache headers | Not as a cache; only missing or expired requests are sent to RainViewer / OpenStreetMap |
| Widget preferences | Glance DataStore | Store widget city, colors, and options | The configured city may trigger the same weather requests as the app |

“Stored locally” means that MeteoCompare does not synchronize these databases or files to a MeteoCompare server. It does not mean that information they contain is never used in a network request: for example, a favorite place's coordinates must be sent to the weather service to retrieve its forecast.

### Android backup

Android automatic backup is enabled **selectively**. MeteoCompare's backup rules allow only:

- general app preferences;
- the list of favorite cities;
- the language preference.

Android may back up or transfer these items to your account or device depending on your system settings. MeteoCompare has no access to those backups.

Room weather-cache databases, the HTTP cache in `cacheDir`, and other files not explicitly included in the backup rules are not part of this selective app backup.

Uninstalling the app removes its local data from the device. An Android backup already created remains subject to Android and your account settings.

## 6. Android permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | HTTPS calls to Open-Meteo, the MeteoCompare Worker, RainViewer, and OpenStreetMap |
| `ACCESS_NETWORK_STATE` | Detect a missing network connection and adapt the interface / requests |
| `RECEIVE_BOOT_COMPLETED` | Properly reschedule periodic widget updates after a reboot or app update |
| `POST_NOTIFICATIONS` | Show local weather notifications (daily summary, model divergence, forecast changes) only when enabled in Settings. They are calculated on the device: no push service, identifier, or additional data is transmitted |
| `ACCESS_COARSE_LOCATION` | Optional “My location” feature: network/fused provider with explicit consent |
| `ACCESS_FINE_LOCATION` | Optional permission: if granted, a one-time GPS acquisition (notably for positions injected into an emulator). Never used in the background; precise data is not stored |

MeteoCompare **does not request**:

- background location (`ACCESS_BACKGROUND_LOCATION`); precise location (`ACCESS_FINE_LOCATION`) is optional and is accessed only after foreground consent;
- access to contacts, photos, or external files;
- access to phone calls or SMS;
- Bluetooth or NFC;
- the advertising identifier.

By default, **you choose the city**. The optional “My location” feature obtains an approximate position only after consent, while an activity is visible; see the dedicated section below.

## 7. Transmission security

The network services configured by MeteoCompare use HTTPS. Data leaving the device is therefore encrypted in transit between the app and contacted servers using Android's standard TLS mechanisms.

## 8. Intended audience

The app is not specifically intended for children under 13. It creates no account, advertising profile, or MeteoCompare server-side history associated with a user's identity.

## 9. Changes to this policy

If an update adds new data collection, an account system, cloud synchronization, analytics, advertising, or another significant data transfer, this policy will be updated before that version is released.

The commitment for the 1.x series remains no analytics, advertising, tracking, or profiling by MeteoCompare.

## 10. Contact

For questions about this policy: [github.com/Pat0chat/MeteoCompare/issues](https://github.com/Pat0chat/MeteoCompare/issues)

## 11. Approximate location (v1.19.0, optional feature)

“My location” is off by default. When you select it, MeteoCompare requests `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` together: on Android 12+, you can select “Precise” or “Approximate.” If “Approximate” is selected, MeteoCompare **never** queries `GPS_PROVIDER`. Choosing precise location allows a one-time GPS acquisition, which is especially useful on certain emulators. An offer to grant additional precision is not repeated after the first consent: only a local “offer shown” flag is remembered in preferences that are not backed up (no position is stored there). You can change your choice in Android settings. There is no `ACCESS_BACKGROUND_LOCATION` permission or tracking SDK. Acquisition occurs only while an activity is visible (activation, returning to the foreground according to the freshness policy, or explicit refresh).

The raw position stays only in memory during resolution, then is reduced locally to a 0.05-degree grid. Only the coordinates of that grid cell and the refresh timestamp are stored in a private, non-backed-up and non-transferred `current_location` DataStore. No position or route history is recorded. Forecast evolution histories are not recorded for this location, and the previous cache is cleared when the cell changes or the feature is disabled. Weather model requests send the approximate cell to Open-Meteo, which also sees your IP address. An approximate position is still location data.

Radar tiles you view may temporarily remain in the shared HTTP cache until evicted. They are neither backed up nor transferred, but remain technical geographic data on disk.

Widgets **never** trigger GPS / location acquisition when Android refreshes them in the background. They use the last saved cell, which may be outdated after you move. To update your position, open MeteoCompare or tap the ↻ action on a “My location” widget: this opens a visible activity that may request foreground location permissions and lets you choose precision, without background tracking. A successful acquisition is reused for 30 minutes when returning automatically to the foreground; excessively frequent attempts are rate-limited. Removing the “My location” card clears the local location preferences and associated cache.
