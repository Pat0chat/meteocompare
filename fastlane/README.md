# Fastlane metadata for F-Droid

F-Droid can read this tree directly from tagged application source releases.
Keep metadata that describes the app here rather than duplicating it in
`fdroiddata`. The localized `short_description.txt` and `full_description.txt`
are kept in sync with `play-store/descriptions/`.

Current locales:
- `en-US`
- `fr-FR`
- `es-ES`
- `de-DE`
- `it-IT`

For each Android release, add a changelog file whose filename is the numeric
`versionCode`, not the semantic `versionName`.

Run `./scripts/validate-fdroid-metadata.sh` before tagging a release.
