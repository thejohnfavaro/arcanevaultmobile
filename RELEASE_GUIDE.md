# Arcane Vault: opt-in update notifications and synergy tags

## Update notifications

The **Import & Data → Application updates** panel is built into both the Windows and Android projects.

1. Publish public **compiled** releases in a GitHub repository (for example `RNGesus/ArcaneVault`). The example repository name is only a placeholder; no repository is preconfigured.
2. Name the version-specific release tags `windows-v4.9.3` and `android-v1.5.0`, continuing the same tag patterns for later versions.
3. Attach a compiled Windows installer/package or signed Android APK to each corresponding release and supply human-readable release notes. Source code ZIPs are not installable application updates. Maintain the Android signing certificate across versions.
4. In each app, open **Import & Data → Application updates**, enter `owner/repo`, then select **Check for updates**. Optionally enable weekly startup checks. The app looks at the 50 newest *published, stable* GitHub Releases, filters by platform tag, compares numeric versions, and links to the matching release page.

This is a notification-only updater. It **does not download or silently install executables**, write system files, change existing app data, or run arbitrary release instructions. Users must explicitly open the release and install the appropriate signed build. GitHub API access needs internet, and rate limits / CORS or networking failures are shown in the status line. No background polling occurs when the application is closed. If the GitHub repo or tag is missing, no new version can be found. Private repositories and APK auto-install are not supported in this version.

## Card type synergy

Card popups display functional roles plus identified creature types. Click a creature-type chip to open Strategy Workbench filtered to that type. The **Deck Studio → Find cards** selector prioritizes creature types already in the current draft. Strategy Workbench also supports a full creature-type selector and can combine a role, a type, and a text query. The tags are rule-based suggestions based on the synced Oracle/type-line metadata; they are not a claim that a card creates a complete combo or is legal in a given Arena format.

Example: **Liliana, Death's Majesty** is expected to classify as `Board Wipe`, `Tokens`, and `Zombie synergy` when Scryfall metadata includes her relevant abilities. Re-sync individual card details if text is missing.

## Testing

`node tests/regression.mjs` and `node tests/new-features.mjs` can be run in both source projects. The tests do not substitute for Windows and Android native compilation, on-device testing, or a live public GitHub Releases feed.
