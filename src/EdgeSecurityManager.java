import com.sun.net.httpserver.HttpExchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IEC 62443 Industrial Cybersecurity, HMAC-SHA256 Command Authentication,
 * Role-Based Access Control (RBAC), Replay Attack Protection, and Token-Bucket Rate Limiter.
 *
 * Implements Defense-in-Depth for Edge Industrial Control Systems (IACS):
 * 1. Zero-Trust Cryptographic Command Signature:
 *    Header: X-AgriGuard-Role (VIEWER, OPERATOR, AGRONOMIST)
 *    Header: X-AgriGuard-Timestamp (Epoch ms)
 *    Header: X-AgriGuard-Signature = HMAC-SHA256(secret, role + ":" + timestamp + ":" + method + ":" + path + ":" + body)
 * 2. Replay-Attack Mitigation: Strict ±30-second timestamp validation window.
 * 3. Role-Based Access Control (RBAC):
 *    - VIEWER: Read-only access to telemetry, metrics, and audit ledger. Denied all mutations (403 Forbidden).
 *    - OPERATOR: Authorized for field diagnostics and actuator manual overrides. Denied agronomic crop setpoint changes (403 Forbidden).
 *    - AGRONOMIST: Full operational and agronomic crop profile authorization.
 * 4. Token-Bucket Rate Limiter: 30 req/sec burst limit per client IP, returning 429 Too Many Requests.
 */
public class EdgeSecurityManager {

    public static final String DEFAULT_AUTH_SECRET = "agriguard-edge-dev-key-2026";
    public static final long REPLAY_WINDOW_MS = 30_000L; // ±30 seconds

    public enum AuthMode {
        STRICT,      // Cryptographic signature and RBAC strictly enforced on all mutating endpoints
        PERMISSIVE   // Unsigned requests allowed (for legacy local tools), but if signed, strictly validated
    }

    public enum Role {
        VIEWER,
        OPERATOR,
        AGRONOMIST;

        public static Role fromString(String val) {
            if (val == null || val.isBlank()) return null;
            try {
                return Role.valueOf(val.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public boolean canModifyCropProfile() {
            return this == AGRONOMIST;
        }

        public boolean canOperateActuators() {
            return this == OPERATOR || this == AGRONOMIST;
        }

        public boolean canInjectDiagnostics() {
            return this == OPERATOR || this == AGRONOMIST;
        }
    }

    public record SecurityResult(
            boolean authorized,
            int statusCode,
            String errorMessage,
            Role role
    ) {
        public static SecurityResult allow(Role role) {
            return new SecurityResult(true, 200, null, role);
        }

        public static SecurityResult deny(int statusCode, String message) {
            return new SecurityResult(false, statusCode, message, null);
        }
    }

    // Lightweight In-Memory Token Bucket for per-IP rate limiting
    private static class TokenBucket {
        private final double capacity;
        private final double refillRatePerSec;
        private double tokens;
        private long lastRefillNanos;

        public TokenBucket(double capacity, double refillRatePerSec) {
            this.capacity = capacity;
            this.refillRatePerSec = refillRatePerSec;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        public synchronized boolean tryConsume() {
            long now = System.nanoTime();
            double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            tokens = Math.min(capacity, tokens + elapsedSec * refillRatePerSec);

            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    private final String authSecret;
    private final AuthMode authMode;
    private final Map<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    public EdgeSecurityManager() {
        this(DEFAULT_AUTH_SECRET, AuthMode.STRICT);
    }

    public EdgeSecurityManager(String authSecret, AuthMode authMode) {
        this.authSecret = (authSecret != null && !authSecret.isBlank()) ? authSecret : DEFAULT_AUTH_SECRET;
        this.authMode = (authMode != null) ? authMode : AuthMode.STRICT;
    }

    public String getAuthSecret() {
        return authSecret;
    }

    public AuthMode getAuthMode() {
        return authMode;
    }

    /**
     * Checks per-IP Token Bucket rate limit.
     * Allows up to 30 requests per second burst.
     */
    public boolean checkRateLimit(String clientIp) {
        String ipKey = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
        TokenBucket bucket = ipBuckets.computeIfAbsent(ipKey, k -> new TokenBucket(30.0, 30.0));
        return bucket.tryConsume();
    }

    /**
     * Computes HMAC-SHA256 signature formatted as a 64-character lowercase hexadecimal string.
     */
    public static String computeHmacSha256(String key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(64);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 cryptographic failure", e);
        }
    }

    /**
     * Validates incoming HTTP request against IEC 62443 HMAC-SHA256 signature and RBAC rules.
     *
     * @param exchange HTTP request exchange
     * @param body Raw request body string
     * @param actionTarget Target resource ("PROFILE", "ACTUATOR", "DIAGNOSTICS")
     * @return SecurityResult indicating whether request is authorized
     */
    public SecurityResult authenticateAndAuthorize(
            HttpExchange exchange,
            String body,
            String actionTarget) {

        String clientIp = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "127.0.0.1";

        String roleHeader = exchange.getRequestHeaders().getFirst("X-AgriGuard-Role");
        String timestampHeader = exchange.getRequestHeaders().getFirst("X-AgriGuard-Timestamp");
        String signatureHeader = exchange.getRequestHeaders().getFirst("X-AgriGuard-Signature");
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        return authenticateAndAuthorize(clientIp, method, path, body, roleHeader, timestampHeader, signatureHeader, actionTarget);
    }

    /**
     * Core verification engine for IEC 62443 HMAC-SHA256 signature and RBAC rules.
     */
    public SecurityResult authenticateAndAuthorize(
            String clientIp,
            String method,
            String path,
            String body,
            String roleHeader,
            String timestampHeader,
            String signatureHeader,
            String actionTarget) {

        // 1. Rate Limiting Check (30 req/sec)
        if (!checkRateLimit(clientIp)) {
            return SecurityResult.deny(429, "Rate limit exceeded (Maximum 30 requests/second burst per client IP)");
        }

        // 2. In permissive mode, unauthenticated requests can pass as default OPERATOR
        if (authMode == AuthMode.PERMISSIVE && (roleHeader == null || signatureHeader == null)) {
            return SecurityResult.allow(Role.OPERATOR);
        }

        // 3. Strict Mode Authentication Validation
        if (roleHeader == null || roleHeader.isBlank()) {
            return SecurityResult.deny(401, "Missing 'X-AgriGuard-Role' security header. Required: VIEWER, OPERATOR, AGRONOMIST");
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return SecurityResult.deny(401, "Missing 'X-AgriGuard-Timestamp' header for replay protection");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return SecurityResult.deny(401, "Missing 'X-AgriGuard-Signature' (HMAC-SHA256 digest)");
        }

        Role role = Role.fromString(roleHeader);
        if (role == null) {
            return SecurityResult.deny(401, String.format(Locale.US,
                "Invalid role '%s'. Allowed roles: VIEWER, OPERATOR, AGRONOMIST", roleHeader));
        }

        // 4. Replay-Attack Mitigation (±30s window)
        long requestTimeMs;
        try {
            requestTimeMs = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return SecurityResult.deny(401, "Malformed 'X-AgriGuard-Timestamp' (expected epoch milliseconds)");
        }

        long now = System.currentTimeMillis();
        long driftMs = Math.abs(now - requestTimeMs);
        if (driftMs > REPLAY_WINDOW_MS) {
            return SecurityResult.deny(401, String.format(Locale.US,
                "Request timestamp expired (Replay window is ±%d ms; received drift was %d ms)",
                REPLAY_WINDOW_MS, driftMs));
        }

        // 5. Cryptographic HMAC-SHA256 Signature Verification
        // Canonical string: role + ":" + timestamp + ":" + method + ":" + path + ":" + body
        String normMethod = (method != null ? method : "POST").toUpperCase();
        String normPath = (path != null ? path : "");
        String canonicalPayload = String.format("%s:%s:%s:%s:%s",
                role.name(),
                timestampHeader.trim(),
                normMethod,
                normPath,
                body != null ? body : "");

        String expectedSignature = computeHmacSha256(authSecret, canonicalPayload);

        // Constant-time comparison to prevent timing side-channel attacks
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = signatureHeader.trim().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            return SecurityResult.deny(401, "Invalid cryptographic signature (HMAC-SHA256 verification failed)");
        }

        // 6. Role-Based Access Control (RBAC) Enforcement
        if (role == Role.VIEWER) {
            return SecurityResult.deny(403, "Access Denied: Role 'VIEWER' is read-only. Mutation operations forbidden.");
        }

        if ("PROFILE".equalsIgnoreCase(actionTarget) && !role.canModifyCropProfile()) {
            return SecurityResult.deny(403, String.format(Locale.US,
                "Access Denied: Role '%s' cannot alter agronomic crop profiles. Requires 'AGRONOMIST'.", role.name()));
        }

        if ("ACTUATOR".equalsIgnoreCase(actionTarget) && !role.canOperateActuators()) {
            return SecurityResult.deny(403, String.format(Locale.US,
                "Access Denied: Role '%s' cannot control field actuators.", role.name()));
        }

        if ("DIAGNOSTICS".equalsIgnoreCase(actionTarget) && !role.canInjectDiagnostics()) {
            return SecurityResult.deny(403, String.format(Locale.US,
                "Access Denied: Role '%s' cannot trigger field diagnostic faults.", role.name()));
        }

        return SecurityResult.allow(role);
    }
}
