# Changelog

## 0.22.1
- **The ring's green part has its line**: "Kept" with its amount, above fees and expenses, so every colour on the ring
  is named.
- **✕ on the screenshots card**: hides it until tomorrow, like a swipe.

## 0.22.0
- **Screenshots card, narrower**: two rows. Today with its button, amount or "Day off" on the right, then the week with
  each day's letter beside its dot (no legend: the halves are in the apps' colours). **Tap a day** that's in to open
  it; a past day with nothing in offers Didn't drive, Open day and Import. **Swipe it away** and it's back tomorrow
  (Customise removes it for good).
- **Kept, fees and expenses as a ring**, right under money kept: the share you keep in the middle, the gross beside
  it, then fees and expenses. A layout saved before moves it there once.
- **One big number per screen**: money kept is the only headline on Home; the gross and the payout total are normal
  figures, and the target line says just "Target 3,000 EUR" (the ring shows how far you are).

## 0.21.0
- **A shorter Home** (owner, 16 Oct 2026: too many cards, too much text). Home now shows, in order:
  - **Today and this week's screenshots** at the top: "Today · Friday" with Add today (or today's amount once it's in),
    and one row of dots for the week, each dot split in half per app (Uber | Bolt) instead of a row per app.
  - **Money kept with the target inside**: a ring with the target's share, "Target: 1,810 of 3,000", what each driving
    day left needs and the pace, in two lines. Tap it for the target's settings.
  - **This week's payout**, each app's amount with its cash only when there was some.
  - **In detail**: income and per hour with their change, hours · trips · distance, the apps' shares, and **See all**
    for the full cards (breakdown, comparison, metrics, money per hour, by app, card and cash, activity, expenses,
    when you earn).
  Customise still adds, removes and moves any card. A layout saved before switches to the short one once.
- **Less text everywhere**: one figure per line, no cents on Home's own cards (rates keep them; Money, Trips and
  Details keep cents), changes as "▼ 5.5 % vs Sep", and explanations behind a small ⓘ instead of sentences on the
  cards (Home, Money's card and cash, Trips' fares, Import's privacy note and gallery tip). Shorter intros on Import,
  the target, the evening reminder, automatic backup, car documents and empty screens. Comparison rows show what it was
  before when tapped.
- **Trips**: only cash trips name how they were paid (in the app is the usual case).
- **Fixed**: trip distance said "km km"; the target said "driving days left" twice; Uber's payout showed the same
  amount twice.
- Play Store icon and feature graphics redrawn: the ring was drawn as a full circle.

## 0.20.0
- **Widgets in the phone's colours**: the Monthly target and Quick add widgets now take the colours from your wallpaper
  (Material You, like Google's own widgets) on Android 12 and newer; older phones keep the app's colours. Light and
  dark follow the phone.
- **Quick add fits its size**: one button per cell of width, in this order: 4×1 shows Fuel, Import, Expense and Income;
  3×1 the first three; 2×1 two; 1×1 just Fuel, as an icon. Two cells high, they wrap into a grid (2×2 shows all four).
- **Swipe away the month's card** on Home ("Your September is ready"): it stays in Menu → Summaries, and a message
  says so.
- **Marked days leave the list**: in a summary, tapping a missing day marks it as a day off and it disappears from
  "Days missing" (the count drops, "Days" counts it as off), with Undo in the message. Import's Day off menu works the
  same way: a marked day leaves the menu, with Undo.

## 0.19.2
- **Mark missing days anywhere.** A summary's missing days are now a card of chips: tap a day to mark it "Didn't drive"
  (it leaves the missing list and counts as a day off; tap again to undo), for any week or month, however old. On
  Home's week card, tapping a past day with nothing in opens "Didn't drive" (or "Drove after all") and Import.

## 0.19.1
- Import's floating actions (Screenshots, Files, Day off) are now three matching frosted-glass buttons; none is coloured,
  so none looks like a selected tab.

## 0.19.0
- **Weekly summary**: on Monday around 10:00 (the day after your week ends), "Last week: RON 1,840 kept" opens a page with
  the week: money kept and the change from the week before, money per hour from the apps, after fuel and after all
  expenses, days driven and days off, the best day, each app (income and per hour), and the week's payout. Days with
  nothing in and not marked as a day off are listed as missing, with Import.
- **Monthly summary**: the same page for a month, with "Play your Wrapped" at the top. It's announced once the month's
  files are all in ("Your September is ready"), or on the 10th anyway, marked "may still change". End of month shows
  "See your September" once the month is complete. Home's card for last month now opens this page (from the moment the
  month is complete, or the 10th, to the end of the month); the year's Wrapped is as before.
- **Summaries** in the menu: every finished month and the last 12 weeks.
- **Import, tidier**: Bolt's and Uber's cards are smaller (where to find the screenshot opens with the arrow), days off
  show as a dash in the week strip, and the actions sit in a floating toolbar at the bottom: Screenshots, Files and
  Day off (a menu of the last week's days with nothing in).

## 0.18.0
- **End of month, simpler.** Import's "End of month" page now shows only the month that just ended, one card per app
  with two steps: the app's own monthly total (Bolt: Monthly screenshot; Uber: payments_organization) and your hours
  (Bolt: Activity → Hours; Uber: driver_time_and_distance). Each step says where to find it and what it adds, and is
  ticked once it's in. Trips files and Uber's other files moved to "Extras", folded away.
- **The monthly total vs your days, in one tap.** When an app's monthly total differs from the month's daily
  screenshots (campaigns, a day without a screenshot), the card says by how much; "Add it to the month" adds the
  difference as one entry on the month's last day, marked "From the monthly total". It doesn't count as a day in,
  and later screenshots of that day don't replace it.
- **"Didn't drive?"** on Import's Today page: the last week's days with nothing from any app, as chips to mark a day
  off. A day off counts as done on Home and the evening reminder stays quiet that day.
- **Screenshots this week** also shows the month so far ("October: 13 of 15 days"), and in the first 10 days of a
  month, how last month's files are coming in ("September's files: 1 of 2 apps", with Import).
- **"May still change"** on last month's numbers (Home and Money) until its month-end files are in; it opens End of
  month.
- **A reminder on the 1st** (and the 3rd if still needed), around 10:00: "September is over", to get last month's
  files. It goes with the evening import reminder.
- **Weekly backup** (Your data → Weekly backup): pick one file once, on Google Drive or the phone, and Ridely saves the
  backup over it every week (and "Back up now"). It never overwrites a backup while the app is empty, and says so in
  red when it couldn't save. Android's own backup (Google account) also includes Ridely: checked on the emulator,
  data, settings and target came back.
- **This week's payout** (new Home card): per app, what it paid this week, the cash already in hand, and what's coming
  to your account (or owed to Bolt when cash is more than earned), with the total. Fleet drivers see that the fleet
  pays it, after its cut.
- **Money per hour** (new Home card): what the apps paid per hour online, then after fuel and after all expenses.
  Fuel is the fuel and charging expenses you entered (no estimates); with hours for only some apps or days, costs are
  shared out by income.
- Database 10 (days off), backup format 10 (older apps would drop days off).

## 0.17.0
- **New name and icon: Ridely** (a working name, owner 15 Oct 2026). The icon is a ring that is almost closed, with a
  light green dot where it starts, the same shape as the target widget; it has a one-colour version for themed icons,
  and notifications use it instead of the car. The Beta app is "Ridely Beta", still orange. Nothing else changes:
  same app, same data, it updates over the old one.
- **Screenshots this week** on Home: a column for each day of the week with a dot for each app, filled in the app's
  colour once that day is in (days still to come are faint), and how many days are in so far. While today has
  nothing yet, "Add today" opens Import; so does tapping the card. A day without a screenshot still counts as 0, so
  this only shows what's in. It's a Home card like the others (move or remove it in Customise).
- **Monthly target widget** for the home screen (hold the app icon → Widgets), in Material 3 Expressive
  style: a progress ring with the percentage, the amount so far in big numbers ("RON 2,160", of RON 3,000) and one
  coloured pill: what each driving day left needs, behind (red) or ahead (green) of pace, or "Done". It lays out for
  its exact size, so the ring and the number grow to fill it; small sizes show the ring and the number. It updates by itself when you import or change the target, follows light and dark mode,
  and opens the app when tapped (the target page if you haven't set one).
- The target widget fits any size: square or tall stacks the ring over the number, wide puts them side by side, and
  the number's size follows the room left, so "RON 2,160" is never cut; narrow widgets show a short pill ("RON 79/day").
- **Quick add widget**: Fuel (a new expense already set to fuel), Import, Expense and Income in one tap from the home
  screen; a row at 4×1, a 2×2 grid when square.
- **Wrapped**, like Spotify's: your month (or year) as full-screen story cards: what you kept and the change, best
  day, best weekday, best month (yearly), money per hour online, rides and distance, which app paid, your target.
  Cards advance by themselves like Spotify's (hold to pause; tap right for the next, left for the previous; not with
  TalkBack on), and their layouts vary (words top, bottom, centred or on the right, the shape in another corner). The
  last card sums it up in a colour you pick and **shares as an image**. Home
  offers last month's in the first 10 days of a month, and the year's from 1 December to 15 January; it's also always
  in the share menu on Money, for the month shown (a month still running is compared with the same days before).
- **The monthly report (PDF and image) has Wrapped's look**: the brand teal with white numbers, see-through cards,
  light green for income and light red for expenses, and an Expressive cookie shape.
- **Comparisons with last month use the same days while a month is running** (the report, Money's totals), like Home:
  15 days of October against 1–15 September, not the whole of September.
- **Kilometres or miles** (Settings → Distance): distances, odometer readings, cost per km/mile and Home's paid
  km/miles follow your choice; everything is still stored in km. Fuel consumption stays in L/100 km.
- **Demo data (Beta app only)**: Your data → Fill with demo data replaces the Beta app's data with a made-up month in
  euro (Bolt and Uber days with breakdowns, hours, rides with times, km and cash or card, fuel and other expenses, a
  target), for screenshots.

## 0.16.3
- **No screen animations, really this time**: screens open and close instantly, and the back swipe no longer moves
  anything while you swipe (only Android's arrow shows); the screen changes when you let go. 0.15.2 still had the
  navigation library's own shrink-and-overlap animation during the swipe; that's gone too. The Material motion from
  0.16.1–0.16.2 is removed.
- **Uber in Romanian**: the Earnings screen in Romanian ("Conectat(ă)", "Curse", "Detaliere", "Recompensa") is now
  read, with the day's hours online and number of rides; "Recompensa" counts as a bonus. Before, the Romanian screen
  wasn't recognised at all.

## 0.16.2
- **Back swipe now matches Android's own** (compared side by side with the Settings app): the screen behind waits
  dimmed and shifted a little to the left, then slides into place when you let go; the screen you're leaving
  shrinks to 85% early in the swipe, like the system does.

## 0.16.1
- **Material 3 screen motion is back, done properly**: opening a screen and the back button use Material's shared
  axis (a short slide with a fade, 250 ms); the back swipe is Material's **predictive back**: the screen shrinks,
  rounds its corners and lifts off with a shadow, following your finger, with the previous screen behind it. Let go
  to finish, or swipe back to cancel. Switching tabs stays instant.
- **Large text** (phone font size up to 200%): Month / Week / Day / All no longer get cut; the kept / fees /
  expenses labels, "Split by platform" and Money's total stack instead of breaking names or amounts; list notes
  (like a document's date) aren't cut off.
- **TalkBack**: the daily activity chart says what it is and which day is selected, with actions to move to the
  previous or next day; every cell of "Best time to drive" reads its weekday, hours and fares; the navigation bar
  reads as tabs with the selected one.
- Smaller polish from the Material 3 audit: the few animations inside screens use the theme's Expressive motion,
  spacing follows the 4 dp grid, the monthly report uses the app's own colours, "km" is translated.

## 0.16.0
- **One month for the whole app**: the month you pick on Home is the one Income and Expenses show, and the other
  way round (a week or day on Home shows its month there). While another month is open, **This month** next to the
  title goes back. Reopening the app starts on this month again.
- **Each car document has its own page**: tapping one shows how long it's still valid, what it last cost, the
  reminders and its **payments**. Edit and Delete are at the top (and on long press in the list).
- **Renew**: on the document's page (or from Home's "Renew"): the new date (a year on to start) and what you paid;
  the document moves on and the payment goes to expenses, linked to it.
- **Expenses linked to documents**: adding an insurance, inspection or road tax expense (or another business cost)
  offers your matching documents; picking one also asks for its new "valid until" date. A linked expense shows its
  document, one tap away.
- **New documents (Romania)**: Copie conformă, Ecuson Uber, Ecuson Bolt. The rovinietă and these show only in Romania.
- Backups keep the link between expenses and documents (backup format 9; older backups still restore).
- **Screenshots first**: Import always opens on Today: after each driving day, one screenshot from each app (Bolt's
  Daily breakdown, Uber's day), with the last 7 days ticked. End of month is still there, but no longer opens by
  itself in the first days of a month.
- **No more estimates**: a day without a screenshot counts as 0, so every number is one you imported or typed.
  Estimated days made before are removed, and Home no longer asks for Bolt's monthly total.
- **Monthly report**: the share button on Money makes a one-page report of the month shown, as a **PDF** or an
  **image**: income, expenses and profit (with the change from last month), income by app and expenses by
  category, and how many days had income. No name on it. It opens the share sheet (WhatsApp, email, print, Files…).
- **The + menu on Money opens over the Income | Expenses switch** instead of pushing it up.
- **Tax deductible on document costs** (PFA, Romania): adding or renewing a document with its cost can mark it
  deductible, like any other expense.
- **Fix: a misread Bolt day total** (e.g. "lei 161.40" read as 161,400) no longer gets saved: when every line of the
  breakdown checks out, the day's total comes from the lines. Days already saved that way are corrected when the app
  starts. Import also warns about any day over 5,000.

## 0.15.2
- **No screen animations**: screens, tabs and the navigation bar now change instantly, opening and going back alike.

## 0.15.1
- **Faster going back and opening screens**: once you let go of the back swipe (or press back), the screen finishes
  leaving in 200 ms instead of 300; opening a screen takes 220 ms. While you swipe it still follows your finger.

## 0.15.0
- **Import is guided**: its start page has **Today** (after each driving day, one screenshot per app, with where to
  find it in Bolt and Uber, and the last 7 days ticked as they come in) and **End of month** (what a finished month
  needs from each app: the finished month first, ticked as it arrives). In the first days of a month it opens on
  End of month.
- **Clear reasons when something can't be read**, with what to do instead: the week or date missing from the
  screenshot, the breakdown cut off before the commission or total, another Bolt or Uber screen, a picture with no
  text, a PDF that isn't Bolt's monthly summary, an Uber file that was renamed, a spreadsheet that isn't one of ours.
- **Import reminder** (optional, Settings or the Import page): every evening at your time (21:30 to start), "Import
  today's earnings". It stays quiet when every app you usually import already has today. Tapping it opens Import.

## 0.14.0
- **Car documents** in the Vehicle tab: RCA, ITP, rovinietă, CASCO, driving licence, or any other with your own name.
  Each has its "valid until" date and, optionally, its cost; ticking "Add the cost to expenses" adds it today in the
  right category (insurance, inspection, road tax). The list shows the days left: red from a week before.
- **Reminders**: a notification 30 and 7 days before a document expires, and on the day (with the daily 9:00 check).
  From 30 days before, it shows under "Needs attention" on Home with "Renew", which opens it to set the new date.
- Database version 8 (new table, nothing changes for existing data); backups include the documents (format 8).

## 0.13.0
- **Android's predictive back**: while you swipe back, the screen shrinks a little and slides toward the edge, with the
  previous screen behind it, following your finger (as in Android's guide and system apps). Opening a screen fades it in.
- **Comparison card on Home**: this month against last month, this week against last week, a day against the same
  weekday last week: income, money kept, expenses, hours, per hour, trips and each platform, with the change (green
  when better, red when worse). For a month or week still running: the same days, or the whole previous one. It shows
  up by itself in a customised Home (new cards do, once).
- Money's Income | Expenses switch now blurs what's behind it like the navigation bar (it floats with the bar).

## 0.12.2
- **The period stays in sight on Home**: once the month title scrolls away, the top bar shows the period you're
  looking at ("October 2026", "5 Oct – 11 Oct 2026", "All time"). Tap it to go back to the top.

## 0.12.1
- **Smoother going back** (e.g. from Settings to Home): tab screens keep the navigation bar's space the whole time, so
  Home no longer re-lays itself out mid-animation; the bar fades with its screen instead of popping; screens use
  Material's quick fade through (300 ms instead of a 700 ms fade).
- "Tax deductible" is a plain row with the checkbox on the right.

## 0.12.0
- **Tabs open at the top** when you switch to them; coming back from a trip or an entry keeps your place.
- **Uber Earnings screenshots read more reliably**: the week picker's arrow ("Oct 12 ⌄") and a "1" read as "l" in the
  online time ("1h 33 m") no longer stop the import.
- **Erase income and imports** (Your data): deletes all income, trips, hours and platform totals so you can import
  everything again; expenses, platforms, car and settings stay.
- **Import again**: a file already imported can be saved again, replacing what it saved the first time (e.g. after a
  misread).
- **Tax deductible** checkbox on expenses, for PFA drivers in Romania (database 7, backup format 7).
- Screens open and close with Android's standard animation.
- **Customise Home**: Reset and Done are in the top bar, always in reach.
- **Money**: Income | Expenses moved to the bottom, above the navigation bar, within reach of your thumb. Income is green,
  expenses red (the switch, the total and the Add button).

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
