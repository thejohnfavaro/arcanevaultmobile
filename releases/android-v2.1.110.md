# Arcane Vault Android v2.1.110

## Added
- **Arena Standard** as a saved-deck format in the native Android editor; selecting it removes the separate commander zone.
- Arena Standard saves require at least 60 mainboard cards, retaining the existing recovery-backed deck revision workflow.
- Arena Standard deck tiles use representative mainboard card artwork and display the format rather than a nonexistent commander.

## Changed
- Arena Standard deck coverage defaults to independently recorded Arena inventory; the physical collection is not modified.
- Format, selected artwork, card quantities and deck identity travel in the existing peer-sync deck object.
- The release supersedes the unsuccessful v2.1.109 signed build. Android debug compilation has passed after correcting picker bindings and test expectations.

## Fixed
- Format picker adapters now use their own local choice lists without shadowing folder filters or printing choices.
- Missing artwork placeholders for noncommander Arena decks.

## Removed
- Nothing. Existing owned inventory, decks, revisions and Android data remain preserved.

## Verification
- Sync a Windows Arena Standard deck to Android, edit it, and save a revised 60+ card mainboard.
- Confirm the deck remains Arena Standard without a commander, and review its Arena collection coverage and artwork.
- Verify Commander and Arena Brawl decks retain their existing commander printing choices and artwork.
