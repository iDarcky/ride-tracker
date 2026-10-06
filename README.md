# Ride Tracker

Track your ride-sharing income across apps (Uber, Bolt, or any other) and see totals by day, week, month or a custom range.

Product direction and scope: [docs/product-brief.md](docs/product-brief.md).

- **Android**: Jetpack Compose + Material 3 Expressive (`androidApp/`)
- **iOS**: SwiftUI on iOS 27, coming later (`iosApp/`)
- **Shared** Kotlin Multiplatform module with the database (Room), settings (DataStore) and logic (`shared/`)

## Build

Requires JDK 21 and the Android SDK (Android Studio ships both).

```bash
./gradlew :shared:testAndroidHostTest      # unit tests
./gradlew :androidApp:assembleDebug        # debug APK
./gradlew :androidApp:assembleRelease      # signed release APK
```

## Versioning and upgrades

Every new version installs over the previous one and keeps all data. To make that work:

1. **Version**: bump `VERSION_NAME` in `version.properties` (SemVer `MAJOR.MINOR.PATCH`).
   `versionCode` is derived from it automatically and always increases.
2. **Signing**: every build is signed with the same key, `~/.ridetracker/release.jks`.
   Its passwords live in `~/.gradle/gradle.properties` (`ridetracker.*`). **Back up both.**
   With a different key, Android refuses to update the app.
3. **Database**: never use destructive migrations. When you change an entity, bump the
   `@Database` version in `AppDatabase.kt` and add an `AutoMigration` or `Migration`.
   Schema JSON files in `shared/schemas/` are committed and show what each version looked like.
4. **applicationId** `app.ridetracker` must never change.

## Translations

All user-facing text lives in Android string resources:
`androidApp/src/main/res/values/strings.xml` (English, default) and `values-ro/strings.xml` (Romanian).
To add a language, copy `values-ro` to `values-<code>` and translate it; the in-app language list and
Android 13+ per-app language settings pick it up (also add the code to `languageOptions` in `MoreScreen.kt`).
Never hard-code text in Kotlin. Dates, numbers and money are formatted with the current locale.

## Design

UI follows the "Rideshare cockpit" design system (Material 3, seed `#0A6C8B`, light and dark).
Theme tokens are in `androidApp/.../ui/theme/Theme.kt`.

## Releases

Push a tag such as `v0.0.1` to build a signed APK in GitHub Actions and attach it to a GitHub Release.
This needs four repository secrets: `RIDETRACKER_KEYSTORE_BASE64` (`base64 -i ~/.ridetracker/release.jks`),
`RIDETRACKER_STOREPASSWORD`, `RIDETRACKER_KEYALIAS` and `RIDETRACKER_KEYPASSWORD`.
