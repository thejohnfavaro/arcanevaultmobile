# Arcane Vault Android v2.1.60

## Added
- Native Deck Analytics destination in the Decks section, with read-only W/L, win rates, rolling 10-match trends, recent results and weekly performance.
- Primary/secondary deck comparison, Arena format filtering and 30/90-day or lifetime filters.
- Optional Windows-to-Android match analytics import from a reviewed Windows peer file. Tap **Data & Sync → Preview peer file → Import analytics** to save an explicitly confirmed local read-only copy.
- Validated bounded analytics payload with a maximum of 2,000 confirmed outcomes and strict recorded-result formats.
- JVM regression coverage for parsing, selective import, UTC week boundaries, format filters and time range behavior.

## Changed
- Deck Analytics is driven solely by existing Windows confirmed match records embedded as optional presentation-only metadata in the peer envelope. The existing native collection, Arena inventory and deck records are unaffected.

## Fixed
- Android no longer needs to extrapolate W/L totals from unrelated inventory events; missing synced analytics displays a clear empty state.

## Removed
- No functionality removed.

**Important:** This is read-only analytics parity through an explicit peer-file preview, **not** automatic bidirectional match-history synchronization. Windows remains authoritative. Analytics metadata does not participate in the peer inventory merge or modify deck/collection ownership. A production-signed Android upgrade must use the existing signing certificate; a debug APK is not a drop-in replacement.
