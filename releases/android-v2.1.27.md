# Arcane Vault Android v2.1.27 — Sharp Artwork & Illustrated Shortlist

## Added
- Full Scryfall card-art tiles in native My Shortlist, with responsive two-column phone layouts and three/four columns on larger screens.
- Tap any Shortlist card to open a high-resolution full-card preview, with the existing ownership summary and an option to remove its bookmark.
- Search-aware pages of up to 48 Shortlist card tiles, with previous/next navigation.

## Changed
- Android's Card Catalog, card inspectors, printings, EDH commander results, and commander deck-box covers use adaptive pixel-resolution bitmap decoding instead of fixed quarter-resolution thumbnails.
- Full-card previews prefer Scryfall's official large JPEG where available. Cached normal-size art remains a fallback, including offline.
- Thumbnails are decoded at the resolution required by the actual display, rather than being enlarged from 122-pixel-wide bitmaps.
- Shortlist artwork loads only for visible cards, via the guarded Scryfall artwork cache and bounded two-worker queue.

## Fixed
- Cards appeared blurry on high-density Android screens because normal artwork was always downsampled by 4 before rendering.
- Native My Shortlist only showed text labels instead of artwork, unlike the card catalog.

## Removed
- No bookmarks, cards, decks, printings, or collection ownership changed.

## Safety
- All image URLs remain restricted to official Scryfall hosts via the existing cache allowlist.
- The larger image preference uses only the official normal/large Scryfall path variants, never arbitrary external URLs.
- No new persistent card or bookmark store is introduced, and existing sync and update contracts are untouched.
