# Arcane Vault Android v2.1.85

## Added
- A lifecycle-aware OAuth handoff: when Chrome completes the local authorization callback, Arcane Vault waits for its Android Activity to resume before exchanging the one-time authorization code.
- A specific error if the browser callback completed but Arcane Vault did not return to foreground.

## Changed
- OAuth token exchange no longer begins automatically while the external sign-in browser may still own the foreground.
- Existing DNS-only retry behavior and safe error reporting remain in place.

## Fixed
- A probable timing issue where system and enumerated Android network DNS were unavailable during background token exchange despite successful network checks in the foreground.

## Removed
- No collection, deck, sync, or saved account data removed.

## Verification
- Signed release workflow must pass regression tests, Android unit tests, release APK compilation, and signing verification.
- On-device: Home → Options → Continue with ChatGPT. After browser confirmation, return to Arcane Vault to allow code exchange. Then Check ChatGPT connection.
