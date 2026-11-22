# Arcane Vault Android v2.1.91

## Added
- Tap-to-expand and tap-to-collapse full-card artwork in the native card inspector, retaining high-quality card image loading.
- Compact, inline mana cost and color-identity glyph rows.

## Changed
- Native card inspector starts with a smaller artwork preview so Oracle text is reached sooner on phone displays.
- Back and title occupy less vertical space. Artwork can be expanded on demand.
- The same shared inspector presentation applies to Catalog, AI Review, and Set Progress card inspections.

## Fixed
- Excessive vertical scrolling before card rules due to a large fixed artwork block and stacked metadata headings.
- Full Oracle text still follows the natural-height scrolling fix from v2.1.90.

## Removed
- No saved decks, collection, analysis, authorization, cache, or sync data removed.

## Verification
- Signed Android release workflow must pass regression checks, unit tests, compilation, and APK signing.
- On a phone, open a card from AI Review and Catalog. Check full Oracle text, tap artwork to expand and collapse, inspect mana identity glyphs, and return to the originating view.
