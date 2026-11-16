# Arcane Vault Android v2.1.55

## Added
- Optional **Verify & remember baseline** action after a verified peer merge. It uses the existing full-snapshot identical-state check and stores a checkpoint only when Android exactly matches the received Windows snapshot; Windows must still confirm its own checkpoint.
- Regression coverage for compact baseline selection, successful native workspace refresh, accurate line breaks in the review, and guarded post-sync checkpoint verification.

## Changed
- Refresh the visible native collection/workspace after verified peer apply instead of refreshing only the hidden legacy WebView origin.
- Refresh collection value presentation alongside the native workspace following a successful peer apply or restore.
- Use a compact list-only picker for saved baseline candidates so snapshot options remain visible on narrow Android screens.
- Remove the redundant second confirmation after explicitly choosing the suggested saved baseline; the original warning and final merge approval remain.
- Use real newlines in the final peer inventory/deck/artwork change summary instead of printing literal backslash-n characters.

## Fixed
- Native Android screens could show stale owned cards and deck/set progress immediately after a successful peer merge.
- Oversized baseline picker dialog could obscure choices; the review message displayed escaped line breaks.
- Successful merges required an additional separate navigation to verify and remember a matching checkpoint.

## Removed
- No inventory, saved decks, printing IDs, artwork, peer baselines or recovery backups removed. No unattended peer changes or inferred common baseline permitted.
