# Arcane Vault Android v2.1.89

## Added
- One-tap clear control in Card Catalog search when text is entered.
- Page-of-total indicators for card search results and clearer no-results guidance.

## Changed
- Card Catalog searches now wait briefly between keystrokes before querying, while invalidating stale results immediately.
- Existing source, ownership, color identity, card view, and cards-per-page preferences remain intact.

## Fixed
- Redundant catalog queries on each keystroke and confusing empty-filter result guidance.
- Better visibility of catalog pagination without losing filtered search context.

## Removed
- No collection, deck, saved AI review, authorization, or sync records removed.

## Verification
- Signed Android release workflow must pass JavaScript regressions, Android unit tests, build, and signing verification.
- On-device: enter a card name and tap clear, paginate search results, switch Art/List/Text views, and confirm ownership remains unchanged.
