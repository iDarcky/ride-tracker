# Changelog

## 0.0.4
- Floating navigation bar (Google Photos style) with Home, Trips, Money, Vehicle and Settings.
- Money tab: Income (full history, swipe to delete with undo) and Expenses (coming soon).
- Settings redesigned in the Google Health style: large title and sections (App settings, Your data, About).
- Backup and restore: save everything (income, apps, settings) to a JSON file and restore it in any later version.
- Erase all data, with confirmation; the app starts over at the welcome screen.
- Onboarding: language picker on the welcome screen; drivers in Romania choose PFA or fleet partner.

## 0.0.3
- New permanent app ID `app.ridetracker` and a new signing key. Installs as a new app, so data from 0.0.1–0.0.2 test builds does not carry over.
- Theme setting: system, light or dark.
- Platform badges are rounded squares.
- The overview no longer flashes a placeholder total while loading.

## 0.0.2
- Welcome screen on first launch: choose Romania (RON) or another country and its currency.
- Country replaces the currency setting; it lives in the new More tab.
- English and Romanian, with an in-app language picker.
- "Rideshare cockpit" design system: teal colour scheme and a 5-tab layout (Overview, Trips, Vehicle, Expenses, More).
  Trips, Vehicle and Expenses are placeholders for now.
- Outline on platform badges that blend into the background (e.g. Uber in dark mode).

## 0.0.1
- First MVP: add income by hand per ride-sharing app (Uber and Bolt preloaded, add any other app).
- Totals by day, week, month or custom range, with a per-app breakdown.
- Edit, delete (swipe, with undo), archive apps, choose currency and first day of the week.
- Material 3 Expressive UI, plain blue theme, light/dark mode.
