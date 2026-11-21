# Arcane Vault Android Device Compatibility Plan
Status: proposed foundation • November 21, 2026

## Goal

Support a wide range of Android form factors with one maintainable native app: compact and large phones, foldables, tablets, and eventually desktop-windowed Android/ChromeOS. Preserve local-first storage, existing decks and inventories, physical/Arena ownership separation, and Windows↔Android differential sync. Device support is an engineering/tested guarantee, **not** a claim that every device currently works.

## Current baseline (checked against `main`)

- App version: 2.1.71 (`versionCode 127`); `minSdk 26` (Android 8.0), `compileSdk 35`, `targetSdk 35`, Java 17.
- Native Java Views and some legacy WebView storage. `NativeCompanionScreen` owns navigation; `NativeWorkspaceScreen` owns saved-deck editing and local collection pages. The Tabletop Companion has orientation-sensitive controls.
- `NativeSystemInsets` already exists; retained WebView and native Views must both receive edge-to-edge/insets testing.
- APK CI and signed release workflows exist. Existing unit tests cover some deck and sync contracts; there is not yet a broad device/emulator matrix.
- Current working Android AI parity is tracked separately in PR #91. Do not block or merge its implementation solely because this plan exists.

## Compatibility promise, staged

| Tier | Target | Support policy |
| --- | --- | --- |
| A — baseline | Android 10–17 modern phones, compact/medium portrait, including 360dp-wide devices | Primary release gate: install/update, catalog, deck editor, AI Review, sync, life tracker, data preservation |
| B — broad phones | Android 8–9 devices still allowed by minSdk 26, 320dp layouts; landscape; large text/display scaling | Best effort with explicit smoke tests and known limitations; consider deprecating only with measured evidence |
| C — adaptive | Small/large tablets, foldable cover/unfolded displays, split screen, freeform resizable windows | Progressive adoption: preserve state across folding/resizing and display useful multi-pane views |
| D — specialized | ChromeOS/desktop Android, stylus/mouse/keyboard, DeX-like modes | Later validation, not baseline marketing claim |

Support is defined by current *app window width*, not hardware model or physical diagonal. Use Android's width breakpoints: compact <600dp, medium 600–839dp, expanded >=840dp; consider large >=1200dp and extra large >=1600dp as future tiers. Also test constrained height and landscape.

## First engineering milestones (suggested order)

### M0 — Audit and guardrails
- Record min/target/compile SDK and permissions in a machine-readable compatibility report during CI; avoid unnoticed SDK changes.
- Inventory layout assumptions: fixed widths/heights, one-column lists, clipped dialogs, navigation bar overlap, image resizing, minimum touch target, scroll nesting, and hard-coded orientation. Audit: Home, catalog, shortlist, physical/Arena inventory, set progress, deck details/editor, EDH Lab, AI Review, data/sync, widgets, Tabletop Companion.
- Add a device diagnostics panel with app version, SDK, window dimensions in dp, density, font scale, orientation, locale and form-factor classification; **no personal collection data or identifiers**. Make report export user initiated.

### M1 — Window-adaptive shell
- Introduce `WindowLayoutPolicy` determined from live window metrics (not `screenWidthDp` read once at construction).
- Compact: current bottom navigation, one pane, full-width editor/review.
- Medium: optional 2-pane screens where useful; keep bottom nav or compact rail depending on available height.
- Expanded: navigation rail/sidebar and catalog/list + details, saved decks + preview, EDH Lab draft + AI recommendations.
- Preserve selected deck, unsaved text, scroll position, filters, active tab and connection *state* through configuration changes; **never** store tokens in instance-state bundles.
- Apply insets consistently (gesture nav, 3-button nav, cutouts, keyboard/IME, landscape) using the existing inset helper as a starting point.
- Tabletop Companion may have its own full-screen orientation policy, but must tolerate forced landscape and resizable windows.

### M2 — Presentation and accessibility
- Respect system font scale at 1.0×, 1.3×, 1.5×, 2.0×; no clipped buttons, card metadata, or AI review recommendations.
- Text contrast, large touch targets (>=48dp where feasible), talkback labels, focus order, keyboard controls.
- Landscape/wide windows should *use* width, not stretch one card/tile to absurd size. Bound content widths and increase columns based on actual width.
- Responsive image selection/decoding and caches; limit simultaneous artwork jobs on low-memory devices.
- For AI Review: mobile stacks markdown swap tables as accessible labeled comparisons; tablet can use tables. Never show raw markup or crop a required data field.

### M3 — Robustness and platform readiness
- Test process death during editing, background/foreground transition during OpenAI OAuth, authentication renewal, sync transfer, and cached artwork loading.
- Use Android Keystore on each device; no secrets in sync, backup, screenshots, or logs.
- Check manifest's `allowBackup` against credential/private-data exclusions; consider `dataExtractionRules` and `fullBackupContent` as appropriate.
- Update target SDK to 36+ in a separate reviewed compatibility change; Android 16/17 enforce stronger large-screen orientation/resizability behavior. Do not increase targetSdk just to satisfy version milestones without test coverage.
- Stable app identity/signing and non-destructive updates across architectures and OS versions; check OTA rollback behavior where supported.

## CI / emulator matrix

Each PR (fast lane): Java unit tests, existing JS/sync tests, assemble debug, lint/static checks, prompt-contract tests, 1 compact Android emulator smoke test (API 35/36).

Nightly: device emulator sweep: API 26, 29, 33, 35, 36/37 where SDK images are available; widths 320, 360, 412, 600, 840, 1200dp; portrait, landscape, resized, fold/unfold events where emulator supports; fonts 100%, 150%, 200%; light/dark where relevant; 3-button/gesture navigation.

Release gate: physical modern Samsung/Pixel-class phone + one small/older phone or emulator + tablet/foldable emulator; smoke test install/update data preservation, Android↔Windows differential sync, offline collection and deck editing, widgets, tabletop, AI review authorization/restart/revoke, backup/restore.

Avoid claiming physical-device certification based solely on emulator results.

## Cross-platform development contract

Every feature issue/PR should include:
1. Shared user-facing behavior and domain rules, versioned when serialized.
2. Windows UI and Android compact/adaptive UI acceptance criteria.
3. Data/sync compatibility and migration impact (default: no change).
4. Privacy, offline behavior and error states.
5. Automated tests for shared contracts; Windows build + Android build; at least one device-size screenshot for UI changes.
6. Release note with Windows and Android version mapping.

For AI Review, keep an independent per-device OAuth session, but share prompt-contract fixtures and AI-response test fixtures. Cross-platform parity means the same analysis functionality and read-only ownership rules, not identical controls or pixel dimensions.

## Acceptance checklist (definition of 'compatible')

- [ ] App installs, updates and launches, preserving inventories/decks and previous snapshots.
- [ ] No essential controls hidden under system bars or keyboard.
- [ ] Back navigation doesn't discard unsaved drafts, sessions, or filters without confirmation.
- [ ] Compact portrait and landscape allow reading and editing without horizontal scroll of critical text.
- [ ] Expanded layouts are useful and reflow after split-screen resizing/folding.
- [ ] AI Review renders legible headings and swap proposals without raw markdown, with opt-in ownership and no unsolicited send.
- [ ] Offline catalog, prices last cached, deck editing and tabletop work without network.
- [ ] Sync produces no duplicate or lost collection entries across Windows and Android.
- [ ] Screen reader and 200%-font-scale flows remain operable.

## External platform references
- https://developer.android.com/develop/ui/views/layout/use-window-size-classes
- https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps
- https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability
- https://developer.android.com/develop/adaptive-apps/quality-guidelines/adaptive-app-quality
