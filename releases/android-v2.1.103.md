# Arcane Vault Android v2.1.103

## Added
- Collapse All and Expand All controls for populated card-type sections in the Android Visual Mainboard.
- Compact Deck Composition summary showing the count of cards in each populated type group, with the number of populated card types.
- Regression coverage for presentation-only bulk collapse controls and draft-derived composition counts.

## Changed
- Bulk actions reuse the existing individually verified section toggle handlers so state stays consistent across List, Art + Details, and Grid.
- Composition counts come directly from the currently unsaved draft, not ownership pools or the saved deck, and update on visual refresh or edits.
- Expanding all sections schedules the existing visible-only artwork loader; no changes to artwork cache, queue, fetching or retry behavior.

## Fixed
- Reviewing a large Commander deck required opening or closing every category separately.
- The visual editor now displays a concise breakdown of the deck by type without requiring manual counting.

## Removed
- No saved decks, collection records, owned printings, proxies, AI reviews, sync information, or existing deck view modes removed.

## Verification
- Before release, JavaScript regressions, Android unit tests, and debug APK compilation passed.
- Signed-release workflow must independently pass version and note validation, regressions, Android tests, release compilation, and signing.
- On device: Deck Editing -> Visual mainboard. Use Collapse All, Expand All, and check the composition counts. Switch List / Art + Details / Grid; verify section state and the working artwork loader. Change a quantity, refresh, and verify composition changes without saving until Review & Save.
