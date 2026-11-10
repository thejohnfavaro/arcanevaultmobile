# AVN-004: Android ownership-contract conformance

This adds **only an unused, pure Java reconciliation library and tests**; it does not change the installed companion UI, SQLite schema, network transfers, version, APK signature, or any real collections.

- Uses a copy of Windows' `contracts/ownership-v1.golden.json` fixture.
- Confirms 13 copies (6 confirmed, 7 unresolved), three distinct confirmed printing identities and exact Set Progress counts.
- Name-only records and unknown catalog IDs remain unresolved; conflicting set/collector hints remain unresolved.
- Multiple finishes and multiple printings never collapse into one guessed owned printing.
- Duplicate stable line IDs, invalid quantities and duplicate trusted catalog IDs are rejected.
- Tests run with `./gradlew :app:testDebugUnitTest` on Android CI and signed release CI.

**Not yet integrated into sync or inventory:** This conformance code is a foundation for future staged migration, before enabling any ownership edits from the card scanner. Updating the app's version solely for unused test-only infrastructure is intentionally avoided.

**Required next step:** Explicit read-only extraction of both legacy inventory stores, backup parity checks, transactional SQLite schema and safe hand-off to this contract.
