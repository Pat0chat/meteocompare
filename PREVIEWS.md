# Compose previews

All Android Studio Compose previews live under `app/src/debug/java`. Production
sources under `app/src/main/java` intentionally contain no `@Preview` annotation.

## Organisation

- `ui/preview/` — common light/dark preview annotations, app theme wrapper and deterministic fixtures.
- `ui/citylist/` — complete Home, empty/offline/error/loading states, add-city sheet, mini timeline and sun times.
- `ui/citydetail/` — detail screen and every major section/table/chart/sheet.
- `ui/citydetail/confidence/` — convergence explanation states.
- `ui/components/` — reusable visual components and weather icon gallery.
- `ui/enginecomparison/` — engine comparison loaded state.
- `ui/help/` — How it works screen.
- `ui/settings/` — settings content and donation dialog.
- `ui/theme/` — Material 3, convergence and weather-metric palettes.

Most previews use the custom `@MeteoScreenPreview` or `@MeteoComponentPreview`
annotations. Each function therefore appears in both light and dark mode.

## Home coverage

The Home preview set explicitly covers:

- a complete list with loaded inland and coastal locations;
- the modern 12-hour heat-strip timeline and weather icons;
- a loading card;
- an error card;
- offline mode;
- the empty state;
- the add-city sheet;
- standalone mini-timeline and sunrise/sunset components.

## Intentional exclusion

`ui/navigation/AppNavHost.kt` has no unique visual surface: it only wires
Hilt-backed destinations. Calling it from Android Studio preview would create
production ViewModels. Every destination rendered by the graph has its own
stateless debug preview instead.

Run the coverage check with:

```bash
python scripts/validate-compose-previews.py
```
