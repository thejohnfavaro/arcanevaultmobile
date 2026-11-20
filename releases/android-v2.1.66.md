# Arcane Vault Android v2.1.66

## Added
- Regression coverage for Arena-annotated Brawl card names and commander printing suffixes.
- A modern adaptive Android launcher icon using the approved Arcane Vault artwork.

## Changed
- Deck-to-inventory and deck-to-catalog comparisons normalize standard Arena printing annotations (e.g. `Island (TMT) 316` → `Island`) without editing saved deck entries.
- Android launcher icon now uses an adaptive icon resource rather than only a legacy drawable.

## Fixed
- False "not covered by Arena inventory" results for already-owned saved deck cards with Arena printing suffixes.
- Deck insights falsely showing most cards as unknown, incorrect mana curve, and incorrect card-type counts when the local catalog uses the underlying card name.
- Android owned-copy detail rows using full Arena printing suffixes when checking physical or Arena holdings.

## Removed
- No features or stored records removed.

Notes: This patch does not modify deck names, selected printing details, Arena inventory, physical inventory, or sync data. If cards are genuinely absent from the local catalog after this change, the app will continue to report them as unknown rather than fabricating results.
