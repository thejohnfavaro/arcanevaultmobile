# Arcane Vault Android v2.1.82

## Added
- Independent active-network DNS resolution check during OAuth token exchange fallback.
- Distinct failure messages when Android has no active network, both DNS resolvers fail, or active-network DNS resolves but the HTTPS connection still encounters a hostname lookup failure.

## Changed
- The existing default HTTPS token exchange remains first; active-network fallback is attempted only for a confirmed DNS failure.
- Error reporting remains scoped to networking status, without exposing authorization codes, tokens or account identifiers.

## Fixed
- v2.1.81 fallback failures previously displayed the same generic DNS error as the original attempt, making it impossible to distinguish fallback outcomes.

## Removed
- No collection, deck, sync or credential data removed.

## Verification
- Signed release workflow must pass regression tests, Android unit tests, APK compilation and signature verification.
- On device, Home → Options → Continue with ChatGPT displays the specific fallback result if token exchange still fails.
