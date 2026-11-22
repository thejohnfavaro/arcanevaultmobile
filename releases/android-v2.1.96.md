# Arcane Vault Android v2.1.96

## Added
- Optional Visual mainboard inside native Deck Editing, showing unsaved card quantities and readable card names.
- Tap a draft card to open the complete local card inspector, then return to the exact unsaved editor state and scroll position.
- Quantity + and − controls that update the existing validated mainboard draft text, including removing the final copy of a card.
- Refresh visual mainboard option after manually editing or importing deck text.

## Changed
- Card inspector now supports Deck Details and Deck Editing as origins; Android system Back returns to the correct screen before any leave-draft confirmation.
- Visual draft editing uses NativeDeckDraft.parseEntries and formatEntries with the established quantity and size limits.
- Existing text-first edit and Review & Save workflow remains authoritative. Collection ownership and saved decks are unaffected until explicitly saved.

## Fixed
- Deck Editing previously required manual text editing even for simple quantity adjustments.
- Inspecting draft cards now avoids discarding unsaved name/mainboard edits or reconstructing the editor.

## Removed
- No physical or Arena collection, decks, saved AI reviews, tokens, printings, or sync records removed.

## Verification
- Before release, JavaScript regressions, Android unit tests, and debug APK compilation all passed.
- Signed release must pass all checks and APK signing verification.
- On device: open saved deck editing, expand Visual mainboard, use +/−, inspect a card and return, then verify Review & Save still requires confirmation.
