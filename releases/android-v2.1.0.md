# Arcane Vault Android v2.1.0 — Consolidated Native Workspace

## Added
- Native physical edition quantity editing in the card inspector, with confirmation, an on-device pre-edit recovery snapshot, durable inventory writes and SQLite ownership synchronization.
- Preferred artwork selection through the existing saved artwork preference storage, without a separate library.
- Native Set Progress screen based on confirmed physical printings with search and per-set owned/missing printing browsing.
- Native Deck Library browsing that reads the existing saved WebView library, shows commander, entries, and combined physical/Arena missing copies.
- Native Data & Sync screen with real JSON backup exports (read-only Android content URIs), guarded pre-edit recovery, Scryfall sync trigger/status and a direct path to advanced QR/USB/import tools.
- Scoped, non-exported native backup content provider with grants only to selected Android share targets.

## Changed
- Native navigation includes Sets, Decks, and Data & Sync in the same app shell.
- Physical editing modifies existing authoritative collection storage, not a duplicated copy; editing duplicate ambiguous ownership rows is refused rather than discarding metadata.
- Android backups use real JSON attachments rather than potentially oversized share-message text.
- Existing deck and artwork preferences remain in their current legacy storage while native views replace read-only browsing.

## Fixed
- More of the Android companion is usable without launching the legacy WebView interface.
- Added stricter settings for legacy WebView file-origin access, popups, mixed content and Safe Browsing.
- Preserved verified physical printing identities; unnamed editions cannot become named ownership through editing.

## Removed
- No previously working advanced features removed. Complex deck editing, full imports, QR/USB transfers and advanced collection metadata editing still use Legacy tools.

## Safety
- Full backup before any native physical printing edit; original recovered snapshot can be restored from Data & Sync.
- Native set/deck browser and imports are read-only; Android never uninstalls itself as part of an update.
- Signed release artifacts include an APK and SHA-256. Google Play Protect warning investigation #36 remains open; signing and CI do not establish Play Protect clearance. Intended for informed, opt-in local testing.
