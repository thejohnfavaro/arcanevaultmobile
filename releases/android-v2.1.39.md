# Arcane Vault Android v2.1.39 — Owned Set Value and Lifetime Change

## Added
- Each Physical Set Progress card displays the estimated value of owned, priced printings belonging to that individual set, without counting copies from other sets.
- Cards display a compact, color-coded per-set lifetime percentage change from that set's first valid priced snapshot. An increase appears green, a decrease red, and unchanged values neutral.
- Opening a physical set shows its total owned estimated value, absolute and percentage lifetime difference, and priced/total physical-printing coverage.
- Android Set Progress homescreen widgets show the same scoped value and lifetime percentage alongside completion.
- A dedicated per-set baseline is persisted independently of the overall collection-value baseline.

## Changed
- Set values use the existing Scryfall exact-printing price cache, including the price selected for foil and etched printings, multiplied by verified owned quantities.
- Physical inventory and quote changes update set-value snapshots without recalculating Set Progress ownership or restarting the catalog sync.
- Values display in the existing in-app currency selection using cached reference FX rates. There is no separate direct retailer API.
- Existing visual set cards, official set artwork and symbols, completion indicators, and progress snapshots remain unchanged.

## Fixed
- Pricing previously existed only for the entire collection and its value widget; owned physical sets can now show their own subtotals.
- Stale set-value overlays are removed if quote coverage or physical/Arena scope changes.

## Removed
- No cards, saved decks, ownership records, price history, or set progress data are removed.

## Safety
- Only valid, positive, cached exact-printing Scryfall quote values count. Unpriced owned cards are reported in price coverage rather than silently assumed to be worth $0.
- Per-set lifetime change starts at the first valid priced subtotal after upgrading, not an inferred historical price. Inventory additions/removals can affect the net change, so the result is a change in set collection value rather than market-only investment performance.
- Values are scoped to physical printing set codes in the inventory. Unidentified sets or editions are not guessed from card name.
- No additional quote scraping, background network requests, proxy valuation, or secondary ownership ledger is introduced.
- Android release signing and checksum validation must pass before the APK is published.
