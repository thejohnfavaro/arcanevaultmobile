# Arcane Vault Android v2.1.41 — Collection Analytics, Deck Readiness and Safe Sync Review

## Added
- Collection Intelligence on Home and Physical Collection: pricing coverage, top owned physical printing values, highest-value owned sets, and identified unpriced printing rows.
- Bounded native valuation rankings from the existing authoritative physical inventory and exact Scryfall price cache; a changed quote batch updates the insights without a separate price request.
- An asynchronous read-only data-health audit showing physical/Arena row and copy totals, missing exact printing identifiers, invalid quantities, and possible duplicate physical inventory records.
- A "Find decks closest to buildable" action in the native deck library, listing saved decks by missing owned copies with prioritized missing cards.
- Deck details now show accurate Physical-versus-Arena inventory coverage, including commander counts when appropriate, without mixing ownership pools.
- More useful peer-sync dry-run summaries: per-group additions/changes/peer absences and copy quantity differences, all strictly preview-only.
- Regression checks for analytics source integrity, deck pool separation, bounded reads and UI-thread-safe audits, and read-only peer previews.

## Changed
- Near-buildable deck ranking computes the physical and Arena owned-name maps once per review, instead of scanning large inventories repeatedly while sorting decks.
- The integrity scan runs off the UI thread and is canceled/ignored after navigation or shutdown.
- Existing per-set price snapshots, Set Progress filters, official artwork, collection history and the two Android home-screen widgets are preserved.

## Fixed
- Native saved-deck inspection previously combined physical and Arena card counts, making decks look more buildable than they really were.
- Peer sync preview previously lacked a clear categorized view and quantity differences.

## Removed
- No inventory, saved deck, proxy, historical price cache, sync checkpoint, printing identity, or recovery backup is removed.

## Safety
- Analytics report only cached, verified Scryfall estimates; an unpriced printing is explicitly unpriced, not assigned a value of zero.
- Deck recommendations are owned-copy coverage, not assertions of format legality or card-for-card strategic substitutions.
- Sync comparison neither applies changes nor relaxes existing approved three-way reconciliation and conflict checks.
- No new background scraping or automatic app-to-app overwrites have been introduced.
- Overall price change still includes changes in owned collection composition; a true market-only movers table requires separate historical per-printing quotes before claims can be made.
