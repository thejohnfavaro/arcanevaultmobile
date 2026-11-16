# Arcane Vault Android v2.1.46

## Added
- Visible peer-sync handler identifier in the rejection dialog to distinguish installed binary behavior from a newer GitHub source commit.
- Detailed, safe diagnostics when an Arena conflict was detected but the conflicting record quantities cannot be validated.

## Changed
- Conflicts like `Conflicting edits: cards/andromedanautomatons` continue through the v2.1.45 explicit Keep Android / Use Windows card-level resolution path when their three-way quantities are verifiable.
- Other errors identify the exact sync handler so outdated APKs can be diagnosed without guessing.

## Fixed
- A conflict-detail failure no longer hides its root cause behind a generic error with no indication of which handler was used.

## Removed
- None. Existing baseline validation, recovery backup, final confirmation and conflict refusal for ambiguous edits remain intact.
