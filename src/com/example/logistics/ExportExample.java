package com.example.logistics;

import java.time.Instant;
import java.util.List;

public final class ExportExample {
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        InfraiClient infrai = new InfraiClient(config);
        ShipmentExportService service = new ShipmentExportService(infrai);
        var report = new ShipmentExportService.ShipmentReport(
                List.of(new ShipmentExportService.Event("SHP-1042", Instant.parse("2026-10-23T08:00:00Z"), "DELIVERED", "Dock 3"),
                        new ShipmentExportService.Event("SHP-1042", Instant.parse("2026-10-23T08:05:00Z"), "EXCEPTION", "Recipient unavailable")),
                List.of(new ShipmentExportService.Proof("SHP-1042", "proof/SHP-1042.jpg")),
                List.of(new ShipmentExportService.ExceptionNote("SHP-1042", "Recipient unavailable")));
        var result = service.export(report);
        System.out.println("CSV key: " + result.objectKey());
        System.out.println("Download link: " + result.downloadUrl());
        System.out.println("PDF response: " + result.pdfEnvelope());
    }
}
