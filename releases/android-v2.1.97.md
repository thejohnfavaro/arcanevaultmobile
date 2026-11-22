# Arcane Vault Android v2.1.97

## Added
- Windows-style card-type sections in the Android Visual Mainboard: Creatures, Instants, Sorceries, Artifacts, Enchantments, Planeswalkers, Battles, Other / Unknown, and Lands.
- Per-section total card quantities, with the unsaved overall mainboard totals retained.
- Bounded local Scryfall type-line lookup for precise card grouping; unindexed cards remain visible as Other / Unknown.

## Changed
- The visual deck mainboard now sorts only its displayed rows by card category. Saved deck entry order, unsaved text, and exact card identities are unchanged.
- Artifact lands and creature lands appear under Lands. Hybrid card types follow a stable priority order.
- Type lookups happen on the background executor and cache for the current editor session, so grouping doesn't block the UI or change collection ownership.
- Existing preview, +/− editing, manual text entry, AI Review and Review & Save behavior are preserved.

## Fixed
- The visual draft mainboard formerly appeared as one long unsorted list with no card-role grouping.
- Unknown or unavailable metadata never causes a card to disappear from the draft view.

## Removed
- No saved deck, collection, proxy, printing, analysis, credentials or sync data removed.

## Verification
- Pre-release JavaScript regression checks, Android unit tests and debug APK compilation passed.
- Signed-release workflow must independently pass regression checks, unit tests, release compilation and signing.
- On device, expand Visual Mainboard, inspect categorized creature/spell/artifact/land rows, change a quantity, preview a card, and verify draft-only editing.
