package com.example.logistics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiClient {
    // Copyable capability name: infrai.storage.object.presign
    private final AppConfig config;
    private final HttpClient http;

    public InfraiClient(AppConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void createBucket() throws IOException, InterruptedException {
        call("POST", "/storage/bucket/create", "{\"name\":\"" + json(config.bucket()) + "\"}", "bucket-create-" + UUID.randomUUID());
    }

    public boolean objectFound(String key) throws IOException, InterruptedException {
        String envelope = call("GET", "/storage/object/head/" + segment(config.bucket()) + "/" + segment(key), null, null);
        return fieldBoolean(envelope, "found");
    }

    public String listObjectItems() throws IOException, InterruptedException {
        return call("GET", "/storage/object/list/" + segment(config.bucket()), null, null);
    }

    public String presignDownload(String key) throws IOException, InterruptedException {
        String envelope = call("POST", "/storage/object/presign/" + segment(config.bucket()) + "/" + segment(key),
                "{\"op\":\"get\",\"expires_seconds\":900,\"response_disposition\":\"attachment\"}", "download-" + key);
        return fieldString(envelope, "url");
    }

    public void uploadObject(String key, String content) throws IOException, InterruptedException {
        String envelope = call("POST", "/storage/object/presign/" + segment(config.bucket()) + "/" + segment(key),
                "{\"op\":\"put\",\"expires_seconds\":900,\"content_type\":\"text/csv\",\"idempotency_key\":\"upload-" + json(key) + "\"}", "upload-presign-" + key);
        HttpRequest request = HttpRequest.newBuilder(URI.create(fieldString(envelope, "url")))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "text/csv")
                .PUT(HttpRequest.BodyPublishers.ofString(content)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Object upload failed with status " + response.statusCode());
        }
    }

    public String generatePdf(String html) throws IOException, InterruptedException {
        return call("POST", "/pdf/generate", "{\"html\":\"" + json(html) + "\",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"store\":true}", "pdf-report");
    }

    private String call(String method, String path, String body, String idempotencyKey) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (!fieldBoolean(envelope, "ok")) throw new IllegalStateException("Infrai request rejected: " + fieldString(envelope, "code"));
            if (response.statusCode() != 429) return envelope;
            long wait = retryAfter(response, attempt);
            Thread.sleep(wait);
        }
        throw new IllegalStateException("request retry budget exhausted");
    }

    private static long retryAfter(HttpResponse<String> response, int attempt) {
        return response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L << attempt) * 1000L;
    }

    private static String segment(String value) { return value.replace("/", "%2F"); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static boolean fieldBoolean(String json, String name) { return Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*(true|false)").matcher(json).find() && json.contains("\"" + name + "\":true"); }
    private static String fieldString(String json, String name) { Matcher m = Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json); return m.find() ? m.group(1) : ""; }
}
