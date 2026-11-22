# Arcane Vault Android v2.1.101

## Added
- Tap-to-collapse and tap-to-expand headings for each populated card-type section in the Android Visual Mainboard.
- A horizontally scrolling, app-themed quick-jump strip to reach Creatures, Instants, Sorceries, Artifacts, Enchantments, Planeswalkers, Battles, Other / Unknown, and Lands without traversing every card.
- Quick-jump automatically expands the target card type before scrolling to its heading.
- Accessibility labels announce section card counts and expand/collapse actions.

## Changed
- List, Art + Details, and Grid all render their card rows within the same collapsible section container.
- Expanded/collapsed sections remain consistent across in-editor view switches, quantity edits, and visual refreshes.
- Artwork loading remains bounded and visibility-based; expanding a section requests thumbnails newly visible on screen.
- The quick-jump strip uses the existing app-themed scrollbar component.

## Fixed
- Long Commander mainboards required scrolling past entire card groups to reach later types such as Lands.
- Collapsing card sections now hides their UI content only, without modifying saved deck entries, draft text, owned cards, or their type data.

## Removed
- No saved decks, collections, printings, proxies, AI reviews, sync data, authorization or existing view modes removed.

## Verification
- Pre-release JavaScript regression checks, Android unit tests and debug APK compilation all passed.
- The signed-release workflow must independently validate all checks and APK signing.
- On device: open a large deck -> Edit -> Visual mainboard. Collapse Creatures, use the jump strip to Lands, then return to Creatures and confirm the section expands. Switch List/Art + Details/Grid and verify quantity editing, previews, artwork, and Review & Save.
