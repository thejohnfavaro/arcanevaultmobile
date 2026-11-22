# Arcane Vault Android v2.1.105

## Added
- On-demand, automatically triggered mana-cost metadata recovery when Deck Editing → Deck Insights sees known cards with zero cached casting costs (common after upgrading a catalog created before Android schema v4).
- Small, rate-limited official Scryfall card collection batches, maximum 75 exact names per request, to fill missing local card mana-cost fields without requiring a complete catalog redownload.
- The existing Refresh Insights button can retry metadata recovery if the first attempt was offline or catalog sync was running.
- Regression coverage for exact-name verification, request bounds, catalog-only writes, and missing-data presentation.

## Changed
- Colored casting-cost demand now distinguishes absent mana-cost metadata from genuinely zero colored pips; when all known nonland costs are missing, it reports unavailable instead of all-zero values.
- Background deck insights recomputes immediately after a successful metadata recovery, and still rejects stale UI results if the unsaved draft changed.
- Scryfall responses must exactly match requested canonical names before updating the local cards.mana column.
- Mana values, card-type analysis, spell color identity, land-drop odds and the existing art, collapse and grid behavior remain unchanged.

## Fixed
- Existing Android installations upgraded from an older catalog database could show "0 nonland copies with costs / 59 without cost metadata", causing the casting-cost chart to be blank despite cards being otherwise indexed.
- Automatic small-scope repair no longer requires resetting or replacing the user's local card catalog.

## Removed
- No saved decks, confirmed inventory, card printings, proxies, snapshots, sync data, AI reviews, or existing editor layouts are removed or changed. This update modifies only missing local Scryfall mana metadata for validated exact card names.

## Verification
- JavaScript regressions, Android unit tests and debug APK compilation passed before initiating signed release.
- Signed workflow must independently validate notes, regression suite, tests, APK compilation and signature.
- On-device: open Borg → Edit → Deck Insights while connected to the internet. A legacy catalog with missing mana costs automatically queries Scryfall only for that deck's missing costs. The colored casting-cost demand numbers should populate. If offline or the catalog is syncing, select Refresh Insights later. Confirm your saved deck, collection and existing views are unchanged.
