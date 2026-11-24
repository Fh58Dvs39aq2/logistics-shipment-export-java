package com.example.logistics;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public final class ShipmentExportService {
    public record Event(String shipmentId, Instant occurredAt, String status, String detail) {}
    public record Proof(String shipmentId, String objectKey) {}
    public record ExceptionNote(String shipmentId, String reason) {}
    public record ShipmentReport(List<Event> events, List<Proof> proofs, List<ExceptionNote> exceptions) {}
    public record ExportResult(String objectKey, String downloadUrl, String pdfEnvelope, String csv) {}

    private final InfraiClient infrai;
    public ShipmentExportService(InfraiClient infrai) { this.infrai = infrai; }

    public String csvFor(ShipmentReport report) {
        StringBuilder out = new StringBuilder("shipment_id,occurred_at,status,detail,proof_key,exception\n");
        for (Event event : report.events()) {
            String proof = report.proofs().stream().filter(p -> p.shipmentId().equals(event.shipmentId())).map(Proof::objectKey).findFirst().orElse("");
            String exception = report.exceptions().stream().filter(e -> e.shipmentId().equals(event.shipmentId())).map(ExceptionNote::reason).findFirst().orElse("");
            out.append(row(event.shipmentId())).append(row(event.occurredAt().toString())).append(row(event.status())).append(row(event.detail())).append(row(proof)).append(row(exception)).append('\n');
        }
        return out.toString();
    }

    public ExportResult export(ShipmentReport report) throws IOException, InterruptedException {
        String csv = csvFor(report);
        String key = "exports/shipment-" + report.events().get(0).shipmentId() + ".csv";
        infrai.createBucket();
        infrai.uploadObject(key, csv);
        if (!infrai.objectFound(key)) {
            throw new IllegalStateException("Uploaded object was not found: " + key);
        }
        infrai.listObjectItems();
        String url = infrai.presignDownload(key);
        String pdf = infrai.generatePdf("<h1>Shipment " + report.events().get(0).shipmentId() + "</h1><pre>" + csv + "</pre>");
        return new ExportResult(key, url, pdf, csv);
    }

    private static String row(String value) { return "\"" + value.replace("\"", "\"\"") + "\","; }
}
