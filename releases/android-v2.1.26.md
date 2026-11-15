# Arcane Vault Android v2.1.26 — Quality-of-Life & Parity

## Added
- Native Deck Library search, folder picker, favorite-only filtering, recent-update/name/commander sorting, and preserved commander cover art.
- Offline, read-only deck insights: known/unknown catalog card coverage, type distribution, average nonland mana value, and 0–7+ mana-curve bars.
- Copy a saved deck as an Arena-compatible or plain text list, and paste/import a decklist as a previewed unsaved mainboard revision.
- Native **My Shortlist** under Catalog, backed by the existing legacy vault_favorites store: card search, recorded physical/Arena counts, remove action and copy list.
- Add/remove Shortlist stars and prominent **Add to Physical Collection** access in native Card Catalog card details.

## Changed
- Decklist imports explicitly require the currently selected commander's name to match, and reject unsupported sideboard/companion sections instead of dropping them.
- Arena printing annotations may be omitted only with a clear confirmation; imported card names never infer new physical printings.
- Mainboard replacement remains unsaved until the existing recovery-backed Review & Save action succeeds.

## Fixed
- Android previously required legacy tools for basic saved-deck search/sort, shortlist and convenient decklist interchange.
- Unknown catalog cards now remain unknown instead of being silently interpreted as zero-cost cards in statistics.

## Removed
- No existing collection, deck or shortlist records removed.

## Safety
- All deck insights and catalog ownership summaries read existing offline SQLite metadata.
- The Shortlist uses the authoritative WebView vault_favorites array and refreshes its in-memory Set without duplicating persistent storage.
- Deck edits still use compare-and-swap checks and private recovery snapshots.
- Physical collection updates still require exact Scryfall printing selection and the existing confirmed edit flow.
- Existing wireless/QR/USB sync contracts and installer/updater behavior are unchanged.
