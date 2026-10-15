# Getting Ridely onto Google Play

Everything here is a draft for the owner to check. Nothing has been published or submitted.

## Ready in the repo
- [store-listing.md](store-listing.md): name, short and full description in English and Romanian, category.
- [data-safety.md](data-safety.md): answers for Data safety, App content, content rating and permissions.
- [../privacy/index.md](../privacy/index.md): privacy policy in English and Romanian.
- [graphics/](graphics/): 512 px icon, 1024 × 500 feature graphic (EN, RO), six phone screenshots per language.
- The signed app bundle: `./gradlew :androidApp:bundleRelease` →
  `androidApp/build/outputs/bundle/release/androidApp-release.aab` (20.6 MB for 0.20.0).

## What the owner does
1. **Contact email.** Make one just for the app (for example a new Gmail). It's shown publicly on the listing.
   Put it in place of `CONTACT_EMAIL` in the privacy policy and the listing.
2. **Privacy policy online.** Switch on GitHub Pages for the repo (Settings → Pages → Deploy from branch → `main`,
   folder `/docs`). The policy is then at https://idarcky.github.io/ride-tracker/privacy/. This also publishes the
   other pages in `docs/` (the product brief), which are already public in the repo.
3. **Developer account.** play.google.com/console, personal account, one-off $25, ID check. Your developer name and
   contact email are public; if the app sells something later (Pro), Google may also show an address. Check the
   current rules on the sign-up screens.
4. **Create the app.** Name "Ridely – driver earnings", default language English (United States) or Romanian,
   App, Free.
5. **App signing: use the existing key.** In *Test and release → App integrity*, choose to upload your own app
   signing key (the one in `~/.ridetracker`, exported with Play's PEPK tool). If Play makes its own key instead,
   the Play version can't install over the APK on your phone (different signature), and you'd have to uninstall
   first and restore from a backup.
6. **Closed test (required for new personal accounts):** at least 12 testers opted in for 14 days in a row before
   you can apply for production. Upload the AAB to a closed track, add the testers' Gmail addresses (a Google Group is
   easiest) and share the opt-in link. Recruit drivers from the Facebook/WhatsApp groups.
7. Fill in the store listing, graphics, App content and Data safety from these files, then send the closed test for
   review.
8. After 14 days: apply for production access, answer Google's questions about the test, release.

## Before the first upload
- Bump the version (Play needs a new versionCode for every upload; the app derives it from `VERSION_NAME`).
- The app ID stays `app.ridetracker`. The Beta app (`app.ridetracker.beta`) stays outside Play.
- Name check: search Play for "Ridely" before creating the app.
