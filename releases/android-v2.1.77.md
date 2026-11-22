# Arcane Vault Android v2.1.77

## Added
- **Diagnose ChatGPT network** in Android Home → Options. Tests DNS resolution for the official OpenAI authorization host `auth.openai.com` and API host `api.openai.com` independently of ChatGPT plan access and saved tokens.
- Clear, actionable guidance for DNS failures: compare Wi-Fi with mobile data and check Private DNS, VPN and network filtering.
- Unit tests verifying DNS-specific errors are distinguished from other connection failures.

## Changed
- A failed network lookup no longer displays an opaque Android `Unable to resolve host` stack/network message. The result identifies a DNS issue before an account or subscription problem is implied.
- Network diagnostics never send deck contents, collection data, prompts, or account credentials.
- Existing **Continue with ChatGPT** and **Check ChatGPT connection** features are unchanged except for clearer DNS error wording.

## Fixed
- The Android ChatGPT integration now makes the network failure visible independently of whether a valid OAuth authorization has been saved.
- Network errors are no longer confused with ChatGPT plan eligibility or a failed authorization grant.

## Removed
- No deck, sync, collection, ChatGPT account, or tabletop data removed. Credentials stay encrypted locally and are never transferred between devices.

## Verification
- Java and JavaScript regression suites, debug compilation and signed Android release workflow must pass.
- On-device: Home → Options → **Diagnose ChatGPT network**, first on Wi-Fi, then on mobile data if DNS resolution fails. **Check ChatGPT connection** remains a separate authorization and model-access verification.
- A successful DNS check alone does not mean that a ChatGPT authorization has been granted.
