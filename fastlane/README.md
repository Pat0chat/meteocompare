# Fastlane metadata for F-Droid

F-Droid can read this tree directly from tagged application source releases.
Keep metadata that describes the app here rather than duplicating it in
`fdroiddata`.

Current locales:
- `en-US`
- `fr-FR`

For each Android release, add a changelog file whose filename is the numeric
`versionCode`, not the semantic `versionName`.

Run `./scripts/validate-fdroid-metadata.sh` before tagging a release.
