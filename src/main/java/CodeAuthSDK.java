package CodeAuthSDK;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public final class CodeAuth {

    private static String Endpoint;
    private static String ProjectID;
    private static boolean UseCache;
    private static boolean HasInitialized = false;

    // session cache
    private static final ConcurrentHashMap<String, SessionCacheData> sessionCache = new ConcurrentHashMap<>();

    private static Thread cacheThread = null;

    // --- Internal classes ---
    private static final class SessionCacheData {
        String email;
        long expiration;
        int refreshLeft;

        SessionCacheData(String email, long expiration, int refreshLeft) {
            this.email = email;
            this.expiration = expiration;
            this.refreshLeft = refreshLeft;
        }
    }

    // --- Public result classes  ---

    /**
     * Result of signin email
     */
    public static class SignInEmailResult {
        public String error;
    }

    /**
     * Result of signin verify
     */
    public static class SignInEmailVerifyResult {
        public String session_token;
        public String email;
        public long expiration;
        public int refresh_left;
        public String error;
    }

    /**
     * Result of signin social
     */
    public static class SignInSocialResult {
        public String signin_url;
        public String error;
    }

    /**
     * Result of signin social verify
     */
    public static class SignInSocialVerifyResult {
        public String session_token;
        public String email;
        public long expiration;
        public int refresh_left;
        public String error;
    }

    /**
     * Result of session info
     */
    public static class SessionInfoResult {
        public String email;
        public long expiration;
        public int refresh_left;
        public String error;
    }

    /**
     * Result of session refresh
     */
    public static class SessionRefreshResult {
        public String session_token;
        public String email;
        public long expiration;
        public int refresh_left;
        public String error;
    }

    /**
     * Result of session invalidate
     */
    public static class SessionInvalidateResult {
        public String error;
    }

    // -------------------------
    // Initialization
    // -------------------------
    /**
     * Initialize the CodeAuth SDK
     *
     * @param project_endpoint The endpoint of your project. This can be found inside your project settings.
     * @param project_id Your project ID. This can be found inside your project settings.
     * @param use_cache Whether to use cache or not. Using cache can help speed up response time and mitigate some rate limits. This will automatically cache new session token (from '/signin/emailverify', 'signin/socialverify', 'session/info', 'session/refresh') and automatically delete cache when it is invalidated (from 'session/refresh', 'session/invalidate').
     * @param cache_duration How long the cache should last. At least 15 seconds required to effectively mitigate most rate limits. Check docs for more info.
     */
    public static synchronized void Initialize(String project_endpoint, String project_id, boolean use_cache, int cache_duration) {
        if (HasInitialized) throw new RuntimeException("CodeAuth has already been initialized");
        HasInitialized = true;
        Endpoint = project_endpoint;
        ProjectID = project_id;
        UseCache = use_cache;

        if (use_cache) startBackgroundCache(cache_duration);
    }

    public static synchronized void Initialize(String project_endpoint, String project_id) {
        Initialize(project_endpoint, project_id, true, 30);
    }

    // -------
    // Background worker for session cache
    // -------
    private static void startBackgroundCache(final int cacheDurationSeconds) {
        cacheThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(Math.max(1, cacheDurationSeconds) * 1000L);
                    sessionCache.clear();
                }
            } catch (InterruptedException e) {
                // thread interrupted: exit
            }
        });
        cacheThread.setDaemon(true);
        cacheThread.start();
    }

    // -------
    // Makes sure that the CodeAuth SDK has been initialized
    // -------
    private static void ensureInitialized() {
        if (!HasInitialized) throw new RuntimeException("CodeAuth has not been initialized");
    }

    // -------------------------
    // HTTP helper (synchronous)
    // -------------------------
    private static HttpResponse callApiRequest(String path, String jsonBody) throws Exception {
        URL url = new URL("https://" + Endpoint + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(payload.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
            os.flush();
        }

        int status = conn.getResponseCode();
        BufferedReader reader;
        if (status >= 200 && status <= 299) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            // read error stream if any
            try {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            }
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while (reader != null && (line = reader.readLine()) != null) {
            sb.append(line);
        }
        if (reader != null) reader.close();
        conn.disconnect();

        return new HttpResponse(status, sb.toString());
    }

    private static final class HttpResponse {
        int statusCode;
        String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body != null ? body : "";
        }
    }

    /**
     * Begins the sign in or register flow by sending the user a one time code via email
     * @param email The email of the user you are trying to sign in/up. Email must be between 1 and 64 characters long. The email must also only contain letter, number, dot (not first, last, or consecutive), underscore (not first or last) and/or hyphen (not first or last).
     * @return
     */
    public static SignInEmailResult SignInEmail(String email) {
        ensureInitialized();
        try {
            String body = "{\"project_id\":\"" + escapeJson(ProjectID) + "\",\"email\":\"" + escapeJson(email) + "\"}";
            HttpResponse response = callApiRequest("/signin/email", body);

            if (response.statusCode == 200) {
                SignInEmailResult r = new SignInEmailResult();
                r.error = "no_error";
                return r;
            } else if (response.statusCode == 400) {
                String err = JsonHelper.getString(response.body, "error");
                SignInEmailResult r = new SignInEmailResult();
                r.error = err;
                return r;
            } else {
                SignInEmailResult r = new SignInEmailResult();
                r.error = "connection_error";
                return r;
            }
        } catch (Exception ex) {
            SignInEmailResult r = new SignInEmailResult();
            r.error = "connection_error";
            return r;
        }
    }

    /**
     * Checks if the one time code matches in order to create a session token.
     * @param email The email of the user you are trying to sign in/up. Email must be between 1 and 64 characters long. The email must also only contain letter, number, dot (not first, last, or consecutive), underscore(not first or last) and/or hyphen(not first or last).
     * @param code The one time code that was sent to the email.
     * @return
     */
    public static SignInEmailVerifyResult SignInEmailVerify(String email, String code) {
        ensureInitialized();
        try {
            String body = "{\"project_id\":\"" + escapeJson(ProjectID) + "\",\"email\":\"" + escapeJson(email) + "\",\"code\":\"" + escapeJson(code) + "\"}";
            HttpResponse response = callApiRequest("/signin/emailverify", body);

            if (response.statusCode == 200) {
                String sessionToken = JsonHelper.getString(response.body, "session_token");
                String respEmail = JsonHelper.getString(response.body, "email");
                long expiration = JsonHelper.getLong(response.body, "expiration");
                int refreshLeft = JsonHelper.getInt(response.body, "refresh_left");

                if (UseCache && sessionToken != null) {
                    sessionCache.putIfAbsent(sessionToken, new SessionCacheData(respEmail, expiration, refreshLeft));
                }

                SignInEmailVerifyResult r = new SignInEmailVerifyResult();
                r.session_token = sessionToken;
                r.email = respEmail;
                r.expiration = expiration;
                r.refresh_left = refreshLeft;
                r.error = "no_error";
                return r;
            } else if (response.statusCode == 400) {
                SignInEmailVerifyResult r = new SignInEmailVerifyResult();
                r.error = JsonHelper.getString(response.body, "error");
                return r;
            } else {
                SignInEmailVerifyResult r = new SignInEmailVerifyResult();
                r.error = "connection_error";
                return r;
            }
        } catch (Exception ex) {
            SignInEmailVerifyResult r = new SignInEmailVerifyResult();
            r.error = "connection_error";
            return r;
        }
    }

    /**
     * Begins the sign in or register flow by allowing users to sign in through a social OAuth2 link.
     * @param social_type The type of social OAuth2 url you are trying to create. Possible social types: "google", "microsoft", "apple"
     * @return
     */
    public static SignInSocialResult SignInSocial(String social_type) {
        ensureInitialized();
        try {
            String body = "{\"project_id\":\"" + escapeJson(ProjectID) + "\",\"social_type\":\"" + escapeJson(social_type) + "\"}";
            HttpResponse response = callApiRequest("/signin/social", body);

            if (response.statusCode == 200) {
                String signinUrl = JsonHelper.getString(response.body, "signin_url");
                SignInSocialResult r = new SignInSocialResult();
                r.signin_url = signinUrl;
                r.error = "no_error";
                return r;
            } else if (response.statusCode == 400) {
                SignInSocialResult r = new SignInSocialResult();
                r.error = JsonHelper.getString(response.body, "error");
                return r;
            } else {
                SignInSocialResult r = new SignInSocialResult();
                r.error = "connection_error";
                return r;
            }
        } catch (Exception ex) {
            SignInSocialResult r = new SignInSocialResult();
            r.error = "connection_error";
            return r;
        }
    }

    /**
     * This is the next step after the user signs in with their social account. This request checks the authorization code given by the social media company in order to create a session token.
     * @param social_type The type of social OAuth2 url you are trying to verify
     * @param authorization_code The authorization code given by the social. Please read the doc for more info.
     * @return
     */
    public static SignInSocialVerifyResult SignInSocialVerify(String social_type, String authorization_code) {
        ensureInitialized();
        try {
            String body = "{\"project_id\":\"" + escapeJson(ProjectID) + "\",\"social_type\":\"" + escapeJson(social_type) + "\",\"authorization_code\":\"" + escapeJson(authorization_code) + "\"}";
            HttpResponse response = callApiRequest("/signin/socialverify", body);

            if (response.statusCode == 200) {
                String sessionToken = JsonHelper.getString(response.body, "session_token");
                String respEmail = JsonHelper.getString(response.body, "email");
                long expiration = JsonHelper.getLong(response.body, "expiration");
                int refreshLeft = JsonHelper.getInt(response.body, "refresh_left");

                if (UseCache && sessionToken != null) {
                    sessionCache.putIfAbsent(sessionToken, new SessionCacheData(respEmail, expiration, refreshLeft));
                }

                SignInSocialVerifyResult r = new SignInSocialVerifyResult();
                r.session_token = sessionToken;
                r.email = respEmail;
                r.expiration = expiration;
                r.refresh_left = refreshLeft;
                r.error = "no_error";
                return r;
            } else if (response.statusCode == 400) {
                SignInSocialVerifyResult r = new SignInSocialVerifyResult();
                r.error = JsonHelper.getString(response.body, "error");
                return r;
            } else {
                SignInSocialVerifyResult r = new SignInSocialVerifyResult();
                r.error = "connection_error";
                return r;
            }
        } catch (Exception ex) {
            SignInSocialVerifyResult r = new SignInSocialVerifyResult();
            r.error = "connection_error";
            return r;
        }
    }

    /**
     * Gets the information associated with a session token
     * @param session_token The session token you are trying to get information on
     * @return
     */
    public static SessionInfoResult SessionInfo(String session_token) {
        ensureInitialized();

        // try cache first
        if (UseCache) {
            SessionCacheData cached = sessionCache.get(session_token);
            if (cached != null) {
                SessionInfoResult r = new SessionInfoResult();
                r.email = cached.email;
                r.expiration = cached.expiration;
                r.refresh_left = cached.refreshLeft;
                r.error = "no_error";
                return r;
            }
        }

        try {
            String body = "{\"project_id\":\"" + escapeJson(ProjectID) + "\",\"session_token\":\"" + escapeJson(session_token) + "\"}";
            HttpResponse response = callApiRequest("/session/info", body);

            if (response.statusCode == 200) {
                String respEmail = JsonHelper.getString(response.body, "email");
                long expiration = JsonHelper.getLong(response.body, "expiration");
                int refreshLeft = JsonHelper.getInt(response.body, "refresh_left");

                if (UseCache) {
                    sessionCache.putIfAbsent(session_token, new SessionCacheData(respEmail, expiration, refreshLeft));
                }

                SessionInfoResult r = new SessionInfoResult();
                r.email = respEmail;
                r.expiration = expiration;
                r.refresh_left = refreshLeft;
                r.error = "no_error";
                return r;
            } else if (response.statusCode == 400) {
                SessionInfoResult r = new SessionInfoResult();
                r.error = JsonHelper.getString(response.body, "error");
                return r;
            } else {
                SessionInfoResult r = new SessionInfoResult();
                r.error = "connection_error";
                return r;
            }
        } catch (Exception ex) {
            SessionInfoResult r = new SessionInfoResult();
            r.error = "connection_error";
            return r;
        }
    }

    /**
     * Create a new session token using existing session token
     * @param session_token The session token you are trying to use to create a new token
     * @return
     */
    public static SessionRefreshResult SessionRefresh(String session_token) {
        ensureInitialized();
        try {
            String body = "{\"project_id\":\"" + escapeJson(ProjectID) + "\",\"session_token\":\"" + escapeJson(session_token) + "\"}";
            HttpResponse response = callApiRequest("/session/refresh", body);

            if (response.statusCode == 200) {
                String newToken = JsonHelper.getString(response.body, "session_token");
                String respEmail = JsonHelper.getString(response.body, "email");
                long expiration = JsonHelper.getLong(response.body, "expiration");
                int refreshLeft = JsonHelper.getInt(response.body, "refresh_left");

                if (UseCache) {
                    sessionCache.remove(session_token);
                    if (newToken != null) {
                        sessionCache.putIfAbsent(newToken, new SessionCacheData(respEmail, expiration, refreshLeft));
                    }
                }

                SessionRefreshResult r = new SessionRefreshResult();
                r.session_token = newToken;
                r.email = respEmail;
                r.expiration = expiration;
                r.refresh_left = refreshLeft;
                r.error = "no_error";
                return r;
            } else if (response.statusCode == 400) {
                SessionRefreshResult r = new SessionRefreshResult();
                r.error = JsonHelper.getString(response.body, "error");
                return r;
            } else {
                SessionRefreshResult r = new SessionRefreshResult();
                r.error = "connection_error";
                return r;
            }
        } catch (Exception ex) {
            SessionRefreshResult r = new SessionRefreshResult();
            r.error = "connection_error";
            return r;
        }
    }

    /**
     * Invalidate a session token. By doing so, the session token can no longer be used for any api call.
     * @param session_token The session token you are trying to use to invalidate
     * @param invalidate_type How to use the session token to invalidate. Possible invalidate types: "only_this", "all", "all_but_this"
     * @return
     */
    public static SessionInvalidateResult SessionInvalidate(String session_token, String invalidate_type) {
        ensureInitialized();
        try {
            String body = "{\"project_id\":\"" + escapeJson(ProjectID) + "\",\"session_token\":\"" + escapeJson(session_token) + "\",\"invalidate_type\":\"" + escapeJson(invalidate_type) + "\"}";
            HttpResponse response = callApiRequest("/session/invalidate", body);

            if (response.statusCode == 200) {
                if (UseCache) sessionCache.remove(session_token);
                SessionInvalidateResult r = new SessionInvalidateResult();
                r.error = "no_error";
                return r;
            } else if (response.statusCode == 400) {
                SessionInvalidateResult r = new SessionInvalidateResult();
                r.error = JsonHelper.getString(response.body, "error");
                return r;
            } else {
                SessionInvalidateResult r = new SessionInvalidateResult();
                r.error = "connection_error";
                return r;
            }
        } catch (Exception ex) {
            SessionInvalidateResult r = new SessionInvalidateResult();
            r.error = "connection_error";
            return r;
        }
    }

    // -------------------------
    // Small JSON helpers (naive)
    // -------------------------
    private static final class JsonHelper {
        // Get string value for a top-level key, or null if not present
        static String getString(String json, String key) {
            if (json == null || key == null) return null;
            String pattern = "\"" + key + "\"";
            int idx = json.indexOf(pattern);
            if (idx == -1) return null;
            int colon = json.indexOf(':', idx + pattern.length());
            if (colon == -1) return null;

            int start = json.indexOf('"', colon + 1);
            if (start == -1) return null;
            int end = json.indexOf('"', start + 1);
            if (end == -1) return null;
            return unescapeJson(json.substring(start + 1, end));
        }

        // Get long (number) value
        static long getLong(String json, String key) {
            String raw = getRawToken(json, key);
            if (raw == null) return 0L;
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }

        // Get int value
        static int getInt(String json, String key) {
            String raw = getRawToken(json, key);
            if (raw == null) return 0;
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Return the raw token (number or unquoted value) after colon
        private static String getRawToken(String json, String key) {
            if (json == null || key == null) return null;
            String pattern = "\"" + key + "\"";
            int idx = json.indexOf(pattern);
            if (idx == -1) return null;
            int colon = json.indexOf(':', idx + pattern.length());
            if (colon == -1) return null;

            int i = colon + 1;
            // skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) return null;

            // if value is quoted string, return without quotes
            if (json.charAt(i) == '"') {
                int start = i + 1;
                int end = json.indexOf('"', start);
                if (end == -1) return null;
                return unescapeJson(json.substring(start, end));
            } else {
                // read until comma or closing brace
                int j = i;
                while (j < json.length() && json.charAt(j) != ',' && json.charAt(j) != '}' && !Character.isWhitespace(json.charAt(j))) j++;
                if (j <= i) return null;
                return json.substring(i, j).trim();
            }
        }
    }

    // escape JSON string simple
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // -------------------------
    // End
    // -------------------------
}
