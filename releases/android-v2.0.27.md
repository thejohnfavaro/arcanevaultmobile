# Arcane Vault Android v2.0.27 — Clear Inventory confirmation fix

## Added

- Clear Inventory now provides actionable live feedback: enter the scope's confirmation phrase, check the saved-backup acknowledgement, or proceed when both are satisfied.
- Android mobile parity regression checks confirm that mixed-case phrases pass without bypassing the backup acknowledgement.

## Changed

- Typed inventory-reset phrases are **case-insensitive** and whitespace-trimmed, so `Clear Physical`, `CLEAR PHYSICAL` and `clear physical` all validate for Physical only. Arena and Both follow the same rule.
- The confirmation prompt explicitly explains that capitalization does not matter and indicates when a valid phrase is waiting for the backup checkbox.
- Android build/version is now 2.0.27 (versionCode 47) with updated debug APK artifact naming.

## Fixed

- The **Clear selected inventory** button no longer stays inactive merely because users type the example in title case instead of uppercase.

## Removed

- Nothing. The v2.0.26 native atomic reset, scope-specific isolation, last-clear undo, stored empty-array tombstones, printing-aware Set Progress refresh, backups, import default-to-Replace, collection/deck persistence and signed update behavior remain unchanged.

## Safety and manual acceptance

1. Export a complete backup before using Clear Inventory; verify it exists outside the app.
2. Upgrade via the **signed Android update channel**, without uninstalling the currently installed app.
3. Go to **Data & Sync → Clear inventory**, choose Physical only and type `Clear Physical`; confirm the live prompt acknowledges the phrase.
4. Confirm the action remains disabled until the backup acknowledgement is checked. Uncheck it again; the action must disable.
5. Change the selected scope; the old phrase and acknowledgement must no longer authorize a clear.
6. Cancel without clearing or test a verified Physical-only clear and native Undo. Confirm Arena, decks, printed Set Progress and reopen/restart behavior are unchanged outside the chosen scope.
7. Reimport the same full CSV twice in **Replace physical collection** mode; ownership must not compound.
8. Android debug-build CI, JVM tests, signed release verification, then actual on-device acceptance remain required. A debug APK cannot replace an app signed with a different certificate.
