# Arcane Vault Android v2.1.1 — PC / Android peer sync preview

## Added
- **Export peer sync file** and **Preview Windows / Android peer file** in native Data & Sync.
- Export native physical and Arena inventories, saved decks and preferred artwork from their existing authoritative stores in one portable peer snapshot.
- Stable Android peer device ID and a cross-platform schema-1 envelope containing schema-2 collection and deck data.
- **Three-way compare (known shared baseline)** for distinguishing incoming-only changes, local-only changes and concurrent conflicts using an explicitly selected prior common snapshot.

## Changed
- Normalize JSON object ordering during comparisons to avoid false differences between Windows and Android serializers.
- Use unambiguous length-prefixed physical-printing identity keys.
- Read user-selected peer files through the Android document picker, with a 30 MiB size limit.

## Fixed
- Reject duplicate or missing record identities instead of guessing which printings or decks match.
- Render comparison summaries with readable line breaks and clear counts.
- Never infer that a missing peer record was intentionally deleted.

## Removed
- No existing features removed. Legacy QR/USB collection transfer and backup features remain available.

## Safety
- **This version supports export and read-only reconciliation previews, not automatic bidirectional synchronization.**
- Incoming changes are not applied. Authenticated pairing, saved common-base checkpoints, explicit conflict approval and atomic write/recovery on both platforms are still required.
- Google Play Protect report #36 remains open. Signing and successful CI do not guarantee Play Protect clearance.
