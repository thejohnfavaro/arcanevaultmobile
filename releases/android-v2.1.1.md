# Android v2.1.1 — Peer Sync foundation (export and review)

- Adds **Export peer sync file** and **Preview Windows / Android peer file** under native Data & Sync.
- Snapshot includes native physical and Arena records, saved deck library and preferred artwork from their existing authoritative storage; no separate invented collection or deck database.
- Saves a stable Android peer UUID and exports cross-platform schema-1 peer envelope containing the full schema-2 workspace fields.
- Preview compares exact record identities, shows additions, differences and peer-absent records, and refuses duplicate/missing identities.
- Documents selected with the Android file picker are read with a strict 30-MiB limit.
- **This milestone does not apply incoming edits or guarantee conflict-free synchronization.** There is no automatic merge; both platforms must eventually agree on a common baseline. Backups and existing one-way QR/USB transfers remain available.
- Play Protect report #36 remains under investigation. No weakening of install security.
