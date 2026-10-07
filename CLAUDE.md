# Ride Tracker – working notes for Claude

Ride-sharing income and expenses tracker for drivers (Romania first). Product scope and decisions:
[docs/product-brief.md](docs/product-brief.md). History: [CHANGELOG.md](CHANGELOG.md). App name is not final.

## Working with the owner
- Ask before building; propose, get approval, then implement. The owner tests on the emulator too.
- Not an accounting app: no tax filing; Romanian tax features only for PFA drivers in Romania.
- Design: Material 3 **Expressive** components only (no hand-built components), "Rideshare cockpit"
  colours (seed #0A6C8B), Google Health style settings (each setting its own page), floating nav bar.

## Stack and layout
- Kotlin Multiplatform. `shared/` = Room KMP database, DataStore settings, money/period logic, backup.
  `androidApp/` = Jetpack Compose UI (material3 1.5.0-alpha, pinned in `gradle/libs.versions.toml`).
  `iosApp/` = SwiftUI later (iOS 27).
- Package / applicationId `app.ridetracker` – **never change the applicationId**.
- Manual DI in `RideTrackerApplication.kt` (`AppContainer`); one ViewModel per screen.

## Commands (JDK 21 in ~/.jdks, SDK in ~/Library/Android/sdk; set JAVA_HOME first)
```bash
export JAVA_HOME=$(echo ~/.jdks/jdk-21*/Contents/Home)
./gradlew :shared:testAndroidHostTest          # unit tests
./gradlew :androidApp:assembleRelease          # signed APK (key in ~/.ridetracker, passwords in ~/.gradle/gradle.properties)
./gradlew :androidApp:lintRelease              # must stay clean except ObsoleteSdkInt
~/Library/Android/sdk/emulator/emulator -avd RideTracker_API37   # emulator (run in background)
~/Library/Android/sdk/platform-tools/adb install -r androidApp/build/outputs/apk/release/androidApp-release.apk
```
CI (GitHub Actions) runs tests + build on every push to `main`. Repo is **public**: github.com/iDarcky/ride-tracker.

## Rules that protect user data
- Every release bumps `VERSION_NAME` in `version.properties` (SemVer); versionCode is derived.
- Schema change ⇒ bump `@Database(version)` and add an `AutoMigration`/`Migration`; never destructive.
  Commit the new `shared/schemas/*.json`. Test by installing the previous version with data, then upgrading.
- Enum ids stored in DB/DataStore/backups (`Country`, `DrivingType`, `ThemeMode`, `ExpenseCategory`) are
  permanent: add new values, never rename ids.
- Backup format (`BackupService.kt`): additive changes keep reading old files; bump `FORMAT_VERSION`
  when older apps would lose data, and keep a format test in `BackupFormatTest`.
- Money is `Long` minor units; dates are epoch days in DB and ISO strings in backups.

## Translations
All UI text in `androidApp/src/main/res/values/strings.xml` (English) and `values-ro/strings.xml` (Romanian).
No hard-coded text in Kotlin. Use plurals for counts (Romanian has one/few/other). Format money, dates and
percentages with the current locale (`ui/common/Formatting.kt`).

## Privacy
- Never commit real driver data, names, emails or earnings. The owner's real Uber/Bolt exports and the
  "Ride Earnings Dashboard" prototype contain personal data: use made-up fixtures in tests.
- Git author is the GitHub noreply address; keep it that way.
- The app stores no rider personal data (relevant for future importers).
