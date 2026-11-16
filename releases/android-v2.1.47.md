# Arcane Vault Android v2.1.47

## Added
- Explicit update-status explanation that distinguishes a published, signed APK from later source commits or debug builds.
- Safe diagnostic wording for newer unpublished Android versions.

## Changed
- Retire the "Settings & legacy" navigation from native Data & Sync.
- Commander browsing now copies the commander name without launching the obsolete WebView deck editor.
- Native navigation remains the primary user-facing application.
- Retain the existing hidden WebView storage bridge for saved decks, preferred artwork and legacy-origin recovery until those records are migrated to a native persisted store.
- Retain the signed production release workflow and its package identity and checksum requirements.

## Fixed
- The updater no longer reports "up to date" when only the most recently published signed APK is available; it explains that later source revisions are not installable releases.
- Obsolete legacy UI entry points no longer divert users from the native deck workflow.

## Removed
- Legacy UI navigation entry points from native Data & Sync and Commander browsing; no saved data, sync baselines, inventories, artwork, or signing keys removed.
