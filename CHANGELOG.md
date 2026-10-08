# Changelog

## 0.4.1
- Undo after swiping an entry away works again (it used to delete the entry a second time), and an imported
  entry comes back with its breakdown.
- No sliding or fading between screens.

## 0.4.0
- **Customise Home**: tap "Customise Home" at the bottom of Home. Every card below money kept gets a handle to
  drag it into place and × to remove it; removed cards wait under "Add widget". Done saves the layout, Reset
  brings back the default. "When you earn" is off by default. The layout is part of backups.
- Cards with nothing for the period stay out of the way (while customising they show as empty so you can
  still place them).

## 0.3.0
- **Money tab, one month at a time** (like the cockpit design): a month chip, the month's total with the change
  from the previous month and the Add button, then:
  - Income: by platform (amount and share) and by type from imported breakdowns (ride payments, bonuses and
    tips, tolls and cancellations, commission, paid to you).
  - Expenses: by category (icon, amount, share); tap a category to see only its expenses.
  - Recent entries (5), then "See all". Swipe to delete with Undo as before; recurring expenses stay linked.

## 0.2.1
- **Kept / fees / expenses**: each label now sits right under its part of the bar, with its share; gross and
  what it's made of below.
- **"Platform" instead of "app"** everywhere for Uber, Bolt and the others (Menu → Platforms, Split by platform).
- **Daily activity** reworked: days driven, best day and average per day driven; labelled days, an average
  line, and the selected day's amount above the chart. Switch between money and trips when both exist.
- **Trips, paid km, hours online, per hour, per km** always shown; a dash says what's missing, and a figure
  that covers only some platforms says so ("Bolt only"). Paid km also comes from Bolt's monthly summary PDF
  when the period is that month.
- "Best time to drive" is now "When you earn" (it shows when you drove, not pay per hour).

## 0.2.0
- **New Home** (like the cockpit design):
  - Money kept (or income) with a cumulative line for the period, and the comparison with the previous one.
  - A bar of gross split into kept, platform fees and expenses, with the figures under it and what gross is
    made of (fares, bonuses and tips, other). Fees come from imported breakdowns; typed-in days are noted.
  - Trips and average fare from imported trips; hours, per hour, km and per km appear when a report has them.
  - Split by app (donut), daily activity (stacked per app; tap a bar for the day, then "Open day") and
    best time to drive (trip fares by weekday and time of day, once there are 20+ trips).
  - Chart colours checked for colour-blind readability, in light and dark mode.
- Breakdown lines saved as "other" by an earlier version are re-sorted when the app starts.

## 0.1.1
- **Trips tab**: imported rides by month, grouped by day (day total and count), with app and payment
  filters (cash / in the app) and a total of fares. Tap a ride for its details; anything the report
  doesn't include shows a dash. Rider details are never stored.
- **Bolt in Romanian**: screenshots of „Defalcarea câștigurilor” are read too (Plăți pentru curse, Bacșiș,
  Campanii, Drum cu taxă, Taxe de anulare, Comision Bolt, Numerar în mână), even when accents are misread.
- **Weekly and monthly Bolt screenshots** are accepted as Bolt's own totals for that period (kept for
  checking, not added as income).

## 0.1.0
- **Import from Bolt** (Menu → Import, the + button on Home, or Share → Ride Tracker from the gallery):
  - **Daily earnings screenshots** (Earnings breakdown, Daily tab): read on the phone, shown for checking
    (a tick when every figure adds up), then saved as that day's Bolt income with its breakdown
    (ride payments, tips, campaigns, promotions, tolls, commission) and the cash you collected.
    A screenshot replaces what you had for that app and day; the same file is never imported twice.
  - **Rider invoices CSV**: saved as trips (time, price, cash or in-app). Rider names and addresses are
    never read into the app.
  - **Monthly summary PDF**: Bolt's own totals and km, kept for checking your days (not added as income).
- Imported income shows its breakdown when you open it.
- Database version 6 and backup format 6 (income details, trips, monthly totals, import history).

## 0.0.10
- Romanian: the repeat switch is now called „Recurentă”.

## 0.0.9
- **Recurring expenses**: turn on "Repeat" when adding an expense (weekly, monthly or yearly, optional end
  date). Nothing is added by itself: when one is due, Home shows it under **Needs attention** with
  **Add** / **Skip**, and a notification with an **Add** button arrives once (daily check around 9:00,
  on the phone, no server). Manage them under Money → Expenses → Recurring expenses.
- Monthly dates on the 29th–31st fall on the last day of shorter months, then go back.
- Car silhouettes removed (the vehicle card shows the car icon again).
- Database version 5 and backup format 5 (recurring expenses).

## 0.0.8
- **Comparison with the previous period** on Home, under money kept: month and week so far vs the same
  days of the previous one, finished months/weeks vs the whole previous one, a day vs the same weekday
  last week, custom ranges vs the same number of days before. Shown only when that period has data.
- **Car silhouette**: choose body type (hatchback, sedan, estate, SUV, MPV, van) and colour; drawn offline.
- **Hybrids**: petrol or diesel engine, plug-in or not.
- **Undo** after deleting an odometer reading.
- Lighter look: no grey behind the menu panel; Vehicle cards use the normal background with an outline.
- Database version 4 and backup format 4 (vehicle body type and colour).

## 0.0.7
- **Export to CSV** (Menu → Your data): this month, last month, this year, all time or a custom range.
  One file with income and expenses; international format (comma-separated, dot decimals, ISO dates),
  column titles in the app language, opens correctly in Excel and Google Sheets.
- **Vehicle** tab: your car (name, year, fuel, consumption, fuel price), odometer readings with history,
  and per month: km driven, vehicle costs, cost per km, fuel spent and estimated fuel cost.
- Odometer readings that go backwards or jump impossibly far (typos) are refused.
- Swipe-to-delete reacts to quick swipes too; tap a reading to delete it.
- Database version 3 (vehicle tables) and backup format 3; older backups still restore.

## 0.0.6
- Expenses: add, edit and delete (swipe, with undo) in Money → Expenses. Three groups (Vehicle,
  Business, Other) with categories such as fuel, repairs, insurance, ITP, accountant, bank fees.
- Home shows **Money kept** (income − expenses) once you log an expense, plus expenses by group.
- Home's add button is now a menu: Add income or Add expense.
- Backups include expenses (backup format 2). Backups from 0.0.4–0.0.5 still restore.
- First database migration (version 1 → 2), tested by upgrading 0.0.5 with data to 0.0.6.

## 0.0.5
- Tabs reordered to Home, Money, Trips, Vehicle.
- Top-right menu button opens a full-screen panel (Google Photos / Google Health style) with Apps,
  Your data and Settings, using Material 3 Expressive segmented lists.
- Settings: every setting opens its own page with a back arrow and a large title that collapses on scroll.
- Your data page: back up, restore, erase all data.

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
