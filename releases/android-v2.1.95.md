# Arcane Vault Android v2.1.95

## Added
- Tap the commander or any mainboard card in saved Deck Details to open the full local card inspector.
- Return directly to the same deck position using the Back button or Android system Back.

## Changed
- Card previews reuse the exact verified local-card inspector already used by AI Review, with full Oracle text, expandable artwork, color identity, printings and other metadata.
- Deck Details views remain alive while a card is inspected, avoiding reloading deck ownership and catalog insights when returning.
- Saved decks, collections and ownership remain read-only during inspection.

## Fixed
- Deck Details previously showed card names and counts without a way to inspect cards from the deck.
- Navigating from an inspected card back to its deck no longer requires reconstructing the full deck screen.

## Removed
- No saved decks, drafts, collection, AI analyses, authorization, or sync data removed.

## Verification
- Pre-release debug checks passed JavaScript regressions, Android unit tests and debug APK compilation.
- Signed release workflow must pass all checks, APK compilation, and signature verification.
- On device: open saved Deck Details, scroll to a mainboard card, tap it, inspect full rules, and use Back to return to the same scroll position.
