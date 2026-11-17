# Arcane Vault Android v2.1.61

## Added
- Dedicated native Deck Analytics screen with W/L, win percentage, two-deck comparison, date and Arena format filters, weekly performance and rolling 10-match trends.
- Explicit **Data & Sync → Preview peer file → Import analytics** control for the optional Windows match summary.
- JVM regression coverage for confirmed match parsing, UTC week handling, comparisons and date filtering.
- Documentation for manually refreshed read-only analytics and unchanged inventory authority.

## Changed
- Windows-confirmed match history is imported only after a separate user confirmation. The copy is stored outside physical/Arena collection and deck inventories.
- Valid Windows peer snapshots without match data continue their normal inventory preview/merge workflow.

## Fixed
- Corrupt, oversized or unsupported optional analytics metadata is rejected **without blocking an otherwise valid inventory peer comparison**.
- Analytics excludes unassigned/unknown results and only displays confirmed outcomes from the Windows source.

## Removed
- No features removed.

> This Android release contains a read-only analytics snapshot, not automatic differential match-history replication. Windows owns Arena match results. The existing peer `format`, `schema`, `revision` and authoritative inventory `data` fields remain unchanged. Preserve existing local data and use only a production-signed update from the same signing certificate.
