# Arcane Vault Android v2.1.92

## Added
- A compact card inspector with tap-to-expand artwork and inline mana-cost and color-identity glyphs, retained from v2.1.91.

## Changed
- Keep the short visible mana label while exposing its full precise meaning, "MANA COST (EXACT)", to Android accessibility services.

## Fixed
- The v2.1.91 release regression check that required the exact mana-cost semantics failed when the compact UI changed the label.
- This update retains the full Oracle text, AI card-inspector scrolling, and existing card data.

## Removed
- No saved decks, collection, AI analyses, authorization, or sync data removed.

## Verification
- Signed Android release workflow must pass JavaScript regression checks, Android unit tests, release compilation, and APK signature verification.
- On device, check compact artwork expands and collapses, mana symbols display correctly, and all Oracle text remains scrollable.
