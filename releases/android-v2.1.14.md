# Arcane Vault Android v2.1.14 — In-workspace Card Inspector

## Added
- Native Android in-workspace card inspector opened from the Card Catalog without a full-screen modal.
- Inline official printings browser and edition detail with verified printing identity, current physical ownership and direct access to existing physical copy editor.
- Existing preferred artwork selection directly from a printing detail.
- Bounded three-page catalog search result cache keyed by all active filters.
- Background prefetch of each 40-card page's Scryfall artwork into the existing disk-limited artwork cache.

## Changed
- Returning from card inspection restores the existing Card Catalog query, filters, result page and scroll position.
- Next and Previous pagination return to the top of the catalog page.
- Printings lists reveal 32 additional records at a time without requiring a dialog chain.
- Card information and official artwork remain available in the same native browsing workspace.

## Fixed
- Card inspection no longer requires opening and closing a separate modal window.
- Revisiting a cached page avoids repeating an identical SQLite search until the bounded cache is evicted.
- Physical ownership edits invalidate the cached catalog records before refreshing results.

## Removed
- No collection records, editing capabilities, or synchronization features removed.

## Safety
- The catalog and preview stay read-only until existing explicit edit actions.
- Confirmed physical printings remain verified by exact Scryfall ID or unique set and collector number.
- Existing collection journals, recovery snapshots, and synchronization remain unchanged.
- Automated regression and Android APK build checks passed on the feature branch; device-level visual and interaction testing remains recommended.
