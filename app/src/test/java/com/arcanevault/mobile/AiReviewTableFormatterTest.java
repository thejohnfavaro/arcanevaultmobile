package com.arcanevault.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AiReviewTableFormatterTest {
    @Test public void preservesFourthColumnReasonAndCardReferences() {
        String[] headers=AiReviewTableFormatter.cells("| Priority | Cut | Add | Why |");
        String[] values=AiReviewTableFormatter.cells(
            "| 1 | [[card:Deserted Temple]] | [[card:Godless Shrine]] | Stabilizes early mana and reduces color screw. |");
        String output=AiReviewTableFormatter.row(headers,values,1);
        assertTrue(output.contains("PRIORITY: 1"));
        assertTrue(output.contains("⊖ [[card:Deserted Temple]]"));
        assertTrue(output.contains("⊕ [[card:Godless Shrine]]"));
        assertTrue(output.contains("WHY: Stabilizes early mana and reduces color screw."));
    }

    @Test public void preservesAdditionalColumnsWithoutLosingExplanations() {
        String[] headers=AiReviewTableFormatter.cells("| Cut | Replacement | Role | Reason | Ownership |");
        String[] values=AiReviewTableFormatter.cells(
            "| [[card:Fear of Immobility]] | [[card:Get Lost]] | Removal | More flexible answer | Unknown |");
        String output=AiReviewTableFormatter.row(headers,values,2);
        assertTrue(output.contains("⊖ [[card:Fear of Immobility]]"));
        assertTrue(output.contains("⊕ [[card:Get Lost]]"));
        assertTrue(output.contains("ROLE: Removal"));
        assertTrue(output.contains("WHY: More flexible answer"));
        assertTrue(output.contains("OWNERSHIP: Unknown"));
    }

    @Test public void detectsMarkdownTableSeparators() {
        assertTrue(AiReviewTableFormatter.separator("| --- | :---: | ---: |"));
        assertFalse(AiReviewTableFormatter.separator("| 1 | Out | In | Why |"));
    }
}
