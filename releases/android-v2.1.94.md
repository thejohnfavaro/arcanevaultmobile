# Arcane Vault Android v2.1.94

## Added
- One-tap clear button in Deck Library search.
- Return-to-library scroll restoration when viewing a deck and going back.

## Changed
- Deck Library search waits briefly between keystrokes instead of rebuilding the list on every character.
- Existing filters, sorting, deck-box artwork caching, and ownership counts are retained.

## Fixed
- Returning from Deck Details unexpectedly jumped to the top of the Deck Library.
- Rapid searches caused repeated full deck-list rendering and card artwork request churn.
- Pending search redraws are canceled when navigating or closing the workspace.

## Removed
- No collection, saved deck, draft, AI analysis, ChatGPT authorization, or sync data removed.

## Verification
- Prior debug validation passed the full JavaScript regression suite, Android unit tests, and debug APK compilation.
- Signed release workflow must pass regression checks, tests, release compilation and APK signing.
- On-device: scroll down Deck Library, open and return from a deck, confirm position; search then clear; check sort and favorites.
