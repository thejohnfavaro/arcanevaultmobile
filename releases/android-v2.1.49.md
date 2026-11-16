# Arcane Vault Android v2.1.49

## Added
- Regression assertions for the actual Android three-way Arena peer-conflict dialog and explicit Keep Android / Use Windows decisions.

## Changed
- Android wireless peer-apply regression tests now support the retained incoming snapshot used for conflict review.
- Keep the Android native UI cleanup, signed-update diagnostics, and safe per-card peer-merge choices from v2.1.45–v2.1.48.

## Fixed
- Android Signed Release job 133041204568, step 8 (Run regression checks), failed because `tests/native-wireless-apply.mjs` required the obsolete `JSONObject remote=incoming.load()` declaration and three-argument `prepareMerge` call.
- Replaced those brittle checks with the new retained-snapshot flow and four-argument conflict-aware merge checks.

## Removed
- None. No collection, saved decks, preferred artwork, sync baseline, or recovery snapshots are altered.
