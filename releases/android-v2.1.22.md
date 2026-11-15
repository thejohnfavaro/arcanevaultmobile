# Arcane Vault Android v2.1.22 — Native Deck Editing & Compact Catalog

## Added
- Edit saved deck names and mainboard quantities inside the Android native Deck Library.
- Review and confirm saved deck changes before writing.
- Automatic recovery copies of the full saved-deck library before each confirmed edit.
- Compact responsive card-art gallery for Card Catalog and exact owned physical printings.

## Changed
- The Card Catalog opens as a two-column phone gallery, with three or four columns on larger viewports.
- Move collection scope, ownership, printing and advanced filters into a collapsible Filters & Sort drawer so more cards remain visible.
- Remember Card Grid versus Detailed List selection on the device.
- Saved deck edits update the existing authoritative library, not a second native copy.

## Fixed
- Card Catalog controls took most of the phone screen, leaving very little room to browse actual cards.
- Saved decks could previously be viewed from the Android native interface but required the legacy interface for editing.

## Removed
- No collection, deck, catalog, Arena, or printing records removed.

## Safety
- Deck edits preserve IDs, commanders, folders and other metadata, keep revision history, and reject simultaneous changes.
- Full previous deck-library JSON is stored in a private recovery file before a write.
- Physical ownership remains printing-specific; the catalog only displays verified editions in the owned-printing gallery.
- Native browsing and editing do not modify owned card quantities.
