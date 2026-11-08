# Arcane Vault for Android v2.0.25

## Added
- QR/USB import preview now reports how many physical card rows have confirmed printing identifiers.
- Import confirmations report unidentified rows and verified printings preserved.

## Changed
- Mixed and name-only Windows collection transfers preserve previously verified printing assignments where the card-name quantities agree exactly.
- Native printing ownership synchronization protects compatible verified printing entries.

## Fixed
- Prevented certain collection transfers from inadvertently clearing previously confirmed printing identities and reducing Set Progress to zero.
- Updated the Android regression suite to cover the new safety checks.

## Removed
- No user-facing features removed.

## Important
A card that has no Scryfall ID or set-and-collector identifier cannot be assigned to a specific set safely. Zero progress will persist for such cards until correct printing details are supplied. This release does not invent printing identities. Existing Android installations must use an APK signed with the same signing certificate. Back up your collections before changing installations.
