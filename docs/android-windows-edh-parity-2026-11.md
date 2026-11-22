# Arcane Vault EDH Lab parity review — Android vs Windows

Scope: native Android deck discovery, Deck Details and Deck Editing, compared against the native Windows `ArcaneVault.Next.EDHLab` source tree. This is an implementation audit, **not** a claim of full 1:1 app parity. Keep the existing Android data model and sync contract as authoritative.

## Implemented and verified in Android code

| Capability | Android source | State |
| --- | --- | --- |
| Existing saved decks, commander cover images, search, sort and filters | `NativeWorkspaceScreen` | Implemented |
| Draft-only editing with Review & Save and stale-save protection | `NativeDeckDraft`, `NativeWorkspaceScreen` | Implemented |
| Arena text export and import | `NativeDeckTextExchange` | Implemented |
| List, Art + Details, Grid, exact card previews | `NativeWorkspaceScreen`, `NativeCatalogScreen` | Implemented |
| Grouped card types, collapse/jump and expand/collapse all | `NativeDeckTypeGroups`, `NativeWorkspaceScreen` | Implemented |
| Deck type composition from the unsaved draft | `NativeWorkspaceScreen` | Implemented |
| Mana curve with average MV and unknown-card coverage | `CatalogStore.deckInsights` | Previously available in saved Deck Details; added to unsaved editor |
| Color identity of known nonland spells | `CatalogStore.deckInsights` | Added; multicolor copies count in multiple bins |
| Approximate colored mana symbol demand | `NativeDeckManaPips` | Added; Windows `EdhLabColoredPips` policy |
| Natural land-drop probabilities | `NativeDeckLandOdds` | Added; only a complete, verified 99-card mainboard |
| Smart Mana / owned substitution workflow | `NativeSmartMana`, `NativeDeckSubstitutions` | Implemented |
| Saved AI reviews and full card inspector | `MobileAiReviewScreen`, `NativeWorkspaceScreen` | Implemented |
| Local collection, set progress, native widgets, tabletop companion | Android native screens and widgets | Implemented; broader cross-platform surface parity not implied |

## Remaining Windows EDH Lab parity work

| Windows implementation | Android gap | Recommended next phase |
| --- | --- | --- |
| `EdhLabDeckHealth`, `EdhLabStrategyGoals` | Full role-specific deck-health rubric with adjustable goals | Implement a read-only role coverage report with explicit unknown-card warnings |
| `EdhLabSynergyPackages`, `EdhLabSynergyAdvisor`, `EdhLabSynergyChains` | Deterministic local synergy suggestions and package workflows | Port scoring and verification rules before presenting recommendations |
| `EdhLabLandAdvisor`, `EdhLabManaPlanner`, `EdhLabManaBaseBuilder` | Fine-grained land-source planning and early-turn color pressure | Port local source assumptions and warnings; do not equate color identity with mana production |
| `EdhLabDeckOptimizer`, `EdhLabSwapImpact` | Quantified, read-only before/after curve and land effects for proposed swaps | Integrate into the existing owned substitution review, without auto-saving |
| `EdhLabDraftCatalog`, `EdhLabReadOnlyCache` | Windows-equivalent card metadata caching/diagnostics for every advanced analysis | Measure indexing and refresh latency before any architectural migration |
| `EdhLabThreeCardEngines`, `EdhLabAutomaticSubstitutions` | Higher-order interactions and all Windows substitution categories | Staged port with strong card-exactness and inventory safeguards |

## Safety and acceptance criteria

1. A chart must never silently count an unknown card as a zero-cost spell or a nonland card.
2. Every analysis uses local catalog data only; no inventory mutation, hidden proxy conversion, draft save or card substitution.
3. Commander is included in overall saved-deck insights but **excluded** from a 99-card mainboard's natural land-drop model.
4. Mana curve, spell identities, pip demand and actual source availability must be named as different quantities.
5. Draft insights must reject stale responses if the user edits the draft or leaves the editor.
6. The existing art cache, device sync, saved AI reviews and three deck view modes remain unaffected.
7. Every staged parity feature needs unit tests, regression checks and a signed release gate.

Full 1:1 Windows EDH Lab parity remains a multi-feature effort. This pass closes the **basic, offline deck-analytics gap**, not the full deterministic optimization and synergy gap.
