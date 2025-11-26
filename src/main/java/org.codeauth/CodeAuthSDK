package org.codeauth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CodeAuthSDK {

    private static String endpoint;
    private static String projectId;

    private static boolean useCache;
    private static long cacheDurationMs;

    private static Map<String, Map<String, String>> cacheSession = new HashMap<>();
    private static long cacheTimestamp;

    private static boolean initialized = false;

    private static final HttpClient http = HttpClient.newHttpClient();

    // -----------------------------
    // Initialization
    // -----------------------------
    public static void Initialize(String projectEndpoint, String projectID, boolean useCacheOption, int cacheSeconds) {
        if (initialized) throw new RuntimeException("CodeAuth has already been initialized.");
        initialized = true;

        endpoint = projectEndpoint;
        projectId = projectID;
        useCache = useCacheOption;
        cacheDurationMs = cacheSeconds * 1000L;
        cacheTimestamp = Instant.now().toEpochMilli();
    }


    // -----------------------------
    // SignIn Email
    // -----------------------------
    public static Map<String, String> SignInEmail(String email) {
        ensureInit();
        return request(
                "https://" + endpoint + "/signin/email",
                jsonObj(
                        "project_id", projectId,
                        "email", email
                )
        );
    }

    // -----------------------------
    // Email Verify
    // -----------------------------
    public static Map<String, String> SignInEmailVerify(String email, String code) {
        ensureInit();
        return request(
                "https://" + endpoint + "/signin/emailverify",
                jsonObj(
                        "project_id", projectId,
                        "email", email,
                        "code", code
                )
        );
    }


    // -----------------------------
    // Social SignIn
    // -----------------------------
    public static Map<String, String> SignInSocial(String socialType) {
        ensureInit();
        return request(
                "https://" + endpoint + "/signin/social",
                jsonObj(
                        "project_id", projectId,
                        "social_type", socialType
                )
        );
    }


    // -----------------------------
    // Social Verify
    // -----------------------------
    public static Map<String, String> SignInSocialVerify(String socialType, String authorizationCode) {
        ensureInit();
        return request(
                "https://" + endpoint + "/signin/socialverify",
                jsonObj(
                        "project_id", projectId,
                        "social_type", socialType,
                        "authorization_code", authorizationCode
                )
        );
    }


    // -----------------------------
    // Session Info (cached)
    // -----------------------------
    public static Map<String, String> SessionInfo(String sessionToken) {
        ensureInit();

        long now = Instant.now().toEpochMilli();

        if (useCache) {
            if (cacheTimestamp + cacheDurationMs > now) {
                if (cacheSession.containsKey(sessionToken)) {
                    return cacheSession.get(sessionToken);
                }
            } else {
                cacheTimestamp = now;
                cacheSession.clear();
            }
        }

        Map<String, String> result = request(
                "https://" + endpoint + "/session/info",
                jsonObj(
                        "project_id", projectId,
                        "session_token", sessionToken
                )
        );

        if (useCache) {
            cacheSession.put(sessionToken, result);
        }

        return result;
    }


    // -----------------------------
    // Session Refresh
    // -----------------------------
    public static Map<String, String> SessionRefresh(String sessionToken) {
        ensureInit();

        Map<String, String> result = request(
                "https://" + endpoint + "/session/refresh",
                jsonObj(
                        "project_id", projectId,
                        "session_token", sessionToken
                )
        );

        long now = Instant.now().toEpochMilli();
        String error = result.getOrDefault("error", "");

        if (useCache && !error.equals("no_error")) {
            if (cacheTimestamp + cacheDurationMs < now) {
                cacheTimestamp = now;
                cacheSession.clear();
            } else {
                cacheSession.remove(sessionToken);

                if (result.containsKey("session_token")) {
                    Map<String, String> newEntry = new HashMap<>();
                    newEntry.put("email", result.get("email"));
                    newEntry.put("expiration", result.get("expiration"));
                    newEntry.put("refresh_left", result.get("refresh_left"));

                    cacheSession.put(result.get("session_token"), newEntry);
                }
            }
        }

        return result;
    }


    // -----------------------------
    // Session Invalidate
    // -----------------------------
    public static Map<String, String> SessionInvalidate(String sessionToken, String invalidateType) {
        ensureInit();

        Map<String, String> result = request(
                "https://" + endpoint + "/session/invalidate",
                jsonObj(
                        "project_id", projectId,
                        "session_token", sessionToken,
                        "invalidate_type", invalidateType
                )
        );

        long now = Instant.now().toEpochMilli();

        if (useCache) {
            if (cacheTimestamp + cacheDurationMs < now) {
                cacheTimestamp = now;
                cacheSession.clear();
            } else {
                cacheSession.remove(sessionToken);
            }
        }

        return result;
    }


    // ======================================================
    //  HTTP Request
    // ======================================================
    private static Map<String, String> request(String url, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            Map<String, String> parsed = parseJson(res.body());
            if (res.statusCode() == 200) parsed.put("error", "no_error");

            return parsed;

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "connection_error");
            return error;
        }
    }


    // ======================================================
    //  Minimal JSON Builder
    // ======================================================
    private static String jsonObj(String... kv) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(kv[i]).append("\":");
            sb.append("\"").append(escape(kv[i + 1])).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    // ======================================================
    //  Minimal JSON Parser (string → map)
    // ======================================================
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();

        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        String[] parts = json.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String part : parts) {
            String[] kv = part.split(":(?=([^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (kv.length != 2) continue;

            String key = kv[0].trim().replace("\"", "");
            String value = kv[1].trim();

            if (value.startsWith("\"")) value = value.substring(1);
            if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);

            map.put(key, value);
        }

        return map;
    }


    private static void ensureInit() {
        if (!initialized) throw new RuntimeException("CodeAuth has not been initialized.");
    }
}
