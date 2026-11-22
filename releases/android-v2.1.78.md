# Arcane Vault Android v2.1.78

## Added
- Separate DNS lookup and HTTPS reachability checks for `auth.openai.com` and `api.openai.com` under Home → Options → Diagnose ChatGPT network.
- Per-host diagnostic reporting for DNS failures, TLS errors, connection timeouts and HTTP responses, without transmitting credentials or deck contents.
- A persistent, inline explanation beneath Check ChatGPT connection.

## Changed
- Check ChatGPT connection remains accessible even without saved authorization so the app can explain what is missing.
- Improved network troubleshooting messages distinguish browser reachability from app-specific behavior.
- Check ChatGPT connection displays progress and becomes available again after the check.

## Fixed
- Silent or confusing ChatGPT connection checks when an Android authorization is absent.
- Network diagnostic output that previously obscured which hostname or stage failed.

## Removed
- No user data, saved decks, collections, sync records, or authorization material removed.

## Verification
- Signed release workflow must pass regression tests, Android unit tests, release compilation and APK signature verification.
- On device: Home → Options → Diagnose ChatGPT network, then Check ChatGPT connection.
- Successful HTTPS reachability alone does not verify OAuth permission or AI model availability.
