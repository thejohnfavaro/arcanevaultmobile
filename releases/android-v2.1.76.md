# Arcane Vault Android v2.1.76

## Added
- **Check ChatGPT connection** in Android Home → Options. It renews the app-specific authorization if necessary, calls the same model-access endpoint as AI Deck Review, and reports whether models are usable. No deck or collection information is transmitted for this check.
- Clear guidance that signing into the ChatGPT mobile app or signing into Arcane Vault Windows does not authorize the separate Arcane Vault Android app. Users must opt in via **Continue with ChatGPT** and approve eligible plan use.
- Explicit diagnostics when saved credentials cannot be refreshed, access is denied, or no eligible models are returned.

## Changed
- Android Options now distinguishes **Authorization saved** from **ChatGPT plan authorization verified**, rather than assuming that the presence of a refresh token guarantees working access.
- AI Review explains the one-time Android connection requirement and points to Options for permission diagnostics.
- The connection test never includes saved decklists, inventories, collection identifiers, or AI review prompts.

## Fixed
- Misleading connected indicator for a saved OAuth token that may have been revoked, expired, or not entitled to a usable model.
- Options allows immediate connection verification without requiring a speculative failed AI Deck Review first.

## Removed
- No ChatGPT tokens, saved AI reviews, collections, proxies, decks, sync history or tabletop data intentionally removed. Credentials remain encrypted in Android Keystore-backed local storage and are not copied across devices.

## Verification
- Android regression suite, Java unit tests, debug APK, release signature verification, and signed publication required. The connection test never assumes that a saved token guarantees delegated plan access.
- On-device test: Home → Options → **Check ChatGPT connection**. If no saved authorization, use **Continue with ChatGPT** in this app; approve identity and ChatGPT plan use, return and run the check again.
- Successful connection verification confirms access on Android only; it does not imply access to ChatGPT chat history or Windows' saved credentials.
