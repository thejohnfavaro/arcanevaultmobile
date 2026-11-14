# Arcane Vault Android v2.1.6 — one-tap sync snapshot from cold launch

## Added
- **Save Sync Snapshot (One Tap)** in native Android **Data & Sync**. Saves a uniquely named timestamped peer-format snapshot inside the app's private storage with no document picker.
- Persisted snapshot integrity and peer-format identity verification, while keeping earlier local snapshots.

## Changed
- Deck-library and preferred-artwork reads for native peer operations now wait for the existing authoritative WebView origin to finish loading in the background. The user no longer needs to open **Legacy tools** before saving a snapshot.
- Android versionName and versionCode advanced for the signed in-app updater.

## Fixed
- Cold-launch snapshot and peer export flows no longer stop with the instruction to open Legacy tools to initialize saved decks.
- Source read failure stops a snapshot instead of silently substituting an empty saved-deck library.

## Removed
- No existing functionality removed.

## Safety
- Saving a snapshot does not change inventory, decks, or artwork. Previous snapshots remain available in private app storage; uninstalling/clearing app data may remove them.
- A local snapshot is not a verified identical two-device baseline; Wi-Fi merge approval and conflict safeguards remain in effect.
- The existing signed Android release pipeline must build and verify the APK. No Play Protect safety determination is implied.
