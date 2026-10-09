# Changelog

## 0.11.0
- **"Needs attention" is pinned at the top of Home**, above the total, and only shows when something is waiting. It's
  compact now: one line per item with its button (Raportul Z „Gata", a recurring expense's Add/Skip, a month's missing
  Bolt total → Import), the first three and then "N more". The setup chips (monthly target, Raportul Z) sit at its
  bottom. It's no longer a card you can move or remove in Customise; the other cards still are.

## 0.10.1
- **Monthly target page has a Save button.** Change the amount, what it counts and your driving days, then Save;
  leaving with Back keeps the target as it was. The keyboard's ✓ closes the keyboard.

## 0.10.0
- **Monthly target.** Settings → Monthly target (or the "Set a monthly target" chip on Home): how much you want to make
  this month, counted as income after platform fees or as money kept after expenses, your choice. Each month keeps its
  own target; a new month starts with last month's.
- **Target card on Home**: how far you are (wavy progress bar), what's left, what each driving day still needs and
  whether you're ahead of or behind pace. Driving days are Mon–Sat unless you change them. Tap the card to change the
  target. If you customised Home before, the card is added at the top when you set a target.
- **"Monthly target reached"** notification, once a month, when an import or entry takes you over it.

## 0.9.1
- **Ride Tracker Beta**: a second app with an orange icon, installed next to Ride Tracker, with its own data. New
  versions go to Beta first for testing (real imports, deleting, anything) and to Ride Tracker once they're good.
  To test with your data, back up Ride Tracker (Your data → Back up) and restore the file in Beta.
- Raportul Z's page text is shorter.

## 0.9.0
- **Uber screenshots** (Uber Driver in English), so Uber can come in without the Supplier portal files:
  - **Earnings, a day tapped**: that day's income, hours online and trips. **No day tapped**: the week's total and
    hours. Ride Tracker tells them apart by the tapped day's blue label.
  - **Payments** for a week: customer fares, **Uber's fee**, Quest and other bonuses, airport fees and tips. One long
    screenshot (Capture more), or the two halves picked together, which are joined into one.
  - Each can be corrected before saving, like Bolt's.
- **A week's total fills that week's missing days**: Uber's (or Bolt's) weekly total minus the days you imported,
  on the days with trips, or today for a week still running. Days in Uber's payments file are never filled.
- **Raportul Z reminder** (Romania): Settings → Raportul Z, one time for every day. The notification has "Gata"; a day
  not marked done waits under "Needs attention" on Home. A chip on Home offers to set it up. For the exact time,
  Android 14+ asks once to allow "Alarms & reminders".
- "Your Uber data" mentions the screenshots as an alternative to the files.

## 0.8.3
- The navigation bar has its thin outline back, and the **+** is exactly as tall as the bar.

## 0.8.2
- **New navigation bar:** no outline, just frosted glass, with a round blue **+** beside it (like Google Photos'
  bar and search button). The + is on every tab, so the bar never moves; it opens Import, Add expense and Add
  income as before.

## 0.8.1
- **Fix:** the sheet for correcting an import was cut off about two-thirds of the way down (no Cash in hand, no
  Save), with blurred glass above it. The glass now follows the sheet as it slides up.

## 0.8.0
- **Uber import (CSV).** From supplier.uber.com → Reports, Uber gives a dozen files; Ride Tracker reads the four it
  needs and says so for the rest ("Uber · not needed": they repeat the others or hold your phone and email, and
  are never read):
  - **payments_order**: your income for each day after Uber's fee, with fares, bonuses (Quest, promotions,
    rewards), cancellation fees, airport fees refunded and cash. Each day replaces what Uber had on that day, so a
    later export that overlaps an earlier one counts nothing twice. Tap "Show days" to correct any day before saving.
  - **payments_organization**: the month's totals with **Uber's fee** and the fares before it (Money's breakdown
    and Home's fees). Editable before saving.
  - **trip_activity**: each trip's time, km, length and how it was paid (addresses are not saved); its earnings
    come from payments_order, in whichever order the two are imported.
  - **driver_time_and_distance**: hours online (money per hour) and km driven.
- Romanian or English columns; the files are recognised by the names Uber gives them.
- **Your Uber data** on the Import screen: for this month and last, which of the four files are imported, and where
  to find each.
- Days covered by Uber's payments are never estimated from trips.
- An Uber trip shows what you earned on it (Uber's reports don't have the rider's price).
- **Fix:** Bolt daily screenshots at full size (straight from the phone) weren't recognised: text recognition read
  the date as "1oct." without a space.

## 0.7.2
- **Frosted glass everywhere things float**, like the navigation bar: the month list and every other menu,
  dialogs (delete, choices, currency), date pickers and the import edit sheet. What's behind shows through,
  blurred; dialogs are a little more opaque so text stays easy to read.

## 0.7.1
- **New period header on Home**: the month is a large title ("October 2026 ▾"); tap it to pick another month or
  "Custom range…". Below it, Month · Week · Day · All as before. Arrows only for Week and Day; Month and All
  don't need them.
- Money uses the same month title.
- Menus have the plain background with an outline instead of grey.

## 0.7.0
- **Correct an import before saving.** Each read screenshot or PDF has an Edit button: change the date, any amount,
  remove a line or add one Bolt shows but wasn't read, fix hours. A live check says whether the lines add up to
  the earnings. Corrected items say "Corrected by you".
- **Type in a screenshot that wasn't recognised**: "Type in a day" or "Type in hours" on its card.
- **Your Bolt data** on the Import screen: for this month and last, what's imported and what's missing (Monthly
  screenshot, daily screenshots, trips CSV, online hours). Tap a row to see where to find it in Bolt.
- The edit sheet uses the plain background, not grey.

## 0.6.1
- **No more swipe to delete.** Long press an entry, expense or odometer reading for a menu with Edit and Delete;
  Delete asks first, then Undo is still offered. Tap still opens the entry.
- **Tap the tab you're on to go back to the top** (Home, Money, Trips, Vehicle).
- The floating navigation bar is **frosted glass**: the page shows through, blurred, with a thin outline instead
  of a grey bar. The selected tab uses the app's blue.
- The odometer's "Update" button is solid blue instead of grey.
- Money's income card is now called **Breakdown** ("Defalcare"), the word Bolt uses.

## 0.6.0
- **Income by platform**: in Money → Income, tap a platform to see its month: rides, bonuses and campaigns, tips,
  rider credits, cancellation and road fees, minus commission, adding up to what it paid you. A month with
  Bolt's Monthly screenshot takes the parts from it; income without a breakdown is shown as such.
- **Card and cash**: how much came in through the app and how much in cash (before commission), the cash you kept
  and what was paid to your account (earned minus cash in hand). From imported trips: how many were paid each
  way. In Money (all platforms and per platform) and as a new **Card and cash** widget on Home (add it with
  Customise if you've changed your Home layout).
- Money → Income's "By type" card is now "What it's made of", with every kind of income listed.

## 0.5.4
- **Bolt Activity screenshots in English** ("61h 12m", "Past 3 months", "HOURS") are read, as well as past week
  tabs written "Sep 28 - Oct 4".
- **Daily breakdowns dated "1 oct."** import again: text recognition sometimes reads "Oct" as "0ct" or a lone
  "1" as "l".
- When a screenshot isn't recognised, the message says what can be read and to tap a month's bar first for hours.
- **Needs attention** on Home lists finished months whose income is estimated from the PDF or trips because
  Bolt's monthly total is missing (no bonuses), with an Import button.
- Without a daily breakdown, the usual commission share also comes from Bolt's weekly or monthly screenshots.
- New expense category **TVA intracomunitar** (Business), offered in Romania to drivers on their own (PFA).
- Opening and closing screens slides like Android Settings (Material shared axis), and the back gesture follows
  your finger. Switching tabs stays instant.
- The + button sits closer to the navigation bar; long values like "12 h 48 min" shrink to fit their tile.

## 0.5.3
- **Your car in onboarding**: after country (and, in Romania, how you drive), a "Your car" step asks for the
  car's name, fuel, consumption and fuel price. Optional: "Skip for now" and add it later under Vehicle.

## 0.5.2
- **"All" period** on Home (Month · Week · Day · All · Custom): everything from the first month with data to
  today. Long periods (All, or custom ranges over two months) show activity per month; tap a month to open it.
- Hours online add up Bolt's totals that fit the period without overlapping (a month, then weeks, then days).

## 0.5.1
- **Bolt online hours** from the Activity screen (Online hours tab): "Last 3 months" with a month tapped gives
  that month's hours; a week tab gives the week's hours and the tapped day's. Home shows hours online and how
  much you make per hour for those periods. A newer screenshot of the same period replaces the older one.

## 0.5.0
- **Income from Bolt's monthly totals and trips, without counting twice.** Days with a daily screenshot (or typed
  in) stay exact. The rest of the month is filled in, best source first: Bolt's monthly breakdown screenshot
  ("Câștigurile tale" minus the exact days), else the monthly summary PDF (fares after your usual commission, plus
  tips; it has no campaigns), else the trips' fares after commission. It's spread over the days with trips by
  their fares and marked **Estimated** (label in lists, outlined bars, a line on Home). A screenshot of a day
  replaces its estimate; an estimate you edit becomes yours.
- For a month with Bolt's monthly breakdown, Home and Money take gross, commission, campaigns and tips from it.

## 0.4.2
- Importing CSV and PDF files works however the phone labels them: a CSV marked as an Excel file, a PDF opened
  from Gmail or Drive, a UTF-16 spreadsheet export. "Choose PDF or CSV files" now lists every file; anything
  that isn't a Bolt report shows as not recognised. Share → Ride Tracker also accepts CSVs marked as Excel.
- Bolt's monthly summary: its "Bolt Fee" is Bolt's other costs and fees, not the commission, so it is no longer
  shown as commission.

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
