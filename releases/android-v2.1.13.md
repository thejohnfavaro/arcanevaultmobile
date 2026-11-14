# Arcane Vault Android v2.1.13 — Card Catalog browsing parity

## Added
- Collapsible **Filters & Sort** controls in the native Android Card Catalog.
- Exact set-code, card type, rarity, and utilization role filtering.
- Toggleable exact WUBRG color identity combinations and colorless-only filter.
- Sorting by name, owned quantity, mana value, or rarity.
- Two-column Card Art browsing alongside the existing detailed card list, with card inspection from either view.
- Reset advanced filters without disturbing source or ownership selection.
- Advanced filters operate on confirmed owned physical printings as well as the main catalog.

## Changed
- Advanced filter controls scroll with the catalog results on narrow Android displays.
- Set-code searches use indexed official printing identities instead of the representative card set code.
- Existing physical/Arena collection scope, ownership filters, printing inspection, and artwork caching continue to be used.

## Fixed
- Native Card Catalog no longer hides existing on-device search/sort capabilities behind legacy tools.
- Confirmed owned-printing browsing now applies set, rarity, type, role, color identity, and sorting filters.

## Removed
- No existing filters, collection data, or sync features removed.

## Safety
- Catalog browsing remains read-only until an explicit existing edit action.
- No collection data migration or changes to persisted ownership or peer synchronization.
- Automated Android regression and APK builds passed; device layout review remains recommended.
