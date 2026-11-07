# Arcane Vault Android v2.0.5

## Added
- **Hide sets with no progress** checkbox in Set Progress; remembers the setting per device and works for Arena and physical collections.
- One **Sync All Card Data** button replaces four separate Scryfall maintenance buttons.
- Two-stage progress: synchronize the full printing catalog, then refresh owned-card metadata and Scryfall format legalities.
- Set Progress now displays catalog indexing/readiness diagnostics instead of an empty screen when printings are unavailable.
- Set Progress automatically reloads when an in-progress printing catalog sync finishes.

## Changed
- The catalog status API reports actual indexed printing counts, separate from owned-card metadata.
- Existing import, backup, QR and USB syncing remain independent of card database maintenance.

## Fixed
- Set Progress previously appeared blank after card details were synced, even when its required printing index was absent or still indexing.
- Unified synchronization waits for the background catalog indexing process before refreshing card details and reporting completion.

## Removed
- The four redundant visible maintenance buttons (individual card details, catalog + variants, official card database, and format legality refresh).
