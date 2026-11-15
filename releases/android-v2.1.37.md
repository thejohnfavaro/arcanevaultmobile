# Arcane Vault Android v2.1.37 — Set Progress Artwork and Icons

## Added
- Independent set-symbol prefetch and artwork fallback handling.

## Fixed
- Set Progress cards could show the correct visual shell but no background art or official set symbols.
- Set icons now fetch independently and before the representative artwork, so a slow card-image download cannot block the set symbol.
- Set artwork selection prioritizes eligible physical/Arena printings but falls back to other indexed Scryfall artwork rather than leaving entire sets without a cover.
- If the cropped art cannot be fetched, the original Scryfall normal card image is fetched as a fallback.
- Missing artwork downloads now report diagnostic warnings rather than failing silently.
- Hide sets with no progress now respects the actual verified-ownership filter instead of automatically showing unowned sets when there are no matching results.
- First-load directory fallback does not claim unverified ownership while hide-empty is selected.

## Changed
- Set artwork uses dedicated bounded workers separate from deck cover downloads.
- A set icon can appear on its card even while its background artwork is still loading.
- A visible tile redraws when its set icon or art finishes caching; progress and ownership remain read-only and cached independently.

## Removed
- No collection records, card printings, or deck data were removed.

## Safety
- Previously saved collection and set-progress snapshots are left intact.
- The app only requests official Scryfall artwork and does not alter owned card counts.

## Unchanged
- No card, collection, deck, printing identity, or price data is modified by artwork loading.
- Set-specific cache invalidation and verified progress calculation from v2.1.35 remain intact.
