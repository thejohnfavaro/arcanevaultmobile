# Arcane Vault Android v2.0.28 — Set Progress troubleshooting

## Added
- Collapsible in-app guide explaining why physical Set Progress may show 0% when owned cards lack confirmed printing IDs.
- Safe instructions for restoring verified printing IDs without increasing copy quantities.

## Changed
- Set Progress provides source-specific (Arena vs Physical) troubleshooting alongside its existing filtering controls.

## Fixed
- No underlying progress calculation change. Clarifies unresolved printing identity behavior rather than inflating totals through guesses.

## Removed
- No features removed.

## Safety
- Informational UI only, with no collection mutations, native storage changes, or network calls. Keep existing backups and prefer signed releases for in-place updates.
