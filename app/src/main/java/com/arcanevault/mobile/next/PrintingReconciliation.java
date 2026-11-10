package com.arcanevault.mobile.next;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Experimental, read-only v1 physical ownership contract. It never guesses
 * printings by name/set hints and never writes a catalog or inventory record.
 * No shipping UI or sync path calls this class yet.
 */
public final class PrintingReconciliation {
    private PrintingReconciliation() { }

    private static final UUID EMPTY_ID = new UUID(0L, 0L);

    public static final class PhysicalLine {
        public final UUID lineId, printId;
        public final String name, setCode, collectorNumber, finish;
        public final int quantity;

        public PhysicalLine(UUID lineId, String name, int quantity, UUID printId,
                            String setCode, String collectorNumber, String finish) {
            this.lineId = lineId;
            this.name = name;
            this.quantity = quantity;
            this.printId = printId;
            this.setCode = setCode;
            this.collectorNumber = collectorNumber;
            this.finish = finish;
        }
    }

    public static final class CatalogPrinting {
        public final UUID printId;
        public final String name, setCode, collectorNumber;

        public CatalogPrinting(UUID printId, String name, String setCode, String collectorNumber) {
            this.printId = printId;
            this.name = name;
            this.setCode = setCode;
            this.collectorNumber = collectorNumber;
        }
    }

    public enum UnresolvedReason {
        NoPrintId, PrintIdAbsentFromCatalog, SourceMetadataConflict
    }

    public static final class UnresolvedLine {
        public final UUID lineId;
        public final long copies;
        public final UnresolvedReason reason;

        public UnresolvedLine(UUID lineId, long copies, UnresolvedReason reason) {
            this.lineId = lineId;
            this.copies = copies;
            this.reason = reason;
        }
    }

    public static final class SetCount {
        public final String setCode;
        public final int distinctPrintings;
        public final long copies;

        private SetCount(String setCode, int distinctPrintings, long copies) {
            this.setCode = setCode;
            this.distinctPrintings = distinctPrintings;
            this.copies = copies;
        }
    }

    public static final class Result {
        public final long totalCopies, confirmedCopies, unresolvedCopies;
        public final int confirmedDistinctPrintings;
        public final List<UnresolvedLine> unresolvedLines;
        public final Map<String, SetCount> confirmedBySet;

        private Result(long totalCopies, long confirmedCopies, long unresolvedCopies,
                       int confirmedDistinctPrintings, List<UnresolvedLine> unresolvedLines,
                       Map<String, SetCount> confirmedBySet) {
            this.totalCopies = totalCopies;
            this.confirmedCopies = confirmedCopies;
            this.unresolvedCopies = unresolvedCopies;
            this.confirmedDistinctPrintings = confirmedDistinctPrintings;
            this.unresolvedLines = Collections.unmodifiableList(new ArrayList<>(unresolvedLines));
            this.confirmedBySet = Collections.unmodifiableMap(new LinkedHashMap<>(confirmedBySet));
        }
    }

    private static boolean hasValue(String input) {
        return input != null && !input.trim().isEmpty();
    }

    private static boolean sameHint(String input, String canonical) {
        return !hasValue(input) || input.trim().equalsIgnoreCase(canonical.trim());
    }

    public static Result reconcile(List<PhysicalLine> lines, List<CatalogPrinting> printings) {
        if (lines == null || printings == null)
            throw new IllegalArgumentException("Inventory and catalog must not be null.");

        Map<UUID, CatalogPrinting> catalog = new HashMap<>();
        for (CatalogPrinting printing : printings) {
            if (printing == null || printing.printId == null || EMPTY_ID.equals(printing.printId) ||
                !hasValue(printing.name) || !hasValue(printing.setCode) || !hasValue(printing.collectorNumber))
                throw new IllegalArgumentException("Invalid catalog printing.");
            if (catalog.putIfAbsent(printing.printId, printing) != null)
                throw new IllegalArgumentException("Duplicate catalog printing ID.");
        }

        Set<UUID> lineIds = new HashSet<>();
        Set<UUID> seenPrintings = new HashSet<>();
        Map<String, Set<UUID>> uniqueBySet = new HashMap<>();
        Map<String, Long> copiesBySet = new HashMap<>();
        List<UnresolvedLine> unresolved = new ArrayList<>();
        long total = 0, confirmed = 0, unconfirmed = 0;

        for (PhysicalLine line : lines) {
            if (line == null || line.lineId == null || EMPTY_ID.equals(line.lineId) ||
                !hasValue(line.name) || !hasValue(line.finish) || line.quantity < 0 ||
                EMPTY_ID.equals(line.printId))
                throw new IllegalArgumentException("Invalid physical inventory line.");
            if (!lineIds.add(line.lineId))
                throw new IllegalArgumentException("Duplicate stable physical inventory line ID.");

            total = Math.addExact(total, line.quantity);
            if (line.quantity == 0) continue;

            UnresolvedReason reason = null;
            CatalogPrinting matched = null;
            if (line.printId == null)
                reason = UnresolvedReason.NoPrintId;
            else if ((matched = catalog.get(line.printId)) == null)
                reason = UnresolvedReason.PrintIdAbsentFromCatalog;
            else if (!sameHint(line.setCode, matched.setCode) ||
                     !sameHint(line.collectorNumber, matched.collectorNumber))
                reason = UnresolvedReason.SourceMetadataConflict;

            if (reason != null) {
                unconfirmed = Math.addExact(unconfirmed, line.quantity);
                unresolved.add(new UnresolvedLine(line.lineId, line.quantity, reason));
                continue;
            }

            String setCode = matched.setCode.trim().toLowerCase(Locale.ROOT);
            uniqueBySet.computeIfAbsent(setCode, ignored -> new HashSet<>()).add(matched.printId);
            copiesBySet.put(setCode,
                Math.addExact(copiesBySet.getOrDefault(setCode, 0L), line.quantity));
            seenPrintings.add(matched.printId);
            confirmed = Math.addExact(confirmed, line.quantity);
        }

        Map<String, SetCount> bySet = new TreeMap<>();
        for (Map.Entry<String, Set<UUID>> entry : uniqueBySet.entrySet()) {
            String set = entry.getKey();
            bySet.put(set, new SetCount(set, entry.getValue().size(), copiesBySet.get(set)));
        }

        return new Result(total, confirmed, unconfirmed, seenPrintings.size(), unresolved, bySet);
    }
}
