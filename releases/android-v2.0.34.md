# Arcane Vault Android v2.0.34 — Physical collection in native Card Catalog

## Added
- Native catalog source selector for **All Cards**, **Arena**, and **Physical**, with source-aware **All**, **Owned**, and **Missing** filters.
- Separate physical and Arena copy counts in native catalog results and card details.

## Changed
- Physical mode uses actual physical inventory totals from the existing on-device physical ownership database. It does not assume cards absent from Arena are physically owned.
- All Cards provides a catalog-wide view with both inventories represented, and shows separate quantities.
- Switching collections resets pagination and refreshes ownership filters.

## Fixed
- Replaced misleading "Not on Arena" and "Arena owned" controls that hid the physical collection.
- Legacy WebView catalog calls without an explicit source retain the previous Arena quantity behavior.

## Removed
- Misleading "Not on Arena" native collection toggle.

## Safety
- Search is read-only: no changes to inventory, printings, favorite art or migration data.
- Signed releases remain gated by Android CI and signing checks.
- Investigate reported Google Play Protect warning (issue #36) before broad distribution; a signing check is not a Play Protect clearance.
