# Arcane Vault Android v2.1.15 — Card Catalog & EDH Commander Browser

## Added
- **Mana value / CMC minimum and maximum** in native Card Catalog filters, combinable with rarity, utilization tags, set, card type, color identity, collection and owned physical printings.
- **Bottom-right flip control** on verified double-faced cards in Card Catalog detailed list, two-column Card Art, inline inspector and printing detail. Only actual paired Scryfall front/back images qualify.
- **Native EDH Lab commander browser** in the Android navigation, using offline Scryfall metadata and existing actual physical/Arena collection names. It offers a searchable commander list and an in-place selection preview, with right-side preview on tablet/wide layouts and a tap-to-preview/back layout on phones.
- Commander preview includes official artwork, verified alternate face when available, card types, mana value, color identity, full Oracle text and name-level ownership.
- A route to existing deck editing tools, copying the selected commander name to the clipboard rather than fabricating a new deck record.

## Changed
- Scryfall printing queries return face-matched reverse artwork, never guessing a back face by card name.
- Cached page prefetch and foreground thumbnails can download in parallel through striped URL locks. Cached images are written atomically and stay inside the existing bounded on-device official Scryfall cache.
- Rapid front/back toggling cannot be overwritten by stale image replies.
- Visible artwork is retained when its card view is created before attachment.

## Fixed
- Adding CMC/mana-value bounds no longer requires sacrificing rarity or card-role filters.
- Fully cached catalog pages and card browsing remain responsive while artwork prefetching progresses.

## Removed
- No existing inventory/deck data, snapshots, native sync or legacy deck editing pathways removed.

## Safety
- Native commander discovery and selection preview are currently read-only. Legacy deck editing stays available rather than creating incompatible persisted decks.
- Official Scryfall HTTPS artwork only; remote images have bounded file sizes and cache limits.
- Card Catalog changes do not rewrite ownership, printings, decks or synchronized data. Windows v0.9.103 contains corresponding browsing/filter/flip support.
- Automated Android regression, JUnit and debug build passed on the feature branch. Real device testing recommended.
