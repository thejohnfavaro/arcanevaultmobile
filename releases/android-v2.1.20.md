# Arcane Vault Android v2.1.20 — Mechanic Keyword Filters

## Added
- **Keyword / Rules Text** field in the native Android Card Catalog advanced filters.
- Filter by mechanics such as **Banding**, **Connive**, and **Replicate** and combine those terms with existing color, rarity, type, source, owned/unowned and set selections.
- The filter also applies when browsing exact owned physical printings.

## Changed
- Typed mechanic terms use bounded, parameterized, case-insensitive Scryfall Oracle text search.
- Main Card Catalog search continues matching name, type, and rules text.

## Fixed
- No separate keyword predicate existed for refining broad search results by mechanic synergies.
- Literal percent and underscore characters in the new keyword filter are handled as text rather than SQL wildcards.

## Removed
- No collection data, decks, card printings, saved filters or user preferences removed.

## Safety
- Search uses the existing on-device Scryfall cache. No network request or collection mutation is performed by the keyword filter.
