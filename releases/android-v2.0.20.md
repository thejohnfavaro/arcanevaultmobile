# Arcane Vault Android v2.0.20 — Printing Catalog Indexing Progress

## Added
- Native in-memory Scryfall printing-index progress counters, including imported printing records, downloaded bytes, and time since the last successful activity.
- A visible warning if indexing shows no activity for three minutes, rather than implying endless progress.
- Regression checks for the nonblocking catalog status API, request timeout, indexing heartbeat, and progress display.

## Changed
- The printing-catalog status endpoint now reads cached counters rather than running SQLite COUNT queries while a bulk import owns a write transaction.
- Set Progress reports active indexing and automatically reloads when the import finishes instead of trying to query the partially written database.
- Catalog status requests are timeout-protected and shared between UI polling operations, preventing request pileups.
- Sync All Card Data displays actual download and import progress with a clear stalled-state message.

## Fixed
- Catalog status checks could block behind bulk SQLite writes, leaving Android on “Checking the local printing catalog…” or apparently stuck on “Indexing”.
- Failed catalog-status requests now show an actionable message instead of hanging indefinitely.
- Redundant Set Progress loading status output has been removed.

## Removed
- No features removed.

## Data safety
- The existing committed catalog remains untouched while Scryfall downloads into staging tables.
- No physical or Arena collection changes, database migrations, storage resets, or destructive recovery operations.
- If indexing truly stops, reopening the app and retrying the sync is safe; **do not uninstall or clear app data**.
