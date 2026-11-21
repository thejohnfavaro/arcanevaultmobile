# Arcane Vault Android v2.1.73

## Added
- In AI Review, tap a highlighted, catalog-verified card name to open the existing full native card inspector with card art, Oracle rules text, format legalities, mana symbols, printing information and ownership.
- Link detection covers cards from the current deck and valid card names in AI recommendation highlights and swap tables.
- A return path back to AI Review keeps the analysis response and current unsaved deck draft intact.
- Local regression tests for card-name extraction and overlapping name handling.

## Changed
- Verified card names in AI results are displayed as gold, underlined interactive links instead of ordinary text.
- AI card inspections reuse the existing local Catalog inspector, including its full details, printings and accessibility affordances.
- Android Back follows the inspector's navigation hierarchy before returning to the previous AI review.

## Fixed
- AI recommendations previously required leaving the review and searching Card Catalog manually to inspect a proposed card.
- Prevents unverified or invented AI card names from opening misleading details; only exact local catalog matches are interactive.

## Removed
- Nothing. Collection, decks, selected printings, proxies, sync baselines, backups, and existing AI review content are preserved.

## Verification
- Android debug compilation, existing regression checks and the newly added candidate-name test passed in PR #93.
- The Android signed release workflow verifies the production APK; full device interaction and callback navigation still require hands-on testing.
- No automatic deck changes and no extra AI uploads for card previews. Catalog lookup stays local.

Build target: Android application version 2.1.73 (versionCode 129); no user data migration.
