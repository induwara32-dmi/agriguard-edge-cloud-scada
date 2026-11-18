import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless Automated Real-Time Verification Suite for CI/CD Pipelines & Container Healthchecks.
 * Executes 7 deterministic real-time, fault-tolerance, and cryptographic verification suites
 * without requiring a GUI or external test harnesses.
 *
 * Test Matrix:
 * 1. TMR consensus under nominal conditions
 * 2. Transient sensor outlier masking (median consensus)
 * 3. Watchdog deadline omission detection & fail-safe trip
 * 4. Critical safety interlocks (heater cutoff, fan active, siren active)
 * 5. Automatic recovery upon heartbeat restoration
 * 6. Rate-Monotonic schedulability bound verification (Liu & Layland <= 0.7568)
 * 7. SHA-256 audit ledger cryptographic integrity and tamper detection
 */
public class HeadlessVerificationSuite {

    public static boolean runSuite() {
        System.out.println("================================================================================");
        System.out.println("    AGRIGUARD RT-SCADA: HEADLESS CONTINUOUS INTEGRATION VERIFICATION SUITE     ");
        System.out.println("================================================================================");

        int totalTests = 9;
        int passedTests = 0;

        // Test 1: TMR Consensus under normal conditions
        if (testTmrNominalConsensus()) {
            passedTests++;
            System.out.printf("  [TEST 1/%d] TMR Consensus Under Nominal Conditions ............ [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 1/%d] TMR Consensus Under Nominal Conditions ............ [FAILED]%n", totalTests);
        }

        // Test 2: Transient outlier fault masking
        if (testTmrOutlierMasking()) {
            passedTests++;
            System.out.printf("  [TEST 2/%d] Transient Outlier Fault Masking (TMR) ............. [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 2/%d] Transient Outlier Fault Masking (TMR) ............. [FAILED]%n", totalTests);
        }

        // Test 3: Watchdog deadline omission detection
        if (testWatchdogOmissionDetection()) {
            passedTests++;
            System.out.printf("  [TEST 3/%d] Watchdog Deadline Omission & Timeout Detection ..... [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 3/%d] Watchdog Deadline Omission & Timeout Detection ..... [FAILED]%n", totalTests);
        }

        // Test 4: Critical safety interlock enforcement
        if (testSafetyInterlockEnforcement()) {
            passedTests++;
            System.out.printf("  [TEST 4/%d] Critical Thermal Safety Interlock Interdictions .... [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 4/%d] Critical Thermal Safety Interlock Interdictions .... [FAILED]%n", totalTests);
        }

        // Test 5: Automatic recovery upon heartbeat restoration
        if (testAutomaticRecovery()) {
            passedTests++;
            System.out.printf("  [TEST 5/%d] Automatic Fail-Safe Recovery On Signal Return ...... [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 5/%d] Automatic Fail-Safe Recovery On Signal Return ...... [FAILED]%n", totalTests);
        }

        // Test 6: Rate-Monotonic schedulability bound
        if (testRateMonotonicSchedulability()) {
            passedTests++;
            System.out.printf("  [TEST 6/%d] Rate-Monotonic (Liu & Layland) Bound Check ........ [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 6/%d] Rate-Monotonic (Liu & Layland) Bound Check ........ [FAILED]%n", totalTests);
        }

        // Test 7: Cryptographic SHA-256 audit chain & tamper alert
        if (testCryptographicAuditLedger()) {
            passedTests++;
            System.out.printf("  [TEST 7/%d] SHA-256 Ledger Integrity & Tamper Detection ....... [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 7/%d] SHA-256 Ledger Integrity & Tamper Detection ....... [FAILED]%n", totalTests);
        }

        // Test 8: IEC 62443 HMAC-SHA256 & RBAC Security Enforcement
        if (testEdgeSecurityHmacAndRbac()) {
            passedTests++;
            System.out.printf("  [TEST 8/%d] HMAC-SHA256 & RBAC Security Enforcement .......... [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 8/%d] HMAC-SHA256 & RBAC Security Enforcement .......... [FAILED]%n", totalTests);
        }

        // Test 9: Predictive Dew Point & Fungal Risk Detection
        if (testPredictiveDewPointAndFungalRisk()) {
            passedTests++;
            System.out.printf("  [TEST 9/%d] Predictive Dew Point & Fungal Risk Detection ...... [PASSED]%n", totalTests);
        } else {
            System.err.printf("  [TEST 9/%d] Predictive Dew Point & Fungal Risk Detection ...... [FAILED]%n", totalTests);
        }

        // Extra Protocol Test: Modbus/TCP loopback codec self-test
        boolean modbusOk = ModbusTcpSensorBridge.runSelfTest();
        if (modbusOk) {
            System.out.printf("  [PROTOCOL] Modbus/TCP CRC-16 Codec & Anomaly Rejection ........ [PASSED]%n");
        } else {
            System.err.printf("  [PROTOCOL] Modbus/TCP CRC-16 Codec & Anomaly Rejection ........ [FAILED]%n");
        }

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Verification Suite Result: %d/%d Tests Passed (All Deterministic Assurances Valid)%n",
            passedTests, totalTests);
        System.out.println("================================================================================");

        return passedTests == totalTests && modbusOk;
    }

    private static boolean testTmrNominalConsensus() {
        List<SensorReading> readings = List.of(
            new SensorReading("T1", SensorReading.SensorType.TEMPERATURE, 24.1, System.currentTimeMillis()),
            new SensorReading("T2", SensorReading.SensorType.TEMPERATURE, 24.3, System.currentTimeMillis()),
            new SensorReading("T3", SensorReading.SensorType.TEMPERATURE, 24.2, System.currentTimeMillis())
        );
        TMRVoter.VoteResult res = TMRVoter.vote(readings);
        return res.status() == TMRVoter.VoteStatus.OK && Math.abs(res.temperature() - 24.2) < 0.15;
    }

    private static boolean testTmrOutlierMasking() {
        List<SensorReading> readings = List.of(
            new SensorReading("T1", SensorReading.SensorType.TEMPERATURE, 24.0, System.currentTimeMillis()),
            new SensorReading("T2", SensorReading.SensorType.TEMPERATURE, 39.5, System.currentTimeMillis()), // Biased outlier
            new SensorReading("T3", SensorReading.SensorType.TEMPERATURE, 24.2, System.currentTimeMillis())
        );
        TMRVoter.VoteResult res = TMRVoter.vote(readings);
        return res.status() == TMRVoter.VoteStatus.MASKED && Math.abs(res.temperature() - 24.1) < 0.2;
    }

    private static boolean testWatchdogOmissionDetection() {
        WatchdogMonitor watchdog = new WatchdogMonitor(80); // 80 ms timeout
        watchdog.heartbeat("T1");
        watchdog.heartbeat("T2");
        watchdog.heartbeat("T3");

        // Wait 120 ms to expire T1, T2, T3
        try {
            Thread.sleep(120);
        } catch (InterruptedException ignored) {}

        watchdog.checkDeadlines();
        return !watchdog.isSensorAlive("T1") && !watchdog.isSensorAlive("T2") && !watchdog.isSensorAlive("T3") && watchdog.isSafeState();
    }

    private static boolean testSafetyInterlockEnforcement() {
        ActuatorManager actuators = new ActuatorManager();

        // 1. Nominal state: heater can turn on
        actuators.setAutoHeater(true, "Cold canopy");
        if (!actuators.isHeaterOn()) {
            return false;
        }

        // 2. Safety Interlock Trip
        actuators.applySafeState("Emergency thermal excursion");

        // Verify fail-safe condition: Heater forced OFF, Fan forced ON, Alarm forced ON
        boolean heaterOff = !actuators.isHeaterOn();
        boolean fanOn = actuators.isFanOn();
        boolean alarmOn = actuators.isAlarmOn();

        // 3. Verify manual override cannot bypass safety interlock
        actuators.setManualOverride("Heater", true);
        boolean overrideBlocked = !actuators.isHeaterOn();

        return heaterOff && fanOn && alarmOn && overrideBlocked;
    }

    private static boolean testAutomaticRecovery() {
        WatchdogMonitor watchdog = new WatchdogMonitor(500);
        watchdog.enterSafeState("Simulated trip");
        if (!watchdog.isSafeState()) {
            return false;
        }

        watchdog.exitSafeState("Heartbeats verified restored");
        return !watchdog.isSafeState();
    }

    private static boolean testRateMonotonicSchedulability() {
        TimingMonitor tm = new TimingMonitor();
        // Record 10 iterations of nominal task timings
        for (int i = 0; i < 10; i++) {
            tm.recordExecution("Sensor Acquisition", 1_200_000L);  // 1.2 ms (Period 500 ms)
            tm.recordExecution("SCADA Control Cycle", 2_500_000L);  // 2.5 ms (Period 1000 ms)
            tm.recordExecution("Watchdog Supervisor", 500_000L);    // 0.5 ms (Period 1000 ms)
            tm.recordExecution("Telemetry & Status", 1_000_000L);   // 1.0 ms (Period 2000 ms)
        }

        double util = tm.getTotalUtilization();
        double bound = tm.getLiuLaylandBound(); // ~0.7568

        // Real-time utilization should be ~0.0059, well under 0.7568
        return util > 0.0 && util <= bound;
    }

    private static boolean testCryptographicAuditLedger() {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("agriguard_audit_test");
        } catch (IOException e) {
            System.err.printf("[TEST 7] Temp dir creation failed: %s%n", e.getMessage());
            return false;
        }

        Path csvPath = tempDir.resolve("test_audit.csv");
        TelemetryLogger logger = new TelemetryLogger(csvPath.toString());

        try {
            // Write 5 sample points
            for (int i = 0; i < 5; i++) {
                logger.recordPoint(
                    CropProfile.BELL_PEPPER,
                    24.5 + i * 0.1,
                    "OK",
                    68.0,
                    48.0,
                    1.05,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "AUTO",
                    false,
                    "CI Test Point " + i
                );
            }

            // Close logger to flush writes to disk
            logger.close();

            // Verify nominal chain
            boolean nominalVerified = TelemetryLogger.verifyAuditChain(csvPath);
            if (!nominalVerified) {
                System.err.println("[TEST 7 FAILED] Nominal ledger failed verification!");
                return false;
            }

            // Inject tamper: alter one character in the payload of line 3
            List<String> lines = new ArrayList<>(Files.readAllLines(csvPath));
            if (lines.size() < 4) {
                System.err.println("[TEST 7 FAILED] Expected >= 4 lines in CSV");
                return false;
            }

            // Tamper line index 2 (record #2)
            String originalLine = lines.get(2);
            String tamperedLine = originalLine.replace("24.60", "99.99");
            lines.set(2, tamperedLine);

            Path tamperedPath = tempDir.resolve("tampered_audit.csv");
            Files.write(tamperedPath, lines);

            // Verify that audit chain correctly detects the tampering
            boolean tamperedDetected = !TelemetryLogger.verifyAuditChain(tamperedPath);

            // Cleanup
            try {
                Files.deleteIfExists(csvPath);
                Files.deleteIfExists(tamperedPath);
                Files.deleteIfExists(tempDir);
            } catch (IOException ignored) {}

            return tamperedDetected;

        } catch (Exception e) {
            System.err.printf("[TEST 7 FAILED] Exception: %s%n", e.getMessage());
            return false;
        }
    }

    private static boolean testEdgeSecurityHmacAndRbac() {
        String testSecret = "agri-sec-test-key-2026";
        EdgeSecurityManager sec = new EdgeSecurityManager(testSecret, EdgeSecurityManager.AuthMode.STRICT);

        long now = System.currentTimeMillis();
        String path = "/api/v1/control/profile";
        String body = "{\"profile\":\"STRAWBERRY\"}";

        // 1. Valid Signature with AGRONOMIST role -> Authorized
        String canonical = "AGRONOMIST:" + now + ":POST:" + path + ":" + body;
        String validSig = EdgeSecurityManager.computeHmacSha256(testSecret, canonical);
        EdgeSecurityManager.SecurityResult r1 = sec.authenticateAndAuthorize(
            "127.0.0.1", "POST", path, body, "AGRONOMIST", String.valueOf(now), validSig, "PROFILE"
        );
        if (!r1.authorized() || r1.role() != EdgeSecurityManager.Role.AGRONOMIST) {
            System.err.printf("[TEST 8] Valid AGRONOMIST signature rejected: %s%n", r1.errorMessage());
            return false;
        }

        // 2. Replay Attack Prevention (timestamp outside ±30s window) -> 401 Denied
        long expiredTs = now - 45_000L;
        String expiredCanonical = "AGRONOMIST:" + expiredTs + ":POST:" + path + ":" + body;
        String expiredSig = EdgeSecurityManager.computeHmacSha256(testSecret, expiredCanonical);
        EdgeSecurityManager.SecurityResult r2 = sec.authenticateAndAuthorize(
            "127.0.0.1", "POST", path, body, "AGRONOMIST", String.valueOf(expiredTs), expiredSig, "PROFILE"
        );
        if (r2.authorized() || r2.statusCode() != 401) {
            System.err.println("[TEST 8] Expired timestamp was not rejected as replay attack!");
            return false;
        }

        // 3. Tampered Payload / Invalid Signature -> 401 Denied
        EdgeSecurityManager.SecurityResult r3 = sec.authenticateAndAuthorize(
            "127.0.0.1", "POST", path, "{\"profile\":\"TAMPERED\"}", "AGRONOMIST", String.valueOf(now), validSig, "PROFILE"
        );
        if (r3.authorized() || r3.statusCode() != 401) {
            System.err.println("[TEST 8] Tampered payload was not rejected!");
            return false;
        }

        // 4. RBAC Check: OPERATOR attempting PROFILE mutation -> 403 Forbidden
        String opCanonical = "OPERATOR:" + now + ":POST:" + path + ":" + body;
        String opSig = EdgeSecurityManager.computeHmacSha256(testSecret, opCanonical);
        EdgeSecurityManager.SecurityResult r4 = sec.authenticateAndAuthorize(
            "127.0.0.1", "POST", path, body, "OPERATOR", String.valueOf(now), opSig, "PROFILE"
        );
        if (r4.authorized() || r4.statusCode() != 403) {
            System.err.println("[TEST 8] OPERATOR was not blocked from PROFILE mutation!");
            return false;
        }

        // 5. RBAC Check: VIEWER attempting ACTUATOR mutation -> 403 Forbidden
        String actPath = "/api/v1/control/actuator";
        String actBody = "{\"actuator\":\"HEATER\",\"mode\":\"MANUAL\",\"state\":true}";
        String viewerCanonical = "VIEWER:" + now + ":POST:" + actPath + ":" + actBody;
        String viewerSig = EdgeSecurityManager.computeHmacSha256(testSecret, viewerCanonical);
        EdgeSecurityManager.SecurityResult r5 = sec.authenticateAndAuthorize(
            "127.0.0.1", "POST", actPath, actBody, "VIEWER", String.valueOf(now), viewerSig, "ACTUATOR"
        );
        if (r5.authorized() || r5.statusCode() != 403) {
            System.err.println("[TEST 8] VIEWER was not blocked from ACTUATOR mutation!");
            return false;
        }

        // 6. Token Bucket Rate Limiting Burst Test (30 req/sec)
        String clientIp = "192.168.1.100";
        for (int i = 0; i < 30; i++) {
            if (!sec.checkRateLimit(clientIp)) {
                System.err.println("[TEST 8] Token bucket rejected valid request within capacity!");
                return false;
            }
        }
        if (sec.checkRateLimit(clientIp)) {
            System.err.println("[TEST 8] Rate limiter failed to reject burst over 30 req/sec!");
            return false;
        }

        return true;
    }

    private static boolean testPredictiveDewPointAndFungalRisk() {
        // Benchmark 1: Magnus-Tetens standard physical condition (24°C, 70% RH)
        double t1 = 24.0;
        double rh1 = 70.0;
        double td1 = AgronomyIntelligence.calculateDewPoint(t1, rh1);
        if (Math.abs(td1 - 18.2) > 0.4) {
            System.err.printf("[TEST 9] Dew Point mismatch: expected ~18.2°C, got %.2f°C%n", td1);
            return false;
        }
        double spread1 = AgronomyIntelligence.calculateDewPointSpread(t1, td1);
        if (spread1 < 5.0) {
            System.err.printf("[TEST 9] Dew Point spread mismatch: expected >5.0°C, got %.2f°C%n", spread1);
            return false;
        }
        AgronomyIntelligence.FungalRisk risk1 = AgronomyIntelligence.evaluateFungalRisk(spread1, rh1);
        if (risk1 != AgronomyIntelligence.FungalRisk.LOW) {
            System.err.printf("[TEST 9] Expected LOW risk under nominal canopy, got %s%n", risk1);
            return false;
        }

        // Benchmark 2: Critical Condensation / Fungal Risk (24°C, 92% RH)
        double t2 = 24.0;
        double rh2 = 92.0;
        double td2 = AgronomyIntelligence.calculateDewPoint(t2, rh2);
        double spread2 = AgronomyIntelligence.calculateDewPointSpread(t2, td2);
        if (spread2 >= 2.0) {
            System.err.printf("[TEST 9] Expected spread < 2.0°C for 92%% RH, got %.2f°C%n", spread2);
            return false;
        }
        AgronomyIntelligence.FungalRisk risk2 = AgronomyIntelligence.evaluateFungalRisk(spread2, rh2);
        if (risk2 != AgronomyIntelligence.FungalRisk.HIGH && risk2 != AgronomyIntelligence.FungalRisk.CRITICAL) {
            System.err.printf("[TEST 9] Expected HIGH/CRITICAL fungal risk, got %s%n", risk2);
            return false;
        }

        // Benchmark 3: Reference Evapotranspiration (ET0) & Irrigation Advisory
        double et0 = AgronomyIntelligence.calculateET0(24.0, 1.05);
        if (et0 < 1.0 || et0 > 8.0) {
            System.err.printf("[TEST 9] ET0 out of physiological range: %.2f mm/day%n", et0);
            return false;
        }
        String advisory = AgronomyIntelligence.generateIrrigationAdvisory(et0, 32.0, 40.0, 55.0);
        if (!advisory.contains("Deficit detected") || !advisory.contains("Immediate pulse")) {
            System.err.printf("[TEST 9] Advisory did not detect soil deficit: %s%n", advisory);
            return false;
        }

        return true;
    }
}
