# Arcane Vault Android — 2.0.0

## Added

- Separate **physical card inventory** with manual editing and CSV imports. Detects common ManaBox, Moxfield, Mythic Tools, Archidekt, Delver Lens, TCGplayer-style and Deckbox-style CSV columns. Store set, collector number, finish, language, condition, and binder/location without changing Arena ownership.
- **Collection source** toggle in Set Progress: Arena or Physical. Progress, owned/missing set drilldowns, and set CSV exports use the selected source. Catalog scope (Arena-listed or all printings) stays independent.
- **Physical Only** card browser view, with Arena and Physical copy counts on card tiles and details. Availability options are All Magic Cards, Arena Only, and Physical Only.
- **Grouped sidebar** with expandable categories, favorites (up to 5), quick page search, keyboard shortcut Ctrl+K (hardware keyboards), compact desktop/tablet sidebar, and the Android bottom navigation bar with More opening the grouped sidebar.
- **Full-card artwork in Goldfishing**, including text fallbacks when a card image cannot be loaded.
- **Manual match history** on Android with saved-deck dropdown, version, format, win/loss/draw, game record, corrections, grouped stats, and CSV import/export. Imported matches are distinct from automatically verified PC log entries.
- Android Debug APK GitHub Actions workflow and targeted cross-platform parity regression checks.

## Changed

- Physical collection and manual match history are included in full JSON backups. Existing Arena ownership and saved decks remain separate.
- Set Progress checks use Android's separate physical-ownership SQLite table without overwriting the Arena-ownership table.
- Goldfishing preserves the previously existing simplified rules model (not a full Magic rules engine).
- Android update notifications target only release tags carrying an Android `.apk`, and no longer mistake Windows/source archives for installable Android updates.

## Fixed

- Availability source selector initialization in the Android interface.
- Mobile navigation no longer disables the native local catalog browser and its availability filters.
- Restoring a full backup preserves deck IDs needed to associate manually recorded matches with saved decks.

## Removed

- Outdated Android 0.2 mobile notices claiming Set Progress and match CSV import are unavailable. Automatic Windows log tracking is **not** removed from the Windows app; it remains a desktop-only capability.

## Platform limitations

Android cannot directly read MTG Arena's Windows `Player.log`, automatically discover desktop decks, or receive desktop in-app Windows updater packages. Use Windows Arcane Vault for automatic capture and Android's JSON/CSV import tools for manual transfer. No cloud/live cross-device synchronization is included.

## Build

Open the project root in Android Studio with SDK 35 / JDK 17 and Gradle 8.11.1 available; sync and run **Build > Build APK(s)**. Or push this project's contents as the root of an Android GitHub repository and run the included **Android Debug APK** action. The generated debug APK is for personal testing and uses Android's debug signing key; it is not a Play Store or signed production release. Retain the same signing key for future Android updates and export a full backup before replacing a previously installed app.

## Validation performed

Node JavaScript syntax checks and the bundled regression scripts pass. The native Android SDK/Gradle toolchain is not installed in the preparation environment; APK compilation, installation, and on-device behavior have **not** been verified. Never treat this source archive as an already-compiled APK.
