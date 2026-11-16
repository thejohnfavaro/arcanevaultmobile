# Arcane Vault Android v2.1.59

## Added
- **Windows-aligned paper collector-slot completion** in Set Progress: physical denominators count distinct collector numbers for official paper, nondigital Scryfall printings; ownership is credited only through verified printing IDs or uniquely matched set/collector hints. Name-only ownership never becomes set progress.
- **Read-only mana-symbol detail presentation:** The shared Catalog/Set Progress inspector renders official Scryfall mana glyphs for supported color symbols; other literal mana tokens remain readable. Color identity glyphs are not interactive filters.
- **Verified peer checkpoint identity:** Confirmed peer baseline prompt now shows the stored checkpoint date, partial peer identity, and revision instead of a generic reminder.
- **Native deck/artwork safety-copy export:** Data & Sync → Snapshots shows verified deck/artwork mirror age and record counts, and offers a user-initiated document export through Android storage picker. This is a recovery file, not a replacement for the full inventory backup.
- **Read-only differential summary** in final Windows → Android merge review: count modified/added/missing record identities across Arena, physical printings, saved decks and artwork relative to the shared baseline. Malformed or unsupported records fall back to the existing full-snapshot review.

## Changed
- A versioned set-progress cache discards old physical name-count totals and recomputes collector slots.
- Individual dirty-set recalculations use the same metric and update the aggregate owned/missing summary, avoiding inconsistent overview figures.
- Physical set detail text explicitly explains that percentage completion counts paper collector slots, while individual printing quantities remain separately verified.
- Existing full-snapshot sync, explicit conflict choices, crash-recovery backup and final write approval remain authoritative; a delta report cannot authorize deletion or a write.
- Recovery mirror is export-only, and cannot silently replace the live saved-deck WebView data.
- Updated regression and unit tests for collector-slot counting, official mana-symbol rendering, checkpoint context, mirror export and fail-safe delta summaries.

## Fixed
- Android physical set completion could disagree with Windows by counting card names rather than collector-number slots, and dirty-set refresh could use a different metric from the overview.
- Obsolete regression assertions for name-based completion and earlier card-detail text.
- Paired-device baseline prompts hid which checkpoint they were about to use.
- Native saved-deck recovery mirror could not be inspected or exported.

## Removed
- No former data stores, collections, decks or sync mechanisms removed; this release retains backward compatibility.

## Remaining work
- Differential **transport and apply** are not switched on: Windows and Android must first negotiate base revisions, tombstone rules, payload checksums and fallback semantics.
- A full authoritative native saved-deck/artwork storage cutover is not yet enabled; the verified mirror is a recovery safeguard.
- Set-detail printings are still individual Scryfall editions, while completion percentage counts unique paper collector slots.
- Previously imported cards require a fresh official Scryfall catalog sync to populate their exact mana-cost metadata.

## Data safety
No collection, deck, art preference, saved checkpoint or recovery backup is intentionally deleted. No unattended sync, automatic tombstones, unreviewed overwrite or data reset is introduced.
