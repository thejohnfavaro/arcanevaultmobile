# Arcane Vault Android v2.1.16 — Visual commander browsing and update cleanup

## Added
- Commander candidate rows now include official Scryfall card-art thumbnails alongside commander name, type, color identity and owned quantities.
- The native Card Catalog and printing inspector omit the artwork area when a card has no valid official image instead of showing a blank or uncached-art widget.
- Obsolete verified installer downloads are cleaned up after an updated Android app starts successfully.

## Changed
- Update cleanup is scoped to the app-owned verified-updates temporary cache. Android itself continues managing APK installations; no attempt is made to erase installed application versions.
- In the EDH commander browser, real artwork is shown when available, while rows remain browsable offline without images.

## Safety
- Physical and Arena collections, decks, proxies, preferred artwork, full recovery snapshots, sync checkpoints and user files are untouched by cleanup.
- No new ownership or deck data is inferred by the native commander browser.
- Includes the existing first USB sync, combined mana-value filter, card-face flipping, card inspection, and cached page browsing.
