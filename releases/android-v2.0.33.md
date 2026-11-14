# Arcane Vault Android v2.0.33 — Native UI readability and card artwork

## Added
- Actual card art thumbnails in the native on-device Card Catalog using our existing official Scryfall artwork cache.
- Shared Android-native Arcane Vault style primitives for dark surfaces, contrast, rounded controls and readable cards.
- Stronger full-card inspection with large artwork, a distinct Oracle-text panel, ownership information, and printing selection.

## Changed
- Native Home and Physical/Arena screens now use a consistent charcoal/slate look with teal and warm-gold accents rather than Android's default pale controls.
- Navigation scrolls horizontally instead of squeezing five buttons onto narrow phones.
- The native Catalog fills its available screen area; the hard-coded 540dp nested viewport is removed.
- Card rows show art, title, type, rarity and Arena-owned copies, and preserve tappable native details.
- Official artwork thumbnail decoding is moved off the UI and catalog-query thread.

## Fixed
- Native controls no longer inherit light Material styling that made the UI appear grey-on-white.
- Card lists are no longer text-only, and full-screen catalog browsing is no longer clipped by the original nested scroll layout.

## Removed
- No user features removed. Legacy tools remain available for screens not yet migrated.

## Safety
- No collection or printing-identity writes, schema changes, or data migration.
- Cards remain searchable and inspectable offline if artwork has not been cached; image failures cannot clear ownership data.
- Signed APK / same-certificate update path remains in place. Device validation is still required.
