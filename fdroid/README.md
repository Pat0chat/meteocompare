# MeteoCompare → F-Droid submission

This directory contains the `fdroiddata` recipe for `com.meteocompare.app` and
the transition plan from the already-tagged `v1.7.0` release to upstream
Fastlane metadata.

## What goes where

- **Application repository**: `fastlane/metadata/android/**`
  - title, short description, full description
  - localized changelog named with the Android `versionCode`
  - icon, feature graphic and optional screenshots
- **F-Droid `fdroiddata` repository**: `com.meteocompare.app.yml`
  - source repository and license
  - build instructions
  - update checking / automatic update policy
  - anti-feature declaration

## Important v1.7.0 detail

The `v1.7.0` tag already exists and therefore cannot contain Fastlane files
added afterwards without rewriting the tag (do not rewrite a published tag).
For the initial 1.7.0 submission, `fdroid/com.meteocompare.app.yml` includes
one-time English Name/Summary/Description fallbacks.

The cleanest first F-Droid listing is to commit the Fastlane tree, bump the app
to a new version (for example `1.7.1`, with a new `versionCode`), add that new
versionCode's changelog files, tag it, and submit using the upstream metadata.
If 1.7.0 must be submitted immediately, use the included initial YAML as-is.

## Before every release

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
2. Add both changelogs:
   - `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   - `fastlane/metadata/android/fr-FR/changelogs/<versionCode>.txt`
3. Run:

   ```bash
   ./scripts/validate-fdroid-metadata.sh
   ./gradlew clean :app:assembleRelease
   ```

4. Commit, then create and push an immutable `v<versionName>` tag.

## Optional screenshots

If the repository contains the existing Play Store screenshot directory, run:

```bash
./scripts/import-play-store-screenshots-to-fastlane.sh
```

This creates F-Droid-compatible `phoneScreenshots/1.png`, `2.png`, etc. for
`en-US` and `fr-FR`.

## Initial F-Droid submission

In a checkout/fork of `fdroid/fdroiddata`:

```bash
cp /path/to/MeteoCompare/fdroid/com.meteocompare.app.yml metadata/
fdroid lint com.meteocompare.app
fdroid rewritemeta com.meteocompare.app
fdroid checkupdates com.meteocompare.app
fdroid build -v -l com.meteocompare.app
```

Inspect the rewrite before committing it. Then push the branch to your fork and
open a merge request against the official `fdroiddata` repository.

## After Fastlane is present in a tagged release

Remove `Name`, `Summary` and `Description` from the official fdroiddata metadata
(or use `fdroid/com.meteocompare.app.after-fastlane.yml` as the reference
shape). This allows F-Droid to consume the localized upstream metadata instead
of a non-localized YAML override.

`AutoUpdateMode: Version` + the version-tag `UpdateCheckMode` lets F-Droid's
update tooling detect later `vX.Y.Z` tags and generate new build entries.

## Network anti-feature choice

`NonFreeNet` is intentionally not used: Open-Meteo's server is open source and
self-hostable. `TetheredNet` is declared because MeteoCompare currently points
to hard-coded public Open-Meteo endpoints and has no user setting for changing
the backend. A reviewer can change/remove this classification during review.
