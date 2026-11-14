# Arcane Vault for Android — v2.0.30 (Android Studio project)

**This repository contains Android application source; signed APKs, when available, are published separately through the Android release workflow.** The portable features through Windows 5.17 have been incorporated into the separate Android project; Windows-only log reading, PC deck discovery, and desktop in-app installation cannot operate directly on Android. See `CHANGELOG_2_0_0.md` for the complete feature inventory and limitations.

## Building an APK

1. Open this folder in **Android Studio** with **Android SDK 35** and **JDK 17**.
2. Use the checked-in Gradle **8.13** wrapper (`gradlew`/`gradlew.bat`) with a compatible Android Gradle Plugin and run **Build → Build APK(s)**. Alternatively run `BUILD_ANDROID_APK.bat` if you have a compatible Gradle installation.
3. The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. This is a personal testing build, not a signed production release.
4. Alternatively, upload the project contents to the root of an Android GitHub repository and run the bundled `Android Debug APK` GitHub Actions workflow. Its build artifact is the debug APK.
5. Before updating an existing installation, export **Import & Data → Save full backup**. The package ID is unchanged (`com.arcanevault.mobile`), but an APK signed with a different debug key cannot be installed over an existing version without first uninstalling the old app. Uninstalling clears local app data; keep a backup.

## Current Android source and update safety (v2.0.25)

- The latest observed `main` CI run (November 10, 2026) **passed**, including JavaScript regressions, the JVM printing-identity golden test, and debug APK packaging. This is **not** the same as an on-device validation or a signed release.
- A debug APK must not be used to replace a release-signed installation when their certificates differ. Preserve a private full backup and use the matching signed update channel.
- Name-only physical ownership and unknown printing IDs remain **unresolved**. Do not infer set ownership or fill Set Progress from names alone.
- The Android A1 reliability/parity scope and acceptance criteria are tracked in [issue #24](https://github.com/thejohnfavaro/arcanevaultmobile/issues/24). It prioritizes safe Windows QR/USB transfers, print identities, Set Progress, and current app data preservation.

## Android v2.0.30: native in-app update checker

- On the native Home screen, select **Check for app updates**. Android discovers versioned GitHub releases, validates the signed APK and SHA-256, and asks for approval before running the system installer.
- The installation is opt-in and uses the existing app package and matching signing certificate. A debug APK with a different signing certificate is rejected.
- Android may ask to allow installs from this source. Backup your collection before updating, particularly when switching between old test builds and production signed releases.
- If no matching signed release is published, the app reports that no newer signed update is available.

## Android v2.0.29: native-first app foundation

- The default Home, Physical, and Arena collection browsing now use Android-native views instead of HTML/WebView.
- Native collection lists search existing stored inventories without rewriting the data and show additional records in bounded pages.
- All features not yet migrated remain in **Legacy tools**; Android Back returns to native home. Existing WebView storage recovery remains intact.
- This is phase one of a staged migration, not yet a fully native replacement of all features.

## Android v2.0.28: Set Progress help

- Expand **Showing 0% or missing owned cards?** in Set Progress for source selection, catalog troubleshooting and safe physical printing-identity recovery without changing quantities.
- This is explanatory only; it does not fabricate missing set ownership or change any inventory.

## Android v2.0.27: clearer inventory reset confirmation

- **Clear Physical**, **CLEAR PHYSICAL**, and other capitalization variants are accepted as the same typed confirmation. Choose a scope and check the backup acknowledgement to enable the button.
- A live prompt now distinguishes between a missing phrase and the unchecked backup acknowledgement.
- No collection reset happens automatically. All reset/undo safety requirements from 2.0.26 remain in effect.

## Android v2.0.26: selective inventory reset

- In **Data & Sync → Clear inventory**, choose **Physical only**, **Arena only**, or **Both**. This does not delete saved decks, artwork preferences or settings.
- **Save full backup** and verify that the JSON file exists outside the app **before** clearing; then select a scope, enter its exact confirmation phrase, and acknowledge the backup warning.
- Native Android durable storage clears selected inventories in one guarded write and retains the prior affected inventory for **Undo last clear**, provided no further import/changes occurred. Indexed catalog/Set Progress ownership mirrors refresh afterward; reopen the app if refreshing was interrupted.
- **Physical CSV defaults to Replace physical collection**, which does not add old quantities. Use **Add quantities** only when deliberately increasing counts.
- A Debug APK is not safe to install over a differently signed release. Use the normal update checker with the correctly signed release, and export a complete backup before changing installations.

## Notable Android 2.0 features

- Arena and physical inventories kept separate, with CSV import from common physical collection sites and standalone physical exports.
- Card Browser source availability (All Magic Cards / Arena Only / Physical Only) and both owned counts displayed on cards.
- Set Progress Arena/Physical toggle, including native SQLite-backed Owned/Missing set drilldowns.
- Collapsible grouped sidebar, favorites, quick search and compact navigation, alongside bottom navigation shortcuts.
- Manual/imported match history and deck statistics with deck dropdown and backups (not live PC log capture).
- Full-size card artwork in Goldfishing, offline fallback, existing deck library, strategy tools and Scryfall catalog tools.

## Validation status

The November 10, 2026 GitHub Actions Android Debug APK job passed its JavaScript regressions, JVM print reconciliation test and debug APK packaging. Original archive preparation notes below are retained for history; a green CI run does not replace physical-device QA. A headless Chromium smoke test of the page initialized without JavaScript errors, opened the mobile grouped sidebar, added a physical card, switched Set Progress to Physical and recorded a manual match. This is not Android WebView or on-device testing. **Physical-device installation and production update acceptance are still required.** Android Studio/CI build success does not guarantee that an installed release-signed app updates cleanly.

---

## Update and creature-type synergy improvements

See `RELEASE_GUIDE.md` for how to publish release tags, configure update notifications, and search by creature-type synergy.

# Arcane Vault Android 0.2 — Local Scryfall Catalog

This Android Studio project adds a **native SQLite catalog backend** to the 0.1 mobile app. This is **source code**, not a prebuilt APK; it has not been compiled on Android in this environment.

## Build

Open the folder in Android Studio (Android SDK 35, JDK 17), generate a Gradle wrapper if needed using Gradle 8.11.1 (`gradle wrapper --gradle-version 8.11.1`), sync dependencies, then Build APK(s). Minimum Android 8.0 (API 26). An Internet connection is required for Gradle dependencies and Scryfall catalog downloads.

## New in 0.2

- **Sync Catalog + Variants** downloads the Scryfall `default_cards` bulk dataset discovered from `https://api.scryfall.com/bulk-data`. Supports gzipped JSONL and JSON-array fallback, and keeps existing catalog tables if download/indexing fails.
- **All / Unowned / Owned searches** with rarity, text, types, color identity, mana value, sort, role heuristics, and Arena-listed filters, served from local SQLite.
- **Set Progress** completion by unique card name, set drill-down, owned/missing filters, set symbols, and CSV page exports. Owned by name does not imply ownership of a specific printing.
- **Printing variants gallery** by card name and optional Arena-only filter, using Scryfall's printing records (not all in-client cosmetic styles).
- **Collection ownership updates** persisted to native SQLite after CSV import and refreshed from the interface.

## Important limitations

- The catalog download contains many printings and can use **hundreds of MB of data and additional temporary SQLite space** while indexing. Use Wi-Fi and have ample free storage. Synchronization runs on a background thread but is not resumable and will stop if Android terminates the app process.
- Card art loads from Scryfall URLs when online and may be WebView-cached; all official images are not permanently downloaded.
- Arena `Player.log`, match history, and automatic PC match detection remain Windows-only.
- The Android catalog uses an app-private SQLite DB (`arcane_catalog.db`). Web UI decks and preferences remain in WebView storage. There is no automatic PC sync.
- An Android Studio build and on-device verification are still required. No claim of functional, compiled APK is made.

## Privacy and licensing

No user collection is bundled. The catalog sync downloads public Scryfall data. Scryfall is an independent service, and card images and data are subject to their terms. No Arena account access is performed.


## About / attribution update

The Import & Data → About Arcane Vault panel now displays “By RNGesus · Copyright © 2026”. Scryfall attribution is retained.


## Android 0.4: legal attribution

The Import & Data → About Arcane Vault section now distinguishes the original Arcane Vault copyright (RNGesus, 2026) from Wizards of the Coast trademarks and copyrights and from Scryfall API/data attribution. Fan-content and API documentation links are included. This is an attribution notice, not a grant of rights.

UI update: Removed the “How Art Works” sidebar panel; retained a hidden metadata-status node for existing sync code.

## Android 0.6 — Card data sync history

Import & Data now shows last successful sync dates for owned-card Scryfall details and the full card-printings catalog separately. The details date is persisted in local WebView storage; the full catalog date is obtained from the existing local SQLite-backed `/api/catalog/status` endpoint. Failures and canceled runs do not overwrite the previous successful timestamps. On first installation, a never-synced status is displayed. Older detailed-card syncs cannot be dated retroactively.


## Android update: Sync reminders and illustrated Deck Library

- Deck Library now presents saved decks as faux deck boxes with commander art (uses synced art when available; otherwise loads a Scryfall named-card image). When offline without cached art, a decorative placeholder is shown.
- Deck Library includes search by deck/commander and sorting by name, commander, or most recent change.
- Import & Data includes an opt-in/out stale-data reminder. A discrete notification appears when either successfully synced card-data source is 30 or more days old, with Review and Dismiss controls. Dismissal hides the notice for the remainder of the current local date; dates remain in the existing sync history.
- No schema changes to decks, collection, or catalog databases. Artwork ownership remains with its respective rights holders.

Validation: Embedded JavaScript syntax checked with Node.js; archives tested for integrity. Native Windows/Android build not verified in this environment.


## Deck Library update — 0.8

New features: select any card in a deck as cover art; mark favorite and filter favorites; show commander mana-color badges when metadata is available; organize decks with named folders and tags; automatically save the last 20 revisions before updates, create manual snapshots, and restore earlier versions; show the missing-card count and detailed missing-card list against the imported collection. A selected cover uses local synced image metadata when available and Scryfall as an online fallback. Existing libraries are compatible; JSON library backups now retain all new metadata and snapshots. These features are available on both desktop and mobile.

For Windows, open ArcaneVault.sln in Visual Studio. For Android, open the project in Android Studio. The app has not been compiled in this environment.


## Mana symbols and Deck Studio validation update

Deck Library uses actual Magic mana symbol SVGs from Scryfall (`https://svgs.scryfall.io/card-symbols/{W,U,B,R,G,C}.svg`), displayed in WUBRG order. These are network-loaded, not local assets; when inaccessible, a textual letter fallback is shown. In Deck Studio, validation guidance explains how to address blocking issues and informational warnings. No deck metadata format changes.


## Deck Studio — Validate Deck update

Use **Validate Deck** in Deck Studio to fetch fresh Scryfall card metadata for the current commander and card list (batched in groups of 75) and rerun count, color-identity, singleton, and Brawl restrictions. On successful metadata refresh, stale validation errors will disappear when resolved. Explicit `banned` statuses remain blocking; Scryfall `not_legal`/missing `historicbrawl` metadata is not conclusive evidence of an Arena ban. Missing Scryfall Arena IDs are not automatically treated as illegality. A failed network request retains the existing information and shows an incomplete-refresh message. Arena remains the final authority.

Only deck-specific metadata is refreshed. No decklist or ownership modifications are made.


## Favorite button alignment (1.1)
The Deck Library favorite circle and star are now centered with CSS grid, zero inherited button padding, and consistent font/line-height.


# Arcane Vault — Strategy Workbench release

This release continues from Windows 4.8.21 and Android 1.1. It adds a shared **Strategy Workbench** accessible via the left navigation (on Android: **More → Strategy Workbench**).

## Implemented in this release

1. **Mana-base analyzer**: counts known lands, ramp role tags, average known mana value; computes exact hypergeometric probabilities for natural land drops 2/3/4, assuming a shuffled 99-card library with a commander excluded (no mulligans, ramp, scry, MDFCs, or Arena play-state simulation). Missing card metadata is flagged.
2. **Opening-hand sampler**: randomly shuffles a selected deck's library and deals seven cards, with a new-seven reshuffle and one-card draw. No game rules or opponents are simulated.
3. **Deck comparison**: side-by-side land, mana value, role counts and exact name/quantity changes between a selected deck and any saved deck.
4. **Function search**: keyword + curated card-role search over *the imported collection with synced metadata*; Owned Only option. Does not cover every unowned card in the Scryfall bulk catalog.
5. **Collection-aware commander discovery**: ranks owned, metadata-verified commanders by number of compatible owned names and cheap spells. Scores are *coverage*, not competitiveness, win rates, or Arena format-legal guarantees.
6. **Collection import change history**: logs counts of added, removed, and quantity-changed names following successful CSV imports; persists locally, last ten entries, and travels with full JSON backups. Older backups without the field still restore. The previous one-step collection rollback is unchanged.
7. **Navigation to full backups**: Workbench backup shortcut invokes existing full JSON backup command.

The following existing tools remain available, unchanged: deck upgrade/synergy guidance, Wildcard Planner, Arena deck validation, Set Progress, and Windows match tracker. The same backup JSON structure can be moved manually between platforms through the existing Export/Restore flow; no automatic network sync is claimed.

## Not yet implemented

These larger roadmap features **are not included** in this release: full rules-engine goldfishing, predictive deck strength or win rate, automatic peer-to-peer/cloud Windows-Android sync, offline download/persistent image storage for every Scryfall image, per-match opponent-color metadata beyond what logs expose, and full-catalog indexed function search for unowned cards. Those require backend, permission, storage, or design changes and dedicated native/on-device testing.

## Installation

**Windows:** Extract, open `ArcaneVault.sln` in Visual Studio, Clean Solution, then Rebuild Solution. Existing app data remains outside the solution in local storage and the app database. Export a full backup before upgrading.

**Android:** Extract, open in Android Studio with Android SDK/JDK configured (details in the original README). Build/install an APK. Existing WebView data usually persists over an upgrade with the same application ID, but export a full backup first. No compiled APK is included.

## Verification

- Both embedded application scripts: `node --check` passed.
- Workbench controls and navigation IDs validated.
- Probability implementation tested for small exact-case hypergeometric outcomes.
- ZIP integrity checked after packaging.
- **Native Windows and Android compilation, Arena log parsing, and device interaction have not been run in this environment.**

## Rights

Arcane Vault original software by RNGesus © 2026. Magic: The Gathering and related marks/artwork are owned by Wizards of the Coast and relevant rights holders. Existing in-app fan-content and Scryfall disclosures remain unchanged.


## Strategy Workbench upgrade: 2026-11
- Turn-by-turn simplified goldfish: on play/draw, draw step, one land drop per turn, undo land, logging and fresh opening-seven reshuffles.
- Deck Studio verification report separates structural violations and missing metadata, with copyable report.
- Goldfish does not resolve spells, opponents, color sources, mulligan bottoms or conditional land abilities.
- Existing decks/backups and catalogs remain unchanged.


## vNext: Offline artwork, mana and goldfish, repeatable tests

- **Offline artwork:** Import & Data > Offline artwork cache. Images from Scryfall's official card and SVG delivery hosts are retained on demand in app-private storage. Storage is capped at 200 MiB, oldest entries evicted. Includes a clear-cache button. Only previously visited artwork is available without a network connection; API-generated card images from `api.scryfall.com` are not cached.
- **Mana analysis:** Conservatively identifies basic/explicit-color land sources and reports uncertain lands separately; this is not a full land-rules model.
- **Goldfish:** Choose which land to play, perform London-mulligan bottoming, and cast a selected spell using a **mana-value-only estimate** with tracked tapped/untapped lands. Colored mana costs, conditional restrictions, land-entry timing, card effects and triggered abilities are not enforced. The feature is explicitly *not* a Magic rules engine.
- **Regression checks:** Execute `node tests/regression.mjs` to verify both the embedded probability implementation and key cross-platform service contracts.
- **Build validation:** Native Windows/Android compilation and offline artwork downloads require testing on the respective platform. This source package has not been compiled as an EXE or APK in this environment.

### Android 1.6 — Creature type labels
Card detail badges now display creature types directly (e.g., Zombie instead of Zombie synergy). Deck Studio and Strategy Workbench filters say Creature type. Functional role tags and filtering logic are unchanged.
