# Arcane Vault Android v2.0.29 — Native companion foundation

## Added
- A real native Android home with physical/Arena copy and row totals.
- Native, searchable, read-only Physical and Arena collection lists with bounded batches and load-more.
- Native Home / Physical / Arena navigation plus explicit Legacy tools access.

## Changed
- After existing WebView origin recovery, native Android views become the default Activity screen.
- Existing WebView feature surface remains accessible and unchanged through Legacy tools; Android Back returns to native home.

## Fixed
- Native startup collection browsing no longer requires HTML, JavaScript, or an HTTP-backed WebView view.
- Inventory JSON is read from the existing native authoritative preferences on a worker thread rather than creating a second copy.

## Removed
- Nothing yet. WebView is intentionally retained for the features not yet migrated.

## Safety
- No migration, collection writes, account change, uninstall, or replacement of native inventory.
- Signed release must match the installed certificate; debug APKs are for separate test environments.
