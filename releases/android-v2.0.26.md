# Arcane Vault Android v2.0.26 — Selective Inventory Reset & Clean Reimport

## Added

- Data & Sync includes a clearly labeled **Clear inventory — clean reimport** panel with **Physical only**, **Arena only**, and **Both Physical and Arena** choices.
- Every reset requires both a scope-specific phrase (`CLEAR PHYSICAL`, `CLEAR ARENA` or `CLEAR BOTH`) and explicit acknowledgement that a complete backup has been saved and checked.
- Native Android bridge now performs an atomic, guarded collection reset in the authoritative SharedPreferences storage, retaining the previous selected inventory for **Undo last clear**. It rejects stale snapshots and refuses to overwrite an imported replacement on undo.
- Explicit native `[]` tombstones protect reset collections from legacy-origin migration, bundled defaults and SQLite fallback repopulation after restart.
- After a successful reset/undo, Android refreshes both the local physical/Arena catalog mirrors and the visible collection and Set Progress views.
- JVM regression tests for the three independent inventory scopes, retained printings, previously empty collections, invalid scopes, and reset undo safety; JavaScript/UI wiring tests.

## Changed

- **Physical CSV import already defaults to Replace**. The Data & Sync instructions now explicitly warn that **Add quantities** is additive and will compound counts on repeat imports.
- The debug APK and production signed APK are clearly separated. Signed production publication is owned by `.github/workflows/android-release.yml`, including certificate verification, SHA-256 and versioned release notes; a second competing publishing step was removed from the debug-build workflow.
- Version is **2.0.26** (Android versionCode **46**).

## Fixed

- Users can independently clear outdated physical or Arena copies before replacing their collection, rather than having to delete both or uninstall the app.
- A successful native clear can be undone only if the selected inventory has remained empty; it will not overwrite subsequent imports.
- Reset actions explicitly refresh physical printing mirrors to avoid stale Set Progress ownership when possible; a failed mirror refresh is reported and retried on normal app startup.

## Removed

- No deck library, preferences, saved artwork, settings, collected metadata or unrelated collection scope is removed by a selective reset.
- Removed the obsolete duplicate signed publishing path from the debug APK workflow; the separately signed official release workflow is retained.

## Important data-safety notes

- **Export a full backup before using Clear inventory.** The in-app prior-clear recovery only covers the selected native inventory and is replaced by a subsequent clear. Your independent JSON export is the complete recovery source.
- A reset is not an import: after clearing Physical, open **Physical → Import CSV**, leave the import mode on **Replace physical collection**, and select your complete authoritative export. Do not choose Add quantities for a snapshot.
- Android's native source of truth is separate from its cached SQLite printing catalog. If a mirror refresh is interrupted after the atomic clear, reopening the app repeats the ownership mirroring from the durable collection.
- The existing installed Android app must accept the same signing certificate. A debug APK or mismatched signer cannot safely overwrite the existing installation.
- Android CI passing does not substitute for user physical-device acceptance of storage, import, Set Progress, and clean restart.

## Local acceptance

1. Use the installed app's **Save full backup**, check the file outside the app, and note physical/Arena totals and saved deck count.
2. Update to signed v2.0.26. Verify all previously owned cards and decks are still present after relaunch, *before using Clear inventory*.
3. In **Data & Sync → Clear inventory**, select **Physical only**, type **CLEAR PHYSICAL**, acknowledge the backup and confirm. Confirm physical totals and physical Set Progress are zero, while Arena and deck library are unchanged.
4. Tap **Undo last clear** before importing. Confirm the previous physical printings and Set Progress reappear correctly.
5. Clear Physical again, then import a full physical CSV with the default **Replace physical collection** mode. Import it a second time in Replace mode and verify totals do not compound.
6. Test **Arena only** and **Both** on a private, recoverable test collection; verify no other data is altered.
7. Restart the app after a clear. Explicitly empty inventories must remain empty, not be reconstructed from legacy or cached catalog mirrors.
8. Verify QR/USB Windows-to-Android transfers, split-card printing identities, owned printing counts and the signed in-app update path.

Production rollout remains gated on successful Android build, unit/regression suite, signed APK release verification and the user's on-device acceptance.
