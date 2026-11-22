# Arcane Vault Android v2.1.107

## Added
- Search-as-you-type commander autocomplete in the native saved-deck editor, backed by the locally synced commander catalog.
- Editable commander field in the Android deck editor, including transforming front-face commander suggestions.
- Win/loss matchup breakdown by opponent color identity in Deck Analytics.
- Safe optional opponent color metadata intake from a Windows peer analytics snapshot, with Unknown and Colorless separated.

## Changed
- Commander selections are staged as a deck edit and saved only after Review & Save.
- Matchup statistics respect the selected deck, format, and date filter; they count confirmed wins and losses only.

## Fixed
- A missing opponent color identity no longer risks being treated as colorless.
- Commander entry no longer requires memorizing a complete card name to find suggestions.

## Removed
- Nothing. Local ownership, stored decks, sync baselines, and recovery data remain preserved.

## Verification
- In Deck Library open a saved deck, type Etali into the commander field, select a suggested front face, and save explicitly.
- Import match analytics from a Windows peer and verify opponent color matchup breakdowns and Unknown fallback.
- Run unit tests including opponent identity matching for WUBRG and colorless C.
