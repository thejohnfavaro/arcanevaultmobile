# Arcane Vault Android v2.1.98

## Added
- List / Art + Details presentation switch within the grouped Visual Mainboard in native Deck Editing.
- Compact 70×95dp official card thumbnails beside each visible draft card in Art + Details mode.
- Artwork thumbnails tap through to the same full card inspector as the card-name row.
- Display-mode preference remembered locally for subsequent editing sessions.

## Changed
- Thumbnail loading reuses the existing bounded, visible-only artwork cache and background cover workers; opening a large deck does not initiate bulk artwork downloads.
- Existing card-type sections, quantity plus/minus controls and text-first editing workflow remain intact.
- List mode remains available for a denser interface without card images.

## Fixed
- Visual Mainboard previously offered only text rows despite having accessible local official card art.

## Removed
- No saved decks, collection, proxy records, AI reviews, credentials or sync data removed. Presentation preferences are independent of deck data.

## Verification
- Pre-release debug validation passed JavaScript regressions, Android unit tests and APK compilation.
- Signed release must independently pass regressions, unit tests, release compilation and APK signature checks.
- On-device: open Deck Editing, expand Visual Mainboard, toggle List and Art + Details, scroll to load thumbnails, open a card preview, edit a quantity, and confirm no save until Review & Save.
