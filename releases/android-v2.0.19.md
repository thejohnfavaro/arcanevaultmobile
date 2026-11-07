# Arcane Vault Android v2.0.19 — Card Details Tap Hotfix

## Added
- Executable DOM regression test that opens the Card Details tab layout, confirms panel ordering and repeated-open safety.

## Changed
- Card Details tab panels are anchored using an element that remains attached after the sections move.

## Fixed
- Tapping a card in Card Catalog & Collection previously appeared to do nothing: the detail layout threw a JavaScript NotFoundError before the dialog could open.
- The same layout fix already proven in the Windows edition is now applied to Android.

## Removed
- No existing features removed.

## Data safety
- No changes to collection records, card ownership, printings, metadata storage, or sync logic.
- Install over the existing Android app; do not uninstall or clear app data.
