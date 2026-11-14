# Arcane Vault Android v2.0.35 — Owned Physical Printings

## Added
- **Show owned physical printings** mode in the native Card Catalog. It is available when Physical is selected and lists individually identified Scryfall editions, not only owned card names.
- Distinct edition cards with official artwork, set name/code, collector number, rarity, and confirmed physical copy count.
- Search, paging and inspection of confirmed physical editions, retaining the existing native edition details.
- Regression checks to ensure unknown IDs, name-only holdings, and ambiguous set/collector entries cannot become falsely confirmed owned printings.

## Changed
- Physical/Owned continues to show name-level ownership, including name-only holdings; the new explicit printing mode shows confirmed exact editions only.
- Switching away from Physical or changing general ownership mode exits edition-only mode so filters cannot silently leak across collections.
- The edition list reads the existing SQLite physical printing mirror and indexed official Scryfall variants without rewriting inventory.

## Fixed
- Owning a card name is no longer shown as owning every printing of that card in the native owned-printing view.

## Removed
- No features removed. Legacy tools remain available.

## Safety
- Read-only search. No collection, printing, proxy, preferred image, or backup mutations.
- Local signed release intended for opt-in testing. Google Play Protect report #36 remains under investigation; app signing and CI checks are not a Play Protect malware clearance.
