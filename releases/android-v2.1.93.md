# Arcane Vault Android v2.1.93

## Added
- Compact, expandable card artwork and inline mana-cost/color-identity display retained from the card-details polish pass.

## Changed
- Screen-reader descriptions preserve the full "MANA COST (EXACT)" and "COLOR IDENTITY" labels even when the visible compact label is shortened.

## Fixed
- v2.1.91 and v2.1.92 signed-release regression failures caused by shortening the mana-cost and color-identity labels.
- Preserves fully scrollable Oracle text in the AI-linked inspector from v2.1.90.

## Removed
- No collection, decks, saved analyses, authorization, or synced data removed.

## Verification
- The debug preflight passed JavaScript regression checks and Android unit tests after the label fixes.
- Signed-release workflow still must pass release compilation and APK signing verification.
