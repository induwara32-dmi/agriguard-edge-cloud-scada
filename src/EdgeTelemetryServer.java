import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embedded Edge REST, SSE, Backfill Sync & Prometheus Telemetry Gateway.
 * Provides standards-compliant industrial IoT edge interfaces using pure Java standard library:
 * - GET  /api/v1/status           : Full JSON system state snapshot (including Zone Identity & Agronomy)
 * - GET  /api/v1/telemetry/stream : Server-Sent Events (SSE) live push stream
 * - GET  /api/v1/telemetry/backfill: Offline-first store-and-forward ledger catch-up stream
 * - POST /api/v1/control/profile  : Dynamic crop agronomic profile selector (AGRONOMIST role)
 * - POST /api/v1/control/fault    : Diagnostic fault injector for testing & SCADA ops (OPERATOR role)
 * - POST /api/v1/control/actuator : Remote actuator mode/state controller enforcing safety interlocks (OPERATOR role)
 * - GET  /api/v1/audit/status     : Cryptographic SHA-256 audit ledger verification status
 * - GET  /metrics                 : Prometheus metrics exposition endpoint (text/plain 0.0.4)
 */
public class EdgeTelemetryServer {

    public static final int DEFAULT_PORT = 8085;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ControlTask controlTask;
    private final String zoneId;
    private final String zoneName;
    private final int port;
    private final EdgeSecurityManager securityManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer server;

    public EdgeTelemetryServer(ControlTask controlTask, int port) {
        this(controlTask, "ZONE-01", "Main Production Zone", port, new EdgeSecurityManager());
    }

    public EdgeTelemetryServer(ControlTask controlTask, String zoneId, String zoneName, int port) {
        this(controlTask, zoneId, zoneName, port, new EdgeSecurityManager());
    }

    public EdgeTelemetryServer(
            ControlTask controlTask,
            String zoneId,
            String zoneName,
            int port,
            EdgeSecurityManager securityManager) {
        this.controlTask = controlTask;
        this.zoneId = (zoneId != null && !zoneId.isBlank()) ? zoneId.trim() : "ZONE-01";
        this.zoneName = (zoneName != null && !zoneName.isBlank()) ? zoneName.trim() : "Main Production Zone";
        this.port = port;
        this.securityManager = (securityManager != null) ? securityManager : new EdgeSecurityManager();
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/status", new StatusHandler());
        server.createContext("/api/v1/telemetry/stream", new SseStreamHandler());
        server.createContext("/api/v1/telemetry/backfill", new BackfillHandler());
        server.createContext("/api/v1/control/profile", new ProfileHandler());
        server.createContext("/api/v1/control/fault", new FaultHandler());
        server.createContext("/api/v1/control/actuator", new ActuatorControlHandler());
        server.createContext("/api/v1/audit/status", new AuditStatusHandler());
        server.createContext("/metrics", new MetricsHandler());

        // Low-overhead daemon executor so background HTTP threads never block JVM shutdown
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "EdgeHttpWorker");
            t.setDaemon(true);
            return t;
        }));

        server.start();
        running.set(true);
        System.out.printf("[EDGE GATEWAY] HTTP/SSE, Backfill & Prometheus service active on http://0.0.0.0:%d [%s: %s]%n",
            port, zoneId, zoneName);
        System.out.printf("[EDGE GATEWAY] Security Mode: %s | Shared Secret: %s...%n",
            securityManager.getAuthMode(), securityManager.getAuthSecret().substring(0, Math.min(8, securityManager.getAuthSecret().length())));
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        if (server != null) {
            server.stop(0);
        }
        System.out.println("[EDGE GATEWAY] Telemetry server stopped cleanly.");
    }

    public int getPort() {
        return port;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public EdgeSecurityManager getSecurityManager() {
        return securityManager;
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
            "Content-Type, Authorization, X-AgriGuard-Role, X-AgriGuard-Timestamp, X-AgriGuard-Signature");
    }

    private String buildJsonSnapshot() {
        CropProfile profile = controlTask.getActiveProfile();
        TMRVoter.VoteResult vote = controlTask.getLatestVoteResult();
        double temp = vote.temperature();
        String status = vote.status().name();
        double humidity = controlTask.getLatestHumidity();
        double soil = controlTask.getLatestSoilMoisture();
        double vpd = controlTask.getLatestVpd();

        ActuatorManager actuators = controlTask.getActuators();
        WatchdogMonitor watchdog = controlTask.getWatchdog();
        BoundedBuffer<SensorReading> buffer = controlTask.getBuffer();

        boolean safeState = actuators.isSafeStateActive() || watchdog.isSafeState();

        AgronomyIntelligence.AgronomySnapshot agro = controlTask.getAgronomySnapshot();
        if (agro == null) {
            agro = new AgronomyIntelligence.AgronomySnapshot(
                18.24, 6.26, AgronomyIntelligence.FungalRisk.LOW, 14.5, 3.8, "Nominal pulse schedule"
            );
        }

        return String.format(Locale.US,
            "{\n" +
            "  \"zoneId\": \"%s\",\n" +
            "  \"zoneName\": \"%s\",\n" +
            "  \"timestamp\": \"%s\",\n" +
            "  \"isoDateTime\": \"%s\",\n" +
            "  \"cropProfile\": \"%s\",\n" +
            "  \"cropDisplayName\": \"%s\",\n" +
            "  \"votedTemperature\": %.2f,\n" +
            "  \"voteStatus\": \"%s\",\n" +
            "  \"voteDetail\": \"%s\",\n" +
            "  \"humidityPercent\": %.1f,\n" +
            "  \"soilMoisturePercent\": %.1f,\n" +
            "  \"vpdKpa\": %.3f,\n" +
            "  \"dewPointC\": %.2f,\n" +
            "  \"dewPointSpreadC\": %.2f,\n" +
            "  \"fungalRiskLevel\": \"%s\",\n" +
            "  \"gddAccumulated\": %.2f,\n" +
            "  \"et0MmDay\": %.2f,\n" +
            "  \"irrigationAdvisory\": \"%s\",\n" +
            "  \"agronomy\": {\n" +
            "    \"dewPointC\": %.2f,\n" +
            "    \"dewPointSpreadC\": %.2f,\n" +
            "    \"fungalRiskLevel\": \"%s\",\n" +
            "    \"fungalRiskSeverity\": %d,\n" +
            "    \"fungalRiskDescription\": \"%s\",\n" +
            "    \"gddAccumulated\": %.2f,\n" +
            "    \"et0MmDay\": %.2f,\n" +
            "    \"irrigationAdvisory\": \"%s\"\n" +
            "  },\n" +
            "  \"actuators\": {\n" +
            "    \"heater\": %b,\n" +
            "    \"fan\": %b,\n" +
            "    \"pump\": %b,\n" +
            "    \"fogger\": %b,\n" +
            "    \"alarm\": %b,\n" +
            "    \"controlMode\": \"%s\",\n" +
            "    \"safeStateInterlock\": %b\n" +
            "  },\n" +
            "  \"diagnostics\": {\n" +
            "    \"tmrMaskedTotal\": %d,\n" +
            "    \"watchdogFailuresTotal\": %d,\n" +
            "    \"safeStateActive\": %b,\n" +
            "    \"bufferOccupancy\": %d,\n" +
            "    \"bufferCapacity\": %d\n" +
            "  }\n" +
            "}",
            zoneId,
            zoneName,
            LocalTime.now().format(TIME_FMT),
            Instant.now().toString(),
            profile.name(),
            profile.getDisplayName(),
            temp,
            status,
            vote.detail().replace("\"", "'"),
            humidity,
            soil,
            vpd,
            agro.dewPointC(),
            agro.dewPointSpreadC(),
            agro.fungalRisk().name(),
            agro.gddAccumulated(),
            agro.et0MmDay(),
            agro.irrigationAdvisory().replace("\"", "'"),
            agro.dewPointC(),
            agro.dewPointSpreadC(),
            agro.fungalRisk().name(),
            agro.fungalRisk().getSeverityLevel(),
            agro.fungalRisk().getDescription(),
            agro.gddAccumulated(),
            agro.et0MmDay(),
            agro.irrigationAdvisory().replace("\"", "'"),
            actuators.isHeaterOn(),
            actuators.isFanOn(),
            actuators.isPumpOn(),
            actuators.isFoggerOn(),
            actuators.isAlarmOn(),
            safeState ? "FAIL_SAFE" : "AUTO",
            safeState,
            controlTask.getTmrMaskedTotal(),
            watchdog.getWatchdogFailuresTotal(),
            safeState,
            buffer != null ? buffer.size() : 0,
            buffer != null ? buffer.getCapacity() : 10
        );
    }

    private static String extractParam(String query, String body, String key) {
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=");
                if (parts.length >= 2 && parts[0].equalsIgnoreCase(key)) {
                    return parts[1].trim();
                }
            }
        }
        if (body != null && !body.isEmpty()) {
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?([^\",}]+)\"?", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(body);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private static Boolean extractBooleanParam(String query, String body, String key) {
        String s = extractParam(query, body, key);
        if (s == null) {
            return null;
        }
        return Boolean.parseBoolean(s.trim());
    }

    // -------------------------------------------------------------------------
    // HANDLERS
    // -------------------------------------------------------------------------

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String clientIp = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "127.0.0.1";
            if (!securityManager.checkRateLimit(clientIp)) {
                sendJsonResponse(exchange, 429, "{\"error\":\"Rate limit exceeded. Maximum 30 req/sec\"}");
                return;
            }

            byte[] jsonBytes = buildJsonSnapshot().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, jsonBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonBytes);
            }
        }
    }

    private class SseStreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String clientIp = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "127.0.0.1";
            if (!securityManager.checkRateLimit(clientIp)) {
                sendJsonResponse(exchange, 429, "{\"error\":\"Rate limit exceeded. Maximum 30 req/sec\"}");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0); // Chunked transfer

            try (OutputStream os = exchange.getResponseBody()) {
                while (running.get()) {
                    String json = buildJsonSnapshot();
                    String compactJson = json.replace("\n", " ").replaceAll("\\s+", " ");
                    String sseEvent = "event: telemetry\ndata: " + compactJson + "\n\n";

                    os.write(sseEvent.getBytes(StandardCharsets.UTF_8));
                    os.flush();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException clientClosed) {
                // Client closed SSE stream connection - exit cleanly
            }
        }
    }

    private class BackfillHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String clientIp = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "127.0.0.1";
            if (!securityManager.checkRateLimit(clientIp)) {
                sendJsonResponse(exchange, 429, "{\"error\":\"Rate limit exceeded. Maximum 30 req/sec\"}");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            long sinceBlock = 0;
            int limit = 100;
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] parts = pair.split("=");
                    if (parts.length >= 2) {
                        if ("sinceBlock".equalsIgnoreCase(parts[0])) {
                            try { sinceBlock = Long.parseLong(parts[1].trim()); } catch (NumberFormatException ignored) {}
                        } else if ("limit".equalsIgnoreCase(parts[0])) {
                            try { limit = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }

            TelemetryLogger logger = controlTask.getTelemetryLogger();
            String json;
            if (logger != null) {
                json = logger.getBackfillJson(sinceBlock, limit, zoneId);
            } else {
                json = String.format(Locale.US,
                    "{\"zoneId\":\"%s\",\"totalRecords\":0,\"sinceBlock\":0,\"returnedCount\":0,\"hasMore\":false,\"records\":[]}",
                    zoneId);
            }

            sendJsonResponse(exchange, 200, json);
        }
    }

    private class ProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // IEC 62443 Cryptographic Authentication & RBAC Check
            EdgeSecurityManager.SecurityResult sec = securityManager.authenticateAndAuthorize(exchange, body, "PROFILE");
            if (!sec.authorized()) {
                sendJsonResponse(exchange, sec.statusCode(), String.format(Locale.US,
                    "{\"error\":\"%s\",\"status\":\"UNAUTHORIZED\"}", sec.errorMessage()));
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String profileName = extractParam(query, body, "profile");

            if (profileName == null || profileName.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Missing 'profile' parameter in JSON body or query\"}");
                return;
            }

            try {
                CropProfile newProfile = CropProfile.valueOf(profileName.toUpperCase().trim());
                controlTask.setCropProfile(newProfile);
                sendJsonResponse(exchange, 200, String.format(Locale.US,
                    "{\"status\":\"OK\",\"message\":\"Switched profile to %s\",\"profile\":\"%s\",\"authorizedRole\":\"%s\"}",
                    newProfile.getDisplayName(), newProfile.name(), sec.role() != null ? sec.role().name() : "PERMISSIVE"));
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, String.format(Locale.US,
                    "{\"error\":\"Unknown profile '%s'. Allowed: BELL_PEPPER, STRAWBERRY, TEA_NURSERY, ORCHID_HOUSE\"}",
                    profileName));
            }
        }
    }

    private class FaultHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // IEC 62443 Cryptographic Authentication & RBAC Check
            EdgeSecurityManager.SecurityResult sec = securityManager.authenticateAndAuthorize(exchange, body, "DIAGNOSTICS");
            if (!sec.authorized()) {
                sendJsonResponse(exchange, sec.statusCode(), String.format(Locale.US,
                    "{\"error\":\"%s\",\"status\":\"UNAUTHORIZED\"}", sec.errorMessage()));
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String action = extractParam(query, body, "action");

            if (action == null || action.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Missing 'action' parameter (BIAS, DISCONNECT, DUAL, SURGE, FREEZE, EXCEPTION, RESET)\"}");
                return;
            }

            SimulatedFieldBus bus = controlTask.getFieldBus();
            String actionNorm = action.toUpperCase().trim();
            String message;

            switch (actionNorm) {
                case "BIAS" -> {
                    bus.setChannelState("T2", SimulatedFieldBus.ChannelState.BIAS_OFFSET, 15.0);
                    message = "Injected +15.0°C transient drift bias on Channel T2";
                }
                case "DISCONNECT" -> {
                    bus.setChannelState("T3", SimulatedFieldBus.ChannelState.DISCONNECTED, 0.0);
                    message = "Disconnected Channel T3 (watchdog omission test)";
                }
                case "DUAL" -> {
                    bus.setChannelState("T3", SimulatedFieldBus.ChannelState.DISCONNECTED, 0.0);
                    bus.setChannelState("T1", SimulatedFieldBus.ChannelState.BIAS_OFFSET, 15.0);
                    message = "Simulated dual-channel failure (T3 disconnected + T1 biased)";
                }
                case "SURGE" -> {
                    bus.injectThermalSurge(14.0);
                    message = "Injected sudden +14.0°C thermal surge into plantation microclimate";
                }
                case "FREEZE" -> {
                    bus.setChannelState("T1", SimulatedFieldBus.ChannelState.FROZEN, 0.0);
                    message = "Frozen Channel T1 ADC transducer output";
                }
                case "EXCEPTION", "FAULT" -> {
                    controlTask.triggerSimulatedException();
                    message = "Arming simulated controller software exception for forward error recovery";
                }
                case "RESET" -> {
                    bus.resetAllChannels();
                    message = "Reset all fieldbus sensor channels to nominal state";
                }
                default -> {
                    sendJsonResponse(exchange, 400, String.format(Locale.US,
                        "{\"error\":\"Unrecognized action '%s'\"}", action));
                    return;
                }
            }

            sendJsonResponse(exchange, 200, String.format(Locale.US,
                "{\"status\":\"OK\",\"action\":\"%s\",\"message\":\"%s\",\"authorizedRole\":\"%s\"}",
                actionNorm, message, sec.role() != null ? sec.role().name() : "PERMISSIVE"));
        }
    }

    private class ActuatorControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // IEC 62443 Cryptographic Authentication & RBAC Check
            EdgeSecurityManager.SecurityResult sec = securityManager.authenticateAndAuthorize(exchange, body, "ACTUATOR");
            if (!sec.authorized()) {
                sendJsonResponse(exchange, sec.statusCode(), String.format(Locale.US,
                    "{\"error\":\"%s\",\"status\":\"UNAUTHORIZED\"}", sec.errorMessage()));
                return;
            }

            String query = exchange.getRequestURI().getQuery();

            String actuator = extractParam(query, body, "actuator");
            String mode = extractParam(query, body, "mode");
            Boolean state = extractBooleanParam(query, body, "state");

            if (actuator == null || actuator.isBlank()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Missing 'actuator' parameter (HEATER, FAN, PUMP, FOGGER, ALARM)\"}");
                return;
            }

            ActuatorManager actuators = controlTask.getActuators();
            WatchdogMonitor watchdog = controlTask.getWatchdog();
            boolean interlockActive = actuators.isSafeStateActive() || watchdog.isSafeState();

            boolean success = actuators.setRemoteActuator(actuator, mode, state);
            if (!success) {
                sendJsonResponse(exchange, 400, String.format(Locale.US,
                    "{\"error\":\"Unknown actuator '%s'. Allowed: HEATER, FAN, PUMP, FOGGER, ALARM\"}", actuator));
                return;
            }

            boolean actualState = actuators.getActuatorState(actuator);
            String actualMode = actuators.getActuatorControlMode(actuator).name();

            String status;
            String message;
            if (interlockActive) {
                status = "INTERLOCKED";
                message = String.format(Locale.US,
                    "Command registered, but CRITICAL SAFETY INTERLOCK IS ACTIVE. Physical outputs remain strictly locked in safe state (Heater OFF, Fan ON, Alarm ON). Actual '%s' state is %s.",
                    actuator.toUpperCase(), actualState ? "ON" : "OFF");
            } else {
                status = "OK";
                message = String.format(Locale.US,
                    "Actuator '%s' updated to %s mode (state=%s).",
                    actuator.toUpperCase(), actualMode, actualState ? "ON" : "OFF");
            }

            String jsonResponse = String.format(Locale.US,
                "{\n" +
                "  \"status\": \"%s\",\n" +
                "  \"zoneId\": \"%s\",\n" +
                "  \"actuator\": \"%s\",\n" +
                "  \"mode\": \"%s\",\n" +
                "  \"requestedState\": %s,\n" +
                "  \"actualState\": %b,\n" +
                "  \"safeStateInterlock\": %b,\n" +
                "  \"authorizedRole\": \"%s\",\n" +
                "  \"message\": \"%s\"\n" +
                "}",
                status,
                zoneId,
                actuator.toUpperCase(),
                actualMode,
                (state != null ? state.toString() : "null"),
                actualState,
                interlockActive,
                sec.role() != null ? sec.role().name() : "PERMISSIVE",
                message
            );

            sendJsonResponse(exchange, 200, jsonResponse);
        }
    }

    private class AuditStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String clientIp = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "127.0.0.1";
            if (!securityManager.checkRateLimit(clientIp)) {
                sendJsonResponse(exchange, 429, "{\"error\":\"Rate limit exceeded. Maximum 30 req/sec\"}");
                return;
            }

            TelemetryLogger logger = controlTask.getTelemetryLogger();
            if (logger != null) {
                logger.flush();
            }
            long totalRecords = (logger != null) ? logger.getRowCount() : 0;
            String genesisHash = TelemetryLogger.GENESIS_HASH;
            String latestHash = (logger != null) ? logger.getCurrentLastHash() : genesisHash;
            Path logPath = (logger != null && logger.getLogFile() != null)
                ? logger.getLogFile().toPath()
                : Path.of(TelemetryLogger.DEFAULT_LOG_FILE);

            boolean verified = Files.exists(logPath) && TelemetryLogger.verifyAuditChain(logPath, false);

            String json = String.format(Locale.US,
                "{\n" +
                "  \"zoneId\": \"%s\",\n" +
                "  \"zoneName\": \"%s\",\n" +
                "  \"auditLedgerFile\": \"%s\",\n" +
                "  \"totalRecords\": %d,\n" +
                "  \"genesisHash\": \"%s\",\n" +
                "  \"latestHash\": \"%s\",\n" +
                "  \"chainVerified\": %b\n" +
                "}",
                zoneId,
                zoneName,
                logPath.toString().replace("\\", "/"),
                totalRecords,
                genesisHash,
                latestHash,
                verified
            );

            sendJsonResponse(exchange, 200, json);
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String clientIp = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "127.0.0.1";
            if (!securityManager.checkRateLimit(clientIp)) {
                sendJsonResponse(exchange, 429, "{\"error\":\"Rate limit exceeded. Maximum 30 req/sec\"}");
                return;
            }

            TimingMonitor tm = controlTask.getTimingMonitor();
            WatchdogMonitor watchdog = controlTask.getWatchdog();
            ActuatorManager actuators = controlTask.getActuators();
            TMRVoter.VoteResult vote = controlTask.getLatestVoteResult();
            AgronomyIntelligence.AgronomySnapshot agro = controlTask.getAgronomySnapshot();

            boolean safeState = actuators.isSafeStateActive() || watchdog.isSafeState();

            Map<String, Double> wcetMap = (tm != null) ? tm.getTaskWcetMs() : Map.of();
            double samplingWcet = wcetMap.getOrDefault("Sensor Acquisition", 0.0);
            double controlWcet = wcetMap.getOrDefault("SCADA Control Cycle", 0.0);
            double watchdogWcet = wcetMap.getOrDefault("Watchdog Supervisor", 0.0);
            double statusWcet = wcetMap.getOrDefault("Telemetry & Status", 0.0);
            double rmUtil = (tm != null) ? tm.getTotalUtilization() : 0.0;

            double dewPoint = (agro != null) ? agro.dewPointC() : 18.2;
            double spread = (agro != null) ? agro.dewPointSpreadC() : 6.2;
            int riskSeverity = (agro != null && agro.fungalRisk() != null) ? agro.fungalRisk().getSeverityLevel() : 0;
            double gdd = (agro != null) ? agro.gddAccumulated() : 14.5;
            double et0 = (agro != null) ? agro.et0MmDay() : 3.8;

            StringBuilder sb = new StringBuilder();
            sb.append("# HELP agriguard_voted_temperature_celsius Current consensus temperature from TMR voting in degrees Celsius.\n");
            sb.append("# TYPE agriguard_voted_temperature_celsius gauge\n");
            sb.append(String.format(Locale.US, "agriguard_voted_temperature_celsius{zone=\"%s\"} %.2f\n\n", zoneId, vote.temperature()));

            sb.append("# HELP agriguard_humidity_percent Current relative humidity percentage.\n");
            sb.append("# TYPE agriguard_humidity_percent gauge\n");
            sb.append(String.format(Locale.US, "agriguard_humidity_percent{zone=\"%s\"} %.1f\n\n", zoneId, controlTask.getLatestHumidity()));

            sb.append("# HELP agriguard_soil_moisture_percent Current volumetric soil moisture percentage.\n");
            sb.append("# TYPE agriguard_soil_moisture_percent gauge\n");
            sb.append(String.format(Locale.US, "agriguard_soil_moisture_percent{zone=\"%s\"} %.1f\n\n", zoneId, controlTask.getLatestSoilMoisture()));

            sb.append("# HELP agriguard_vpd_kpa Current Vapor Pressure Deficit in kilopascals.\n");
            sb.append("# TYPE agriguard_vpd_kpa gauge\n");
            sb.append(String.format(Locale.US, "agriguard_vpd_kpa{zone=\"%s\"} %.3f\n\n", zoneId, controlTask.getLatestVpd()));

            sb.append("# HELP agriguard_dew_point_celsius Calculated dew point temperature in degrees Celsius.\n");
            sb.append("# TYPE agriguard_dew_point_celsius gauge\n");
            sb.append(String.format(Locale.US, "agriguard_dew_point_celsius{zone=\"%s\"} %.2f\n\n", zoneId, dewPoint));

            sb.append("# HELP agriguard_dew_point_spread_celsius Margin to surface condensation (canopy temp minus dew point) in degrees Celsius.\n");
            sb.append("# TYPE agriguard_dew_point_spread_celsius gauge\n");
            sb.append(String.format(Locale.US, "agriguard_dew_point_spread_celsius{zone=\"%s\"} %.2f\n\n", zoneId, spread));

            sb.append("# HELP agriguard_fungal_risk_level Botrytis and condensation fungal risk classifier (0=LOW, 1=MODERATE, 2=HIGH, 3=CRITICAL).\n");
            sb.append("# TYPE agriguard_fungal_risk_level gauge\n");
            sb.append(String.format(Locale.US, "agriguard_fungal_risk_level{zone=\"%s\"} %d\n\n", zoneId, riskSeverity));

            sb.append("# HELP agriguard_gdd_accumulated Cumulative growing degree days above base physiological threshold.\n");
            sb.append("# TYPE agriguard_gdd_accumulated gauge\n");
            sb.append(String.format(Locale.US, "agriguard_gdd_accumulated{zone=\"%s\"} %.2f\n\n", zoneId, gdd));

            sb.append("# HELP agriguard_et0_mm_per_day Reference evapotranspiration rate in mm per day.\n");
            sb.append("# TYPE agriguard_et0_mm_per_day gauge\n");
            sb.append(String.format(Locale.US, "agriguard_et0_mm_per_day{zone=\"%s\"} %.2f\n\n", zoneId, et0));

            sb.append("# HELP agriguard_safe_state_active Indicates whether safety interlock fail-safe state is active (1=active, 0=normal).\n");
            sb.append("# TYPE agriguard_safe_state_active gauge\n");
            sb.append(String.format(Locale.US, "agriguard_safe_state_active{zone=\"%s\"} %d\n\n", zoneId, safeState ? 1 : 0));

            sb.append("# HELP agriguard_tmr_masked_total Cumulative count of transient sensor faults masked by TMR majority voting.\n");
            sb.append("# TYPE agriguard_tmr_masked_total counter\n");
            sb.append(String.format(Locale.US, "agriguard_tmr_masked_total{zone=\"%s\"} %d\n\n", zoneId, controlTask.getTmrMaskedTotal()));

            sb.append("# HELP agriguard_watchdog_failures_total Cumulative count of deadline omission/timeout faults caught by Watchdog.\n");
            sb.append("# TYPE agriguard_watchdog_failures_total counter\n");
            sb.append(String.format(Locale.US, "agriguard_watchdog_failures_total{zone=\"%s\"} %d\n\n", zoneId, watchdog.getWatchdogFailuresTotal()));

            sb.append("# HELP agriguard_task_wcet_ms Worst-case execution time observed per real-time task in milliseconds.\n");
            sb.append("# TYPE agriguard_task_wcet_ms gauge\n");
            sb.append(String.format(Locale.US, "agriguard_task_wcet_ms{zone=\"%s\",task=\"sensor_sampling\"} %.3f\n", zoneId, samplingWcet));
            sb.append(String.format(Locale.US, "agriguard_task_wcet_ms{zone=\"%s\",task=\"control_cycle\"} %.3f\n", zoneId, controlWcet));
            sb.append(String.format(Locale.US, "agriguard_task_wcet_ms{zone=\"%s\",task=\"watchdog_check\"} %.3f\n", zoneId, watchdogWcet));
            sb.append(String.format(Locale.US, "agriguard_task_wcet_ms{zone=\"%s\",task=\"status_display\"} %.3f\n\n", zoneId, statusWcet));

            sb.append("# HELP agriguard_rm_utilization_ratio Current total Rate-Monotonic CPU utilization ratio.\n");
            sb.append("# TYPE agriguard_rm_utilization_ratio gauge\n");
            sb.append(String.format(Locale.US, "agriguard_rm_utilization_ratio{zone=\"%s\"} %.4f\n", zoneId, rmUtil));

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
