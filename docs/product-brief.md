# Ride Tracker – product brief v0.2

Working name: Ride Tracker (final name not chosen). Last updated 8 October 2026 (app at 0.5.3).

This is the public, trimmed version of the brief: no personal earnings data and no business plan.

## What it is

A simple money app for ride-sharing drivers (Uber, Bolt and others) that answers one question:
**how much did I actually keep?** It records income and expenses and shows what is left for a day,
week, month or custom period.

It is **not** an accounting service, a tax filing service, a GPS or kilometre tracker, or a fleet tool.
Services like Pick, SOLO or PFA Ride sell accounting; this app helps the driver see their own numbers.

Terminology: we say **ride-sharing** (what drivers and Romanian sites use), even though Uber and Bolt are
technically "ride-hailing". Inside the app the word is rarely needed: "apps", "Uber", "Bolt".

## Principles

1. **Simple and fast.** Adding an entry takes under 10 seconds.
2. **One question first.** Home answers "how much did I keep?"
3. **Manual first.** Imports and APIs later fill the same records.
4. **Honest numbers.** Every figure is Known, Estimated or Missing; partial metrics say so ("Uber only").
5. **Local-first and private.** Data stays on the device, no account, rider personal data never stored,
   backup and export from the start.
6. **Not tax advice.** Estimates are informational; thresholds are editable, never hard-coded.
7. **Platform-agnostic.** Uber and Bolt are values, not screens; adding an app adds a value.
8. **Calm, not gamified.** No streaks, goals or decorative charts.

## Users

- **Primary:** drivers in Romania working for Uber, Bolt or both, either as a **PFA** (own business)
  or **through a fleet** (a partner company that pays them).
- **Later:** drivers in other countries, through country packs.
- **Not for:** fleet managers, accountants as primary users, people wanting trip analytics.

## Onboarding

1. Country: **Romania** (currency RON) or **Other** (pick a currency). *(Built in 0.0.2.)*
2. Romania only: **How do you drive?** PFA or through a fleet. *(Built; changeable in Settings.)*
   - PFA: tax estimate, cash-register (Z report) and fiscal receipt (CUI) features are offered.
   - Fleet: those are hidden (the fleet handles them); fleet commission becomes an income field.
   - Other countries: none of the Romanian tax features.
3. **Your car** (optional, "Skip for now"): name, fuel, consumption, fuel price. *(Built in 0.5.3.)*

## Scope

| Phase | Contents |
| --- | --- |
| **v0** (now; income, expenses, money kept, backup done) | Income entry per app per day, expenses, money kept summary, day/week/month/custom periods, backup and restore, CSV export, erase all data, Romanian and English, light and dark, country and PFA/fleet onboarding |
| **v0.5** | Cash collected per app, PFA/Fleet onboarding, vehicle (consumption, fuel price, estimated fuel cost), optional import of the Uber payments file and the Bolt monthly summary |
| **v1** (PFA, Romania) | Tax estimate (income tax, CAS, CASS) with editable thresholds; Z-report capture with month-end cash match; fuel receipt capture with CUI check |
| **Later** | Uber/Bolt API connections where possible, trips viewer, insights, other country packs, iPhone app |

Out of scope: GPS/kilometre tracking, tax filing, accounts and cloud sync, fleet management.

## Requirements (v0)

- **Income.** Default entry is one amount (what the app paid you) per app and date. Optional
  "details" splits it into fares, platform fee, bonuses, tips, cancellation fees and cash collected;
  net is calculated but can be overridden with the platform's own figure.
- **Expenses.** Amount, date, optional note, and a category inside one of three groups:
  - **Vehicle:** fuel, charging, maintenance, repairs, insurance (RCA/CASCO), inspection (ITP),
    road tax and tolls, parking, car wash, rent or lease.
  - **Business:** accountant, bank fees, phone and data, fleet partner fees, other business costs.
  - **Other:** anything else.
  Home shows expenses by group; the category list can grow without new screens.
- **Summary.** Money kept = income − expenses (and − platform fees when entered as details);
  income by app and expenses by category; period selector. Label "Earnings so far" until the
  first expense exists, then "Money kept".
- **Data safety.** Backup and restore of the whole database to a file; CSV export per period.
- **General.** Works offline, no account, 48 dp touch targets, Romanian and English, RON, European dates.

## Data model

- `Platform`: name, colour, order, archived. *(Built.)*
- `IncomeEntry`: platform, date, amount; later: details (fares, fee, bonuses, tips, cancellations,
  cash), source (manual, import, api), quality (known, estimated, missing). *(Amount built.)*
- `Expense`: amount, category, date, note; later receipt photo, CUI present, deductible %.
- `Vehicle`, `ZReport`, `TaxSettings`, `ImportBatch`: later phases.
- Money is stored as integers in minor units; one SQLite database on the device; schema changes always
  ship a migration so updates never lose data.

Privacy: never store rider names, phone numbers or addresses. The Bolt rider-invoice export contains them,
so an importer keeps only date, payment method and amount.

## Navigation

Floating navigation bar (Google Photos style) with **Home, Money, Trips, Vehicle**.
Money holds Income and Expenses as two tabs. Trips stays as a tab and fills up once imports exist.

A top-right button (where Google apps show the account picture) opens a full-screen panel with
everything that is not a daily task: Apps, Your data (backup, restore, erase), Settings. Later: cloud
backup (paid), help, feedback, privacy policy. Settings follow the Android pattern: each setting opens
its own page.

## Backup and sync

- **Local backup (free, built in 0.0.4):** one JSON file with all income, apps and settings, saved
  wherever the user chooses; "Restore" replaces everything with a backup. The format is versioned
  and independent of the database, so any backup restores into any later version. "Restore" is only
  for the app's own backups; importing Uber/Bolt reports is a separate feature.
- **Cloud backup and multi-device sync (paid, later):** with a web page explaining it. Not built yet.

## Design

"Rideshare cockpit" design system: Material 3 Expressive on Android, iOS 27 style on iPhone; seed
`#0A6C8B`, light and dark; Roboto with tabular numerals; 8/12/16/28 corner shapes; one custom green
for kept money; rounded-square app badges (no brand logos).

## Technology

Kotlin Multiplatform: shared Kotlin for data, money maths and future importers; Android UI in Jetpack
Compose (Material 3 Expressive); iPhone UI later in SwiftUI. Room (SQLite) and DataStore on device.
See the README for build, versioning and signing rules.

## Next up (decided 7 October 2026, in this order)

1. ~~CSV export and vehicle basics~~ (done in 0.0.7).
2. ~~Comparison with the previous period~~ (done in 0.0.8) on Home, shown only when that period has data:
   month-to-date vs the same days last month; week-to-date vs the same days last week;
   day vs the **same weekday last week**; custom range vs the same number of days before it.
   Compares money kept (or income while no expenses exist), as % and amount.
3. ~~Recurring expenses~~ (done in 0.0.9): weekly / monthly / yearly, due day, optional end date. On the due date a
   local notification asks to add it ("Add" button); ignored ones wait on Home under "Needs attention".
   Notification permission is requested when the first recurring expense is created. No server.
4. From the inspiration boards, before imports: **app filter chips on Home**, **daily activity chart**,
   **expenses by category** (Money → Expenses, with a month filter), **optional hours worked** on income
   entries so Home can show money per hour.
5. **Answered 8 October 2026:** the owner wants data to come in automatically (screenshots, CSV, PDF), not
   typed. Imports moved forward: database for income details, trips, period totals and import history
   (0.1.0, database 6), **Bolt** daily screenshot, rider invoices CSV and monthly PDF (0.1.0, on-device
   ML Kit text recognition). 0.1.1: Romanian Bolt screens, weekly/monthly Bolt totals, Trips tab (mockup).
   0.2.0: Home rework like the cockpit mockup. 0.2.1: labelled kept/fees bar, "platform" wording, daily activity
   rework, five metric tiles. 0.3.0: Money tab by month (total → by platform/type or category → recent).
   0.4.0: customisable Home widgets (Customise at the bottom; drag, remove, add on Home itself).
   0.5.0: estimated income from Bolt monthly totals / PDF / trips for days without a screenshot (no double count).
   0.5.1: Bolt Activity screenshot (online hours per month / week / day → RON per hour).
   0.5.2: "All" period, monthly activity bars for long periods.
   0.5.3: vehicle step in onboarding (optional).
   **Next:** Uber screenshots and CSV (owner sends the files).
   Still open: identity (name, icon, a darker "cockpit" look). The owner said the app lacked identity and had
   gone wide before deep; imports and the cockpit Home address the second.

Later, with imports (v0.5): trips list and detail, import flow with data-quality warnings, import history,
vehicle odometer and fuel estimates, gross / fees / expenses / kept bar, preset list of known apps.

## Open decisions

- [ ] App name (candidates: Tura, Kept, Ridey).
Receipt scanning at v1: on-device (ML Kit) first; server-side only later, if needed and funded.

Decided: tabs as above; income is entered per day per app; Uber/Bolt file import is v0.5.
