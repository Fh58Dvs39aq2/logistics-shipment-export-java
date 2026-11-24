package com.example.logistics;

import java.time.Instant;
import java.util.List;

public final class ExportDecisionTest {
    public static void main(String[] args) {
        var report = new ShipmentExportService.ShipmentReport(
                List.of(new ShipmentExportService.Event("SHP-7", Instant.parse("2026-01-01T00:00:00Z"), "EXCEPTION", "Address check")),
                List.of(new ShipmentExportService.Proof("SHP-7", "proof/SHP-7.pdf")),
                List.of(new ShipmentExportService.ExceptionNote("SHP-7", "Address check")));
        String csv = new ShipmentExportService(null).csvFor(report);
        if (!csv.contains("EXCEPTION") || !csv.contains("Address check") || !csv.contains("proof/SHP-7.pdf")) {
            throw new AssertionError("exception export decision was not represented in the CSV");
        }
        System.out.println("Export decision test passed");
    }
}
