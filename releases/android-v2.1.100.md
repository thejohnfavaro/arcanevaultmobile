# Arcane Vault Android v2.1.100

## Added
- Regression coverage for exact canonical card artwork lookups and immediate visible thumbnail repaint.

## Fixed
- Art + Details and Grid thumbnails now resolve saved Arena-style names such as "Esper Sentinel (MH2) 12" to their exact card identity, "Esper Sentinel", for the artwork lookup. The saved deck text, desired printing notes, and card quantities remain untouched.
- Newly downloaded artwork is assigned directly to live ImageViews instead of relying on a later scroll or layout event to replace the placeholder.
- The draft thumbnail loader retains bounded retries and the exact-name double-faced card fallback from v2.1.99.

## Changed
- Artwork resolution uses the existing NativeArenaCardName.identity helper for the local Scryfall lookup; it never guesses a different card from fuzzy name matches.
- Deck Library, Visual Mainboard List, Art + Details, Grid, card previews, and draft +/- editing remain available.

## Removed
- No saved deck, physical/Arena ownership, collection, printings, proxies, AI reviews, or sync data removed.

## Verification
- The JavaScript regression suite, Android unit tests, and debug APK compilation passed before triggering the signed release.
- Signed-release workflow must independently pass release compilation and signature checks.
- On-device: open Borg -> Edit -> Visual mainboard -> Art + Details; verify Esper Sentinel and other cards with set annotations show genuine artwork; switch to Grid and check imagery there, then confirm edit previews and draft-only quantity controls still work.
