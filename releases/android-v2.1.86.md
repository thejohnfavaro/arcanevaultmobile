# Arcane Vault Android v2.1.86

## Added
- Mobile-first labeled AI swap rows that retain every source table column, including Cut, Add, Priority, Why, and any supplemental notes.
- Regression tests covering multi-column explanations and card reference markers.

## Changed
- New AI review prompts explicitly request a rationale for each proposed cut-and-add swap.
- Markdown tables are displayed as readable vertical entries on phones rather than dropping columns.

## Fixed
- Android AI Deck Review previously hid the rationale when an AI response used four or more Markdown table columns.
- Saved AI reviews use the same updated formatting without changing their original stored Markdown.

## Removed
- No collection, decks, AI review history, authorization data, or sync records removed.

## Verification
- Signed Android release workflow must pass JavaScript regressions, Android unit tests (including swap-table tests), release compilation, signing, and APK verification.
- On-device, open an existing saved review and confirm that the reasoning is shown for its table rows. Card references should remain clickable after local verification.
