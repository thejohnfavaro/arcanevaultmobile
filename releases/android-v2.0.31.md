# Arcane Vault Android v2.0.31 — Native Card Catalog and inspector

## Added
- A new Card Catalog tab rendered entirely in Android native views, backed by the existing on-device Scryfall SQLite catalog.
- Card name, type, set and Oracle-text search, basic Arena versus non-Arena filters, ownership filter for indexed Arena holdings, and bounded 40-card pages.
- Native card-details dialog with Oracle text and official Scryfall art obtained through the existing guarded local artwork cache.

## Changed
- Native shell navigation now includes Catalog alongside Home, Physical, Arena, and Legacy tools.
- Native catalog searches execute on a background thread and ignore obsolete results.

## Fixed
- Card Catalog browsing and card inspection no longer require a WebView page in this native view.

## Removed
- Nothing yet. Full catalog filtering, special physical-owned printing selection, deck editing, sync and set progress remain in Legacy tools during phased migration.

## Safety
- This new screen is read-only. Native card quantities, printing identities, decks, backups and the Scryfall catalog are not rewritten.
- Artwork has a text-only fallback when offline or unavailable.
- CI plus on-device validation are required before distribution.
