# Arcane Vault Android v2.1.24 — Physical Owned Catalog & Official Mana Filters

## Added
- Official Scryfall mana-symbol SVGs as native color identity filter buttons in Card Catalog and EDH Lab commander discovery.
- Readable {W}, {U}, {B}, {R}, {G} and {C} fallback tokens whenever an official SVG is unavailable offline.

## Changed
- Android Card Catalog opens with **Physical** collection and **Owned** ownership selected.
- All Cards, Arena, Missing, and exact owned-printing searches remain available through the existing controls.
- Mana symbols use the same color-toggle semantics as Windows; multiple selected colors combine in WUBRG order, and colorless remains exclusive.

## Fixed
- Android previously used bare W/U/B/R/G/C letters while Windows showed official mana artwork.
- The default catalog query previously showed both collections and all unowned catalog entries rather than starting in the physical collection.

## Removed
- No collection data, deck data, proxies, printing metadata or existing filter modes were removed.

## Safety
- Read-only search defaults do not change stored physical or Arena ownership.
- Symbols load only from six fixed official Scryfall CDN URLs through the guarded art cache.
- No user-controlled card text can become an SVG URL or SVG source.
