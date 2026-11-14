# Arcane Vault Android v2.1.21 — Multi-Mechanic Synergy Search

## Added
- Comma-separated Oracle mechanics in the native Card Catalog, e.g. `Connive, Discard`.
- **Match All** and **Match Any** mode to require all mechanics or allow any matching ability.
- Same mode applies to exact owned physical printing searches.

## Changed
- Parameterized Oracle-text predicates evaluate up to eight distinct phrases with literal case-insensitive text matching.
- Mode and terms are included in the catalog search cache key and work alongside existing filters.

## Fixed
- Comma-separated mechanics previously matched only as a single continuous literal substring.

## Removed
- No inventory, collection, decks, proxies, saved art or existing catalog filters removed.

## Safety
- Queries remain offline and read-only, with bounded phrase length/count and literal SQL escaping.
