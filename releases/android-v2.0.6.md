# Arcane Vault Android v2.0.6

## Added
- Durable native Android inventory storage for Arena and physical collections, independent of the embedded WebView's localhost origin.
- Automatic recovery scan for collections and preferences left in older random-port WebView storage locations.
- Collection Data Protection status in Import & Data, including the number of older storage locations checked and conflicting snapshots retained.
- Regression checks for stable app origin, persistent native collection writes, migration, and verified cleanup.

## Changed
- The app serves its interface from a stable localhost port; if that port is occupied, it uses a temporary port while keeping inventories in native storage.
- Arena and physical collection writes are committed synchronously to app-private native storage instead of browser localStorage.
- Old browser collections and preferences are migrated before initializing the interface.
- Redundant browser-origin storage is removed only when restored data has been verified. Conflicting older versions are retained for safety, never merged by adding quantities.

## Fixed
- Android app upgrades or relaunches could appear to erase collections because the random local port changed and WebView localStorage is origin-scoped.
- Removed duplicate browser copies after confirming successful persistence in the native collection store.

## Removed
- Dependence on per-port browser localStorage as the authoritative inventory database.
