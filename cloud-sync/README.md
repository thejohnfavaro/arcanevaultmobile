# Wix cloud sync foundation (Android)

This branch adds the opt-in `WixSyncClient` transport for the personal Wix domain `johnfavaro.com`.

**Not enabled:** this class is not wired to existing Data & Sync or to background tasks. It neither modifies app data nor changes the existing QR/Wi-Fi merge flow. It is deliberately safe to merge before rollout.

Cloud contract lives in [Windows repository cloud-sync](https://github.com/thejohnfavaro/arcanevault/tree/feature/wix-cloud-sync-foundation/cloud-sync) and the companion Wix backend must be deployed in the owner's Wix Velo website. HTTP `POST /_functions/avSyncPull` retrieves independent Windows and Android snapshot slots; HTTP `POST /_functions/avSyncPush` uploads a JSON snapshot with an expected revision.

**Next integration steps:** use the existing peer export snapshot as the payload, store the user-entered token using Android Keystore-backed encrypted storage, compare downloaded Windows snapshot against the existing shared baseline using the native peer-review system, and require explicit approval before applying differences. Never auto-apply the returned payload or assume a missing remote record means a deletion. Execute networking on a background executor and handle HTTP 401, 409, timeout and size limits in the UI.

Wix Light is capped at 1,500 CMS items. This personal installation uses one item per device but each Wix item has a finite maximum payload size, so chunking/external storage will be needed for larger snapshots.
