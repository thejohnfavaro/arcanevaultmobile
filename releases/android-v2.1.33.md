# Arcane Vault Android v2.1.33 — Set Progress Recovery and Market References

## Added
- TCGplayer Market widget selection can use the existing dated Scryfall USD market-reference subtotal, clearly labeled as a Scryfall-backed estimate.
- Set Progress displays indexed set cards even if the optional hide-empty filter would otherwise conceal every set because exact printing ownership is not yet resolved.

## Changed
- Set Progress overview uses a bounded indexed per-set summary rather than a broad join over every owned printing.
- Artwork lookups are optional and limited to visible tiles; missing imagery must not prevent set progress cards from rendering.
- Collection Value widget selection labels distinguish Scryfall-backed reference valuations from unavailable direct retailer integrations.

## Fixed
- Set Progress could appear to have no sets after recent widget updates due to expensive ownership aggregation or hide-empty filtering.
- Choosing TCGplayer market showed a disconnected-source error despite an available Scryfall market reference.
- Missing local set catalogs now display a clear Data & Sync recovery message without changing ownership.

## Removed
- No saved decks, physical printing records, ownership, set selection, or collection files were removed.

## Safety
- The TCGplayer-labelled value is explicitly a Scryfall market reference, not a live direct TCGplayer API quote.
- Card Kingdom, Cardmarket and unimplemented Low/Retail price modes remain unavailable rather than supplying guessed prices.
- All collection valuation remains read-only and excludes unpriced printings from the priced subtotal.
