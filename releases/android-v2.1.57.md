# Arcane Vault Android v2.1.57

## Added
- **Set Progress printing preview:** Tapping a set card displays a read-only, official Scryfall-artwork-backed preview with exact printing ID, collector number, and owned physical quantity or Arena name-based quantity.
- **Combined merge authority:** The peer-conflict menu can choose Windows or Android for all differing shared **Arena cards and saved decks together**, with the usual separately protected physical printings and artwork.
- **Remembered-peer overview:** Data & Sync displays previously verified matching peer checkpoints and when the latest checkpoint was stored. This is not authentication, unattended pairing, or permission to write.
- **Native storage migration staging:** A locally persisted, checksum-verified mirror of successfully read saved decks and preferred artwork, without replacing existing authoritative WebView storage or risking existing edits.
- **Differential protocol foundation:** A read-only per-record SHA-256 manifest of added, modified, and missing records with base/target revisions and dedicated unit coverage. Full-snapshot transport remains in use until both Windows and Android negotiate/verify a common delta protocol.
- Regression checks for all new user-facing Set Progress and mobile sync behavior.

## Changed
- Set details now use the available vertical space: hide overview-only collection toggle, sorting, hide-empty preference, and set search when browsing cards, then restore those controls on returning to sets.
- Art previews load only when opened, use the existing bounded official artwork cache, and do not alter collection quantities.
- Bulk conflict choices stay opt-in and precede the final verified full backup and save approval.
- The native deck/artwork mirror never silently substitutes stale data after a failed authoritative WebView read.

## Fixed
- Set Progress details previously kept overview controls on-screen, crowding the card list and making the screen look layered.
- Tapping a set card opened a bare metadata dialog with no card artwork.
- Routine sync could require separate Arena and deck authority selections even when users wished to apply one consistent choice to both domains.

## Removed
- No inventories, decks, artwork, printing identifiers, recovery backups, peer baselines, or existing authoritative storage removed.
- No automatic deletions, unchecked merges, or unattended differential data application introduced.
