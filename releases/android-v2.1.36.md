# Arcane Vault Android v2.1.36 — Restore Set Progress Visual Cards

## Added
- A regression check requiring the same visual set-progress card for both offline/cached set-directory listings and indexed completion snapshots.
- Persistent per-set representative artwork metadata, independently cached from ownership counts.

## Changed
- Set Progress's initial loading/offline path now renders the original designed set cards rather than substituting plain text rows.
- Once a progress snapshot is ready, the set cards continue to show verified owned and total counts, completion bar, set symbol, and artwork.
- Missing set-art files are warmed asynchronously without blocking browsing or invalidating collection progress snapshots.

## Fixed
- Set Progress incorrectly showed a stripped-down set-name list when the indexed ownership calculation was still pending.
- Cached Set Progress snapshots had lost their artwork URL, leaving set cards without background imagery after returning to the workspace.
- Set progress views now consistently use the same renderer as the homescreen set-progress widget.

## Removed
- The plain text set-directory fallback in the Set Progress UI.

## Safety
- Set-directory-only entries omit unverified owned/total values rather than claiming a fabricated 0% completion.
- Official set symbols and representative card artwork use the existing Scryfall-aware local cache.
- All set-specific cache invalidation and data-protection logic from v2.1.35 remains in place.
- No card collections, saved decks, prices, ownership identities, or synchronization state are changed by this visual fix.
