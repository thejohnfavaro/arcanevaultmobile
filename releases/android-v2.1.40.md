# Arcane Vault Android v2.1.40 — Physical Collection and Set Value History

## Added
- A physical collection value-history line chart on the Android Home and Physical Collection screens.
- A history chart for each owned physical set on the Set Progress details screen.
- Daily on-device snapshots from the existing printing-aware Scryfall quote valuations, retained for up to 730 days.
- Regression checks for offline, per-set history and accurate empty/one-point chart states.

## Changed
- Repricing or changing collection ownership replaces that day's snapshot rather than creating duplicate timeline entries.
- Set and collection charts draw only dated observations actually recorded after installing this version.
- The existing separate collection value and per-set lifetime baselines are retained unchanged.

## Fixed
- Collection and per-set value tracking previously exposed only a starting-value comparison without historical trends.

## Removed
- No existing collections, saved decks, printing identities, prices, settings or baseline records are removed.

## Safety
- History data is a separate, bounded local cache, not another ownership ledger.
- Missing/unpriced collections are not recorded as zero-dollar observations.
- No fabricated pre-installation price history or interpolated historical quote data is inserted.
- Price observations reuse the current cached Scryfall printing-aware USD estimates; cards without valid quotes are excluded, and collection additions or removals can affect changes.
- No new hidden network requests, price scraping or live merchant integration is introduced.
