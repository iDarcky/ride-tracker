# Ridely – Play Console forms (draft answers)

Checked against the app as of 0.20.0. The only data that leaves the phone without the driver choosing it is ML Kit's
technical logs (see <https://developers.google.com/ml-kit/android-data-disclosure>), which also bring in the INTERNET
permission. Ridely itself has no server, account, analytics or ads.

## Data safety
- **Does your app collect or share any of the required user data types?** Yes (because of ML Kit's logs).
- **Is all of the user data collected by your app encrypted in transit?** Yes (ML Kit uses HTTPS).
- **Do you provide a way for users to request that their data is deleted?** The app's own data: yes, *Erase all
  data* and uninstalling. (ML Kit's logs aren't tied to the user.)
- **Data types collected:**
  - *App info and performance → Crash logs* and *Diagnostics*: collected, not shared, processed ephemerally: no,
    required (can't be turned off), purpose **Analytics** (ML Kit's usage and diagnostics).
  - *Device or other IDs*: collected, not shared, required, purpose **Analytics** (ML Kit's per-installation id).
- **Not collected:** location, personal info, financial info, messages, photos (screenshots are read on the phone and
  not sent), contacts, app activity, web history.
- Backups the driver saves to Drive are the driver's own action to a place they choose; Google's guidance treats
  user-initiated transfers like that as not collected by the app.

## App content
- **Privacy policy:** https://idarcky.github.io/ride-tracker/privacy/
- **Ads:** No ads.
- **App access:** All functionality is available without special access (no login).
- **Content rating questionnaire:** Category *Utility, productivity, communication or other*; answer No to violence,
  sexual content, language, drugs, gambling, user interaction/sharing (no chat; sharing an image goes through
  Android's share sheet), location sharing, digital purchases. Expected rating: Everyone / PEGI 3.
- **Target audience:** 18 and over only.
- **News app:** No. **COVID-19 app:** No. **Government app:** No.
- **Financial features:** none of the regulated ones (no loans, payments, banking, crypto, insurance, trading).
  It's a personal tracker: choose the "My app doesn't provide any financial features" option, or the closest
  "budgeting/personal finance tracking" option if the form offers one.
- **Health apps:** No.

## Permissions
- `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE` (WorkManager): no declaration.
- `SCHEDULE_EXACT_ALARM` (the Raportul Z and evening import reminders at the driver's chosen time): on Android 14+
  the driver allows it themselves in *Alarms & reminders*, and the app works without it (Android may then show the
  reminder a little late). Play restricts `USE_EXACT_ALARM`, which Ridely doesn't use. If the Console still asks
  about exact alarms, the answer is: user-set reminders at a chosen time, the core of the Raportul Z feature.
- `INTERNET`, `ACCESS_NETWORK_STATE`: from ML Kit.
