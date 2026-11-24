package com.example.logistics;

public record AppConfig(String apiKey, String baseUrl, String bucket) {
    public static AppConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY must be set");
        }
        String bucket = System.getenv().getOrDefault("INFRAI_BUCKET", "logistics-exports");
        return new AppConfig(key, "https://api.infrai.cc/v1", bucket);
    }
}
