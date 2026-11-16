# Arcane Vault Android v2.1.45

## Added
- Per-card conflict resolution during Windows → Android peer synchronization for Arena inventory.
- Review dialog showing the last common baseline quantity, current Android quantity, and incoming Windows quantity.
- Explicit **Keep Android** and **Use Windows** choices; unrelated changes still go through the normal guarded merge review.
- Regression coverage for divergent Arena quantities, preservation of independent additions, and rejection of invalid decisions.

## Changed
- An individually reviewed Arena card conflict no longer blocks unrelated valid differences once the user chooses which existing quantity to retain.
- Any new, unresolved conflict continues to stop the merge. The full recovery backup and final apply confirmation are still required.
- Re-check the live Android collection during conflict resolution to prevent applying decisions based on an outdated local snapshot.

## Fixed
- Repeated `Conflicting edits: cards/<name>` errors now provide a safe resolution path rather than forcing users to reset or overwrite a collection.

## Removed
- None. No automatic summing of independent Arena quantity changes; no silent deletion, overwrite or auto-apply.
