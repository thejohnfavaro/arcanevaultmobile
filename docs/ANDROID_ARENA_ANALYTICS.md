# Read-only Arena analytics on Android (v2.1.60)

Arcane Vault Windows is the authoritative source of Arena match results. Android does **not** infer wins from its saved decks, reconstruct matches from inventory counts, or access the Windows-only Arena Player.log.

## One-time / manual refresh

1. On Windows v0.9.145 or later, use **Data & Sync → Export peer sync file**. The export includes the normal inventory envelope and may include an optional `matchAnalytics` block holding up to 2,000 confirmed, assigned match outcomes.
2. Transfer the peer JSON to your Android device using your normal trusted local transfer.
3. On Android, open **Data & Sync → Preview peer file**, select the Windows peer JSON, then choose **Import analytics** in the comparison dialog.
4. Open **Decks → Deck Analytics**. Choose a saved deck name, optional comparison, recorded format and time range. The display shows W/L, streaks, recent form, weekly outcomes, and trends.
5. Repeat import to refresh newer Windows results. The saved analytic snapshot remains visible offline.

The imported data are **presentation-only**. They are persisted in separate Android SharedPreferences and do not touch the authoritative physical collection, Arena collection, deck library, baseline revision, or sync conflict choices. The original Windows peer envelope's inventory `data`, `schema`, `revision` and `format` are unchanged.

## Limits and security

- This feature is not automatic live or differential bidirectional match-history sync. Android has a bounded snapshot of outcomes from a manually reviewed export. The Windows Stream Deck and EDGE snapshots remain separate.
- Only confirmed `win`/`loss` outcomes with a deck name are transmitted. Unknown/unassigned matches are excluded, no opponent identifying information or match IDs are sent.
- Data are validated for format, size, dates, deck names, result types and peer schema before import. Corrupt optional data must not prevent a normal inventory preview.
- Peer files and trusted-LAN QR transfers are not cryptographically authenticated. Import only snapshots you generated yourself. The explicit confirmation is not a claim of origin authenticity.
- This presentation snapshot uses display deck names, which can conflate distinct saved decks with identical names and split history after renames. Stable native-deck identity parity is a remaining 1.0 audit item.
- For updates, install only the production-signed release with the same certificate as your existing installation. Debug APKs cannot replace a differently signed production installation without data-destructive uninstall.
