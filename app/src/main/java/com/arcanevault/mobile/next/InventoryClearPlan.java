package com.arcanevault.mobile.next;

/**
 * Pure, side-effect-free scope planner for Android's native inventory clear.
 * Never edits collections, copies, printing IDs, decks or metadata. The
 * Android bridge performs the guarded atomic native write separately.
 */
public final class InventoryClearPlan {
    private InventoryClearPlan() {}

    public static final class Plan {
        public final boolean clearArena, clearPhysical, alreadyEmpty;
        public final String nextArena, nextPhysical;

        private Plan(boolean arenaScope, boolean physicalScope,
                     String arena, String physical) {
            clearArena = arenaScope;
            clearPhysical = physicalScope;
            nextArena = arenaScope ? "[]" : arena;
            nextPhysical = physicalScope ? "[]" : physical;
            // Even an absent native key must be set to [] to prevent legacy
            // imports and bundled defaults from reappearing after restart.
            alreadyEmpty = (!arenaScope || "[]".equals(arena)) &&
                           (!physicalScope || "[]".equals(physical));
        }
    }

    public static Plan create(String scope, String arena, String physical) {
        if (!"physical".equals(scope) && !"arena".equals(scope) &&
            !"both".equals(scope))
            throw new IllegalArgumentException("Explicit inventory scope required.");
        return new Plan("arena".equals(scope) || "both".equals(scope),
                        "physical".equals(scope) || "both".equals(scope),
                        arena, physical);
    }

    public static boolean canUndo(String scope, String arena, String physical) {
        Plan plan = create(scope, arena, physical);
        return (!plan.clearArena || "[]".equals(arena)) &&
               (!plan.clearPhysical || "[]".equals(physical));
    }
}
