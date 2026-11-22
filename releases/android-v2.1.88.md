# Arcane Vault Android v2.1.88

## Added
- More vertical room for AI Deck Review controls and saved analysis when ChatGPT authorization is already stored.

## Changed
- Hide the redundant ChatGPT authorization status and troubleshooting guidance in AI Deck Review for authorized devices.
- Retain actionable connection guidance when ChatGPT is disconnected.

## Fixed
- Excess vertical space consumed by a redundant connected-account banner above the review controls.

## Removed
- No deck, collection, saved analysis, ChatGPT authorization, or sync data removed.

## Verification
- Signed Android release workflow must pass regression and Android unit tests, APK compilation and signing verification.
- On device, authorized AI Review should start with review controls without the connection banner; disconnected state should still show sign-in guidance.
