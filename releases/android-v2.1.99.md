# Arcane Vault Android v2.1.99

## Added
- A third Grid presentation for native Deck Editing Visual Mainboard alongside List and Art + Details.
- Two card-art columns on phones and three on larger screens, with quantity badges, card names, full card previews, and the same +/− draft controls.
- Saved Grid preference independent of collection, saved deck, and draft state.
- Recovery for draft artwork requests that failed during initial catalog indexing or intermittent download availability.

## Changed
- Grid and Art + Details share the existing bounded, cache-backed visible-only artwork loader, rather than launching all deck images at once.
- Draft thumbnails begin image loading when attached to the window, even without a scroll event.
- Failed draft art requests receive at most two immediate recovery attempts instead of being permanently marked unavailable.
- Double-faced cards named by their front face can resolve official artwork using the exact local Scryfall card metadata fallback.
- List and Art + Details remain selectable; grouped card ordering and saved draft text remain unaffected.

## Fixed
- Art + Details could remain stuck on a placeholder card when its first image request failed or metadata was still indexing.
- Grid and detailed art rows share the corrected artwork loading behavior.
- No speculative image is shown when no verified official art exists.

## Removed
- No collection, deck, draft, AI review, proxy, printing, authorization, or sync records removed.

## Verification
- JavaScript regressions, Android unit tests, and debug APK compilation passed before release.
- The signed workflow must independently pass checks, release compilation and signing.
- On-device: open Deck Editing → Visual mainboard; verify Art + Details shows official card thumbnails; switch to Grid, scroll through cards, inspect a card, and change an unsaved quantity.
