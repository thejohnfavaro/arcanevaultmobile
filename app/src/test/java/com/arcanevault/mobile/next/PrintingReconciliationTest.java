package com.arcanevault.mobile.next;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public final class PrintingReconciliationTest {
    private static UUID uuid(String text) {
        return text == null ? null : UUID.fromString(text);
    }

    private static String optional(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : o.getString(key);
    }

    private static JSONObject fixture() throws Exception {
        try (InputStream stream = PrintingReconciliationTest.class.getResourceAsStream("/ownership-v1.golden.json")) {
            assertNotNull("Golden ownership fixture missing", stream);
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static List<PrintingReconciliation.CatalogPrinting> catalog(JSONArray items) throws JSONException {
        List<PrintingReconciliation.CatalogPrinting> rows = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            rows.add(new PrintingReconciliation.CatalogPrinting(
                uuid(o.getString("printId")), o.getString("name"),
                o.getString("setCode"), o.getString("collectorNumber")));
        }
        return rows;
    }

    private static List<PrintingReconciliation.PhysicalLine> lines(JSONArray items) throws JSONException {
        List<PrintingReconciliation.PhysicalLine> rows = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            rows.add(new PrintingReconciliation.PhysicalLine(
                uuid(o.getString("lineId")), o.getString("name"), o.getInt("quantity"),
                uuid(optional(o, "printId")), optional(o, "setCode"),
                optional(o, "collectorNumber"), o.getString("finish")));
        }
        return rows;
    }

    private static void rejects(Runnable action) {
        try { action.run(); fail("Invalid inventory was accepted."); }
        catch (IllegalArgumentException expected) { /* Required validation. */ }
    }

    @Test public void reconcilesExactWindowsGoldenFixtureWithoutGuessing() throws Exception {
        JSONObject document = fixture();
        assertEquals(1, document.getInt("schemaVersion"));
        List<PrintingReconciliation.CatalogPrinting> catalog = catalog(document.getJSONArray("catalogPrintings"));
        List<PrintingReconciliation.PhysicalLine> physical = lines(document.getJSONArray("physicalLines"));
        JSONObject expected = document.getJSONObject("expected");
        PrintingReconciliation.Result observed = PrintingReconciliation.reconcile(physical, catalog);
        assertEquals(expected.getLong("totalCopies"), observed.totalCopies);
        assertEquals(expected.getLong("confirmedCopies"), observed.confirmedCopies);
        assertEquals(expected.getLong("unresolvedCopies"), observed.unresolvedCopies);
        assertEquals(observed.totalCopies, observed.confirmedCopies + observed.unresolvedCopies);
        assertEquals(expected.getInt("confirmedDistinctPrintings"), observed.confirmedDistinctPrintings);

        JSONArray bySet = expected.getJSONArray("bySet");
        assertEquals(bySet.length(), observed.confirmedBySet.size());
        for (int i = 0; i < bySet.length(); i++) {
            JSONObject want = bySet.getJSONObject(i);
            PrintingReconciliation.SetCount got = observed.confirmedBySet.get(want.getString("setCode"));
            assertNotNull(got);
            assertEquals(want.getInt("distinctPrintings"), got.distinctPrintings);
            assertEquals(want.getLong("copies"), got.copies);
        }

        JSONArray unresolved = expected.getJSONArray("unresolvedLines");
        assertEquals(unresolved.length(), observed.unresolvedLines.size());
        for (int i = 0; i < unresolved.length(); i++) {
            JSONObject want = unresolved.getJSONObject(i);
            UUID expectedId = uuid(want.getString("lineId"));
            PrintingReconciliation.UnresolvedLine got = observed.unresolvedLines.stream()
                .filter(row -> row.lineId.equals(expectedId))
                .findFirst().orElse(null);
            assertNotNull(got);
            assertEquals(want.getLong("copies"), got.copies);
            assertEquals(want.getString("reason"), got.reason.name());
        }

        assertEquals("Name-only card with catalog hints must stay unresolved",
            PrintingReconciliation.UnresolvedReason.NoPrintId, observed.unresolvedLines.get(0).reason);
        assertEquals(8, physical.size()); // No inventory line silently dropped.
    }

    @Test public void repeatedPrintingsAndNameOnlyTransfersNeverFakeSetProgress() {
        UUID printingId = UUID.fromString("00000000-0000-4000-8000-000000000a01");
        PrintingReconciliation.CatalogPrinting printing =
            new PrintingReconciliation.CatalogPrinting(printingId,
                "Split // Name", "TST", "007");
        List<PrintingReconciliation.CatalogPrinting> catalog =
            Collections.singletonList(printing);
        List<PrintingReconciliation.PhysicalLine> lines = Arrays.asList(
            new PrintingReconciliation.PhysicalLine(
                UUID.fromString("00000000-0000-4000-8000-000000000b01"),
                "Split // Name", 2, printingId, "tst", "007", "foil"),
            new PrintingReconciliation.PhysicalLine(
                UUID.fromString("00000000-0000-4000-8000-000000000b02"),
                "Split // Name", 1, printingId, "TST", "007", "nonfoil"),
            new PrintingReconciliation.PhysicalLine(
                UUID.fromString("00000000-0000-4000-8000-000000000b03"),
                "Split // Name", 4, null, "TST", "007", "nonfoil"));

        PrintingReconciliation.Result result =
            PrintingReconciliation.reconcile(lines, catalog);
        assertEquals("All copies remain represented", 7L, result.totalCopies);
        assertEquals("Only explicit matching print IDs are confirmed", 3L,
            result.confirmedCopies);
        assertEquals("Name-only rows stay unresolved despite exact set hints", 4L,
            result.unresolvedCopies);
        assertEquals("Multiple finishes of one printing count once for set completion",
            1, result.confirmedDistinctPrintings);
        PrintingReconciliation.SetCount set = result.confirmedBySet.get("tst");
        assertNotNull(set);
        assertEquals(3L, set.copies);
        assertEquals(1, set.distinctPrintings);
        assertEquals(1, result.unresolvedLines.size());
        assertEquals(PrintingReconciliation.UnresolvedReason.NoPrintId,
            result.unresolvedLines.get(0).reason);

        // A second read must not change totals or mutate the input snapshot.
        PrintingReconciliation.Result second =
            PrintingReconciliation.reconcile(lines, catalog);
        assertEquals(result.confirmedCopies, second.confirmedCopies);
        assertEquals(result.unresolvedCopies, second.unresolvedCopies);
        assertNull("No assignment of previously missing printing identity",
            lines.get(2).printId);
        assertEquals(3, lines.size());
    }

    @Test public void rejectsInvalidLinesWithoutEditingCollections() throws Exception {
        JSONObject document = fixture();
        List<PrintingReconciliation.CatalogPrinting> catalog = catalog(document.getJSONArray("catalogPrintings"));
        PrintingReconciliation.PhysicalLine first = lines(document.getJSONArray("physicalLines")).get(0);

        PrintingReconciliation.PhysicalLine noId = new PrintingReconciliation.PhysicalLine(
            first.lineId, first.name, first.quantity, null, first.setCode, first.collectorNumber, first.finish);
        PrintingReconciliation.Result unknown = PrintingReconciliation.reconcile(
            Collections.singletonList(noId), catalog);
        assertEquals(0L, unknown.confirmedCopies);
        assertEquals(first.quantity, unknown.unresolvedCopies);

        PrintingReconciliation.PhysicalLine alternateName = new PrintingReconciliation.PhysicalLine(
            first.lineId, "Alternative split-card face alias", first.quantity,
            first.printId, first.setCode, first.collectorNumber, first.finish);
        assertEquals(first.quantity, PrintingReconciliation.reconcile(
            Collections.singletonList(alternateName), catalog).confirmedCopies);

        rejects(() -> PrintingReconciliation.reconcile(Arrays.asList(first, first), catalog));
        rejects(() -> PrintingReconciliation.reconcile(Collections.singletonList(
            new PrintingReconciliation.PhysicalLine(first.lineId, first.name, -1, first.printId,
                first.setCode, first.collectorNumber, first.finish)), catalog));
        rejects(() -> PrintingReconciliation.reconcile(Collections.singletonList(first),
            Arrays.asList(catalog.get(0), catalog.get(0))));

        assertEquals("Input inventory must remain intact", 1, first.quantity);
    }
}
