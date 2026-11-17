# Grid. on Google Play — plan and walkthrough

Grid is the first of the four apps to go on Play (order: Grid → Forge → Afterglow → Penumbra, see bottom).
Why Grid first: phone app, two permissions, no account, no data collected, Photo Picker (no storage permission),
nothing in it that Play policy treats as sensitive. It is the cleanest way to get the developer account through its
first review and its "production access" gate without risk to the account.

## The one hard rule that changes the code
Play forbids an app from updating itself outside Play (Device and Network Abuse policy), and `REQUEST_INSTALL_PACKAGES`
is a restricted permission that a photo app cannot justify. So the Play build must have **no in-app updater**.

Plan: two product flavors from the same code.
- `site` — today's app: GitHub updater, `REQUEST_INSTALL_PACKAGES`, `INTERNET`. Ships as the APK on the website.
- `play` — no `update/` code wired in, no `REQUEST_INSTALL_PACKAGES`, no `INTERNET` (only the updater uses it;
  check Google Fonts downloadable fonts are bundled/asset fonts first). Built as an **AAB** (`bundlePlayRelease`).
- Same `applicationId`, same version codes, **same signing key** (see Signing), so a phone with the website APK can
  later take Play updates without uninstalling.
- `targetSdk` 35 → **36** (new apps and updates must target the latest-but-one API level; 36 is the safe value now).

## Signing (decide once, never change)
Play App Signing is mandatory: you upload a bundle signed with an *upload key*; Google re-signs it with the *app signing key*.
- **Recommended: give Google the existing Grid key as the app signing key.** Then website APKs and Play installs have the
  same signature and users can move between them. Play Console → Create app → Release → App integrity → "Use a different
  key" → "Export and upload a key from Java keystore". This needs Google's `pepk.jar` run against the keystore. With no PC,
  a one-off manual GitHub Action does it: decodes `KEYSTORE_BASE64`, runs pepk with the encryption key Play shows you, and
  uploads the resulting encrypted zip as a workflow artifact. The zip is encrypted to Google's public key, so only Google can
  read it. Download it on the phone and upload it in the Console. Then create a **new upload key** (a second secret pair) for
  CI to sign AABs, so the main key stays used only for website APKs.
- If you'd rather let Google generate a new key: simpler, but Play and website builds will no longer be interchangeable
  (users must uninstall to switch). Fine if the website APK is retired once Play is live.

## Walkthrough (Play Console works in a phone browser; use "Desktop site")
1. **Account check.** Personal or organization account? Personal accounts created after Nov 2023 must run a
   **closed test with ≥12 testers opted in for 14 days in a row** before they can apply for production. Organization accounts skip it.
   Also finish the "verify you have access to a real Android device" step in the Play Console app if it is still pending.
2. **Create app**: name "Grid.", default language, App, Free. Accept declarations. Free can never become paid later.
3. **App content** (left menu, Policy → App content). Answers for Grid:
   - Privacy policy: required. Host one page at e.g. `nbansal28.com/grid/privacy` ("Grid collects no data; photos never leave
     the device unless you share the PDF").
   - Ads: No. App access: all functionality available without login.
   - Content rating: IARC questionnaire, category Utility/Productivity, answer No to everything → rated Everyone/3+.
   - Target audience: **18+ or 13+ only** (picking under-13 pulls in Families policy for no benefit).
   - Data safety: no data collected, no data shared (true for the `play` flavor with no network).
   - Government/financial/health/news: No. Advertising ID: not used.
4. **Store listing**: title (≤30), short description (≤80), full description (≤4000); icon 512×512 PNG; feature graphic
   1024×500; 4–8 phone screenshots (9:16, ≥1080×1920; take them on the phone, a finished sheet + caption studio + filters +
   PDF preview). Category: Photography. Contact email (use a public one, not a personal inbox, if you prefer).
5. **First upload by hand**: CI builds `grid-x.y.z-play.aab` as a workflow artifact → download on phone →
   Testing → Closed testing → create track → upload. The Play API can't create the very first release, so this one is manual.
6. **Testers**: create a Google Group (or an email list) with 15–20 people (margin over 12; some drop). Share the opt-in link.
   They must install and **stay opted in all 14 days**. Ask them to open the app a few times and send feedback.
7. **During the 14 days**: ship at least one update to the closed track that fixes something testers reported. Google asks
   about this in the production application.
8. **Apply for production** (Dashboard → "Apply for production"): answer in full sentences — how you recruited testers, what
   feedback you got, what you changed, why it's ready. Vague answers are the common reason this gets refused (you can
   re-apply, but each refusal costs another testing cycle).
9. **Production release**: turn on **Managed publishing** first so an approved build waits for you to press Publish.
   Roll out to 100% (or staged 20% → 100%).
10. **After that** (optional): CI uploads AABs itself through the Play Developer API (service account JSON as a GitHub
    secret, `upload-google-play` action) on merge to `main`, internal track first.

## How to make it go faster
- **The 14-day clock is the bottleneck and can't be shortened.** Start it as early as possible; the store listing,
  content forms and privacy policy can be finished while it runs.
- Run Forge's closed test **in parallel** once its Play build exists; the 12-tester rule is per app, so the same people can
  test both.
- Recruit real testers (friends, family, the website audience). Paid "tester exchange" services exist but Google looks at
  engagement, and fake-looking testing is a known refusal reason.
- First review of a brand-new account usually takes a few days, sometimes ~7+. Keep it boring: no restricted permissions,
  every declaration matches the manifest, privacy-policy link works, screenshots show the real app, no "coming soon" items.
- Later updates of an approved app are typically reviewed in hours to a day or two.
- Never reply to a rejection by re-uploading the same thing; fix, then use the appeal/"resubmit" form with a short explanation.

## The other three (order and what blocks each)
- **Forge** (2nd): fine for Play but needs more paperwork — `play` flavor without updater/`REQUEST_INSTALL_PACKAGES`;
  foreground-service declarations (special use + media processing, each needs a short video of the feature);
  camera/mic/location explained in Data safety; ML Kit noted; the "hybrid server" jobs disclosed if they ship;
  and **no "Coming soon" tools** in the Play build (Minimum functionality). `com.namanbansal.forge` is permanent once uploaded.
- **Afterglow** (3rd): TV apps get an extra Android TV quality review (slower, picky about banner, D-pad, no touch-only UI).
  Play build must drop the updater, `REQUEST_INSTALL_PACKAGES`, `READ_LOGS`, and the in-app **Home-button takeover**
  (connecting to the TV's own adb port to disable the stock launcher is very likely a Device and Network Abuse rejection).
  `QUERY_ALL_PACKAGES` is allowed for launchers but needs its declaration form. Takeover stays on the website build only.
- **Penumbra** (last / decide): the repo here only has a README, so not reviewed. If it plays streams the app doesn't hold
  rights to, keep it website-only: an IP/policy strike on one app can suspend the **whole developer account**, taking the
  other three down with it.
