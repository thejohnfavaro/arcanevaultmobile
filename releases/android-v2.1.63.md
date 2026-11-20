# Arcane Vault Android v2.1.63

## Added
- Official Scryfall mana glyphs inside the native Card Catalog Oracle text inspector for white, blue, black, red, green, and colorless mana.

## Changed
- Existing official color-identity filters remain unchanged; the Oracle glyph renderer uses the same trusted symbol host and artwork cache.

## Fixed
- Oracle rules text no longer needs to show plain {W}, {U}, {B}, {R}, {G}, or {C} tokens when the corresponding official glyph is cached or can be loaded.

## Removed
- No functionality removed.

Note: Physical-to-proxy conversion is not included in this release. Android inventory edits must not silently mutate the Windows proxy ledger.
