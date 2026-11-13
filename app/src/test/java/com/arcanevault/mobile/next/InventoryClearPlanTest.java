package com.arcanevault.mobile.next;

import static org.junit.Assert.*;

import org.junit.Test;

public final class InventoryClearPlanTest {
    private static final String ARENA =
        "[{\"name\":\"Arena Favorite\",\"qty\":4}]";
    private static final String PHYSICAL =
        "[{\"name\":\"Split // Back\",\"qty\":3,\"set\":\"TST\",\"number\":\"007\",\"scryfallId\":\"00000000-0000-4000-8000-000000000001\"}]";

    @Test public void physicalResetNeverTouchesArenaOrPrintingRecovery() {
        InventoryClearPlan.Plan plan = InventoryClearPlan.create(
            "physical", ARENA, PHYSICAL);
        assertFalse(plan.clearArena);
        assertTrue(plan.clearPhysical);
        assertEquals(ARENA, plan.nextArena);
        assertEquals("[]", plan.nextPhysical);
        assertFalse(plan.alreadyEmpty);
        assertTrue(InventoryClearPlan.canUndo("physical", ARENA, "[]"));
        assertFalse("Never replace a newly imported physical collection",
            InventoryClearPlan.canUndo("physical", ARENA, PHYSICAL));
    }

    @Test public void arenaResetNeverTouchesPhysicalOrSetProgressPrintingIds() {
        InventoryClearPlan.Plan plan = InventoryClearPlan.create(
            "arena", ARENA, PHYSICAL);
        assertTrue(plan.clearArena);
        assertFalse(plan.clearPhysical);
        assertEquals("[]", plan.nextArena);
        assertEquals(PHYSICAL, plan.nextPhysical);
        assertTrue(InventoryClearPlan.canUndo("arena", "[]", PHYSICAL));
        assertFalse(InventoryClearPlan.canUndo("arena", ARENA, PHYSICAL));
    }

    @Test public void clearBothRetainsNoOwnedInventoriesAndBlocksStaleUndo() {
        InventoryClearPlan.Plan plan = InventoryClearPlan.create(
            "both", ARENA, PHYSICAL);
        assertTrue(plan.clearArena);
        assertTrue(plan.clearPhysical);
        assertEquals("[]", plan.nextArena);
        assertEquals("[]", plan.nextPhysical);
        assertFalse(plan.alreadyEmpty);
        assertTrue(InventoryClearPlan.canUndo("both", "[]", "[]"));
        assertFalse(InventoryClearPlan.canUndo("both", "[]", PHYSICAL));
        assertFalse(InventoryClearPlan.canUndo("both", ARENA, "[]"));
    }

    @Test public void emptyTombstonesSurviveRestartAndMissingDataMustBeSealed() {
        InventoryClearPlan.Plan empty = InventoryClearPlan.create(
            "both", "[]", "[]");
        assertTrue(empty.alreadyEmpty);
        InventoryClearPlan.Plan absent = InventoryClearPlan.create(
            "physical", ARENA, null);
        assertFalse("Missing native key is not a durable reset tombstone",
            absent.alreadyEmpty);
        assertEquals("[]", absent.nextPhysical);
        assertEquals(ARENA, absent.nextArena);
    }

    @Test public void refusesUnspecificOrUnknownScope() {
        for (String scope : new String[] {"", "all", "cards", "both+decks"}) {
            try {
                InventoryClearPlan.create(scope, ARENA, PHYSICAL);
                fail("Unsupported scope accepted: " + scope);
            } catch (IllegalArgumentException expected) { }
        }
    }
}
