# Arcane Vault Android v2.1.18 — Integrated Scrollbars

## Added
- Theme-consistent, rounded native scrollbar thumbs with subtle fade behavior on supported Android versions.
- Shared scroll styling across Card Catalog, EDH commander browser, Deck Library, Set Progress, Data & Sync, and native companion views.

## Changed
- Android scroll indicators blend into Arcane Vault's existing dark/teal surfaces while preserving built-in touch scrolling and accessibility.
- Older Android versions retain their native scrollbars instead of relying on unsupported APIs.

## Fixed
- Inconsistent native scrollbar chrome between Android workspaces.
- Scroll styling no longer needs separate per-screen implementation.

## Removed
- No collection, browsing, deck-editing, or synchronization features removed.

## Safety
- Pure presentation changes. No collection, saved deck, proxy, artwork cache, snapshot or sync mutation.
- Android debug build and scrollbar theme tests passed in PR #76. On-device gesture and appearance testing is recommended.
