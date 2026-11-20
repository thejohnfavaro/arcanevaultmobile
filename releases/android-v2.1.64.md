# Arcane Vault Android v2.1.64

## Added
- Official MTG tap {T}, untap {Q}, energy {E}, snow {S}, generic and variable mana, hybrid, Phyrexian, and hybrid Phyrexian glyphs in native Card Catalog Oracle text.

## Changed
- Use trusted Scryfall card-symbol SVGs for supported Oracle tokens, including symbols with slash notation; unsupported tokens remain readable.
- Reuse cached official glyphs and keep fetching off the UI thread.

## Fixed
- Oracle text displaying bare tap and mana cost tokens instead of card symbols.

## Removed
- No features removed.

Physical-to-proxy ownership conversion remains separate work and is not introduced in this release.
