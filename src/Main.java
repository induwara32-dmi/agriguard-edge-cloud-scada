import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * AgriGuard RT-SCADA — Edge Climate & Irrigation Controller Entry Point.
 * High-Assurance Real-Time Edge Computing Platform for Protected Agriculture.
 *
 * Core Subsystems:
 * - Multi-Zone Plantation Edge Identity (--zone-id, --zone-name)
 * - Rate-Monotonic Multi-Tasking Executor (Sensor Acquisition, Control, Watchdog, Historian)
 * - Distributed REST / SSE Telemetry & Prometheus Metrics Gateway (EdgeTelemetryServer)
 * - Industrial Binary Transducer Protocol Adapter (ModbusTcpSensorBridge)
 * - Cryptographic Tamper-Evident SHA-256 Audit Trail (TelemetryLogger)
 * - Automated Headless Verification Suite for CI/CD Pipelines (HeadlessVerificationSuite)
 */
public class Main {

    private static volatile boolean statusDisplayPaused = false;
    private static final AtomicBoolean running = new AtomicBoolean(true);

    private static ScheduledExecutorService scheduler;
    private static TimingMonitor timingMonitor;
    private static SimulatedFieldBus fieldBus;
    private static ControlTask controlTask;
    private static WatchdogMonitor watchdog;
    private static ActuatorManager actuators;
    private static TelemetryLogger telemetryLogger;
    private static EdgeTelemetryServer edgeServer;
    private static ModbusTcpSensorBridge modbusBridge;
    private static SensorTask t1, t2, t3, h1, s1;

    public static void main(String[] args) {
        int httpPort = EdgeTelemetryServer.DEFAULT_PORT;
        int modbusPort = ModbusTcpSensorBridge.DEFAULT_PORT;
        String zoneId = "ZONE-01";
        String zoneName = "Main Production Zone";
        String initialProfile = null;
        String customLogPath = null;
        String authSecret = EdgeSecurityManager.DEFAULT_AUTH_SECRET;
        EdgeSecurityManager.AuthMode authMode = EdgeSecurityManager.AuthMode.STRICT;
        boolean headless = GraphicsEnvironment.isHeadless();
        boolean noHttp = false;
        boolean noModbus = false;

        // Parse CLI arguments
        for (String arg : args) {
            if ("--headless-test".equalsIgnoreCase(arg)) {
                boolean pass = HeadlessVerificationSuite.runSuite();
                System.exit(pass ? 0 : 1);
            } else if (arg.startsWith("--verify-audit")) {
                String pathStr = TelemetryLogger.DEFAULT_LOG_FILE;
                if (arg.contains("=")) {
                    pathStr = arg.substring(arg.indexOf('=') + 1).trim();
                }
                boolean pass = TelemetryLogger.verifyAuditChain(Path.of(pathStr));
                System.exit(pass ? 0 : 1);
            } else if ("--headless".equalsIgnoreCase(arg)) {
                headless = true;
            } else if (arg.startsWith("--port=")) {
                httpPort = Integer.parseInt(arg.substring(7).trim());
            } else if (arg.startsWith("--modbus-port=")) {
                modbusPort = Integer.parseInt(arg.substring(14).trim());
            } else if (arg.startsWith("--zone-id=")) {
                zoneId = arg.substring(10).trim();
            } else if (arg.startsWith("--zone-name=")) {
                zoneName = arg.substring(12).trim().replaceAll("^\"|\"$", "");
            } else if (arg.startsWith("--profile=")) {
                initialProfile = arg.substring(10).trim();
            } else if (arg.startsWith("--crop=")) {
                initialProfile = arg.substring(7).trim();
            } else if (arg.startsWith("--log-file=")) {
                customLogPath = arg.substring(11).trim().replaceAll("^\"|\"$", "");
            } else if (arg.startsWith("--auth-secret=")) {
                authSecret = arg.substring(14).trim();
            } else if (arg.startsWith("--auth-mode=")) {
                String modeStr = arg.substring(12).trim().toUpperCase();
                if ("PERMISSIVE".equals(modeStr)) {
                    authMode = EdgeSecurityManager.AuthMode.PERMISSIVE;
                } else {
                    authMode = EdgeSecurityManager.AuthMode.STRICT;
                }
            } else if ("--no-http".equalsIgnoreCase(arg)) {
                noHttp = true;
            } else if ("--no-modbus".equalsIgnoreCase(arg)) {
                noModbus = true;
            }
        }

        System.out.println("==========================================================================================================");
        System.out.println("            AGRIGUARD RT-SCADA: MULTI-ZONE PLANTATION CLIMATE & IRRIGATION CONTROLLER             ");
        System.out.println("                 High-Assurance Real-Time Edge Computing Platform for Protected Agriculture               ");
        System.out.println("==========================================================================================================");
        System.out.printf("%s [SYSTEM] Zone Identity: [%s] %s%n",
            WatchdogMonitor.getTimestamp(), zoneId, zoneName);
        System.out.printf("%s [SYSTEM] Initializing Fieldbus HAL, Monitor Buffer, and Cryptographic Historian...%n",
            WatchdogMonitor.getTimestamp());

        // 1. Shared Infrastructure
        fieldBus = new SimulatedFieldBus();
        BoundedBuffer<SensorReading> buffer = new BoundedBuffer<>(10);
        timingMonitor = new TimingMonitor();
        watchdog = new WatchdogMonitor(timingMonitor);
        actuators = new ActuatorManager();
        telemetryLogger = (customLogPath != null && !customLogPath.isBlank())
            ? new TelemetryLogger(customLogPath)
            : new TelemetryLogger();
        controlTask = new ControlTask(buffer, actuators, watchdog, fieldBus, timingMonitor, telemetryLogger);

        // Configure initial profile if requested
        if (initialProfile != null && !initialProfile.isBlank()) {
            try {
                CropProfile p = CropProfile.valueOf(initialProfile.toUpperCase().trim());
                controlTask.setCropProfile(p);
            } catch (IllegalArgumentException e) {
                System.err.printf("[SYSTEM] Unknown profile '%s'. Retaining default.%n", initialProfile);
            }
        }

        // 2. Field Acquisition Tasks (500 ms period)
        t1 = new SensorTask("T1", SensorReading.SensorType.TEMPERATURE, fieldBus, buffer, watchdog, timingMonitor);
        t2 = new SensorTask("T2", SensorReading.SensorType.TEMPERATURE, fieldBus, buffer, watchdog, timingMonitor);
        t3 = new SensorTask("T3", SensorReading.SensorType.TEMPERATURE, fieldBus, buffer, watchdog, timingMonitor);
        h1 = new SensorTask("H1", SensorReading.SensorType.HUMIDITY, fieldBus, buffer, watchdog, timingMonitor);
        s1 = new SensorTask("S1", SensorReading.SensorType.SOIL_MOISTURE, fieldBus, buffer, watchdog, timingMonitor);

        // 3. Dedicated Real-Time Thread Pool Allocation (7 threads)
        scheduler = Executors.newScheduledThreadPool(7);

        // Schedule Field Acquisition at 500 ms fixed rate
        scheduler.scheduleAtFixedRate(t1, 0, 500, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(t2, 0, 500, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(t3, 0, 500, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(h1, 0, 500, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(s1, 0, 500, TimeUnit.MILLISECONDS);

        // Schedule Watchdog Supervisor at 1000 ms fixed rate
        scheduler.scheduleAtFixedRate(watchdog, 1000, 1000, TimeUnit.MILLISECONDS);

        // Schedule Closed-Loop SCADA Control Cycle at 1000 ms fixed rate
        scheduler.scheduleAtFixedRate(controlTask, 1000, 1000, TimeUnit.MILLISECONDS);

        // Schedule Telemetry Status Display at 2000 ms fixed rate
        final String activeZoneId = zoneId;
        Runnable statusTask = () -> {
            long startNanos = System.nanoTime();
            try {
                if (statusDisplayPaused) {
                    return;
                }
                TMRVoter.VoteResult vr = controlTask.getLatestVoteResult();
                double hum = controlTask.getLatestHumidity();
                double soil = controlTask.getLatestSoilMoisture();
                double vpd = controlTask.getLatestVpd();
                String actSummary = actuators.getStatusSummary();
                boolean isSafe = watchdog.isSafeState();
                CropProfile profile = controlTask.getActiveProfile();

                String safeSuffix = isSafe ? " *** [FAIL-SAFE INTERLOCK ACTIVE] ***" : "";
                System.out.printf("%s [TELEMETRY] [%s|%s] Temp: %.2f°C (%s) | Hum: %.1f%% | Soil: %.1f%% | VPD: %.2f kPa | Actuators: %s%s%n",
                    WatchdogMonitor.getTimestamp(),
                    activeZoneId,
                    profile.name(),
                    vr.temperature(),
                    vr.getDisplayLabel(),
                    hum,
                    soil,
                    vpd,
                    actSummary,
                    safeSuffix
                );
            } finally {
                long durationNanos = System.nanoTime() - startNanos;
                timingMonitor.recordExecution("Telemetry & Status", durationNanos);
            }
        };
        scheduler.scheduleAtFixedRate(statusTask, 1500, 2000, TimeUnit.MILLISECONDS);

        System.out.printf("%s [SYSTEM] All tasks scheduled under Rate-Monotonic Priority Assignment.%n",
            WatchdogMonitor.getTimestamp());

        // 4. Start Embedded Edge REST, SSE & Prometheus Telemetry Server
        if (!noHttp) {
            try {
                EdgeSecurityManager securityManager = new EdgeSecurityManager(authSecret, authMode);
                edgeServer = new EdgeTelemetryServer(controlTask, zoneId, zoneName, httpPort, securityManager);
                edgeServer.start();
            } catch (IOException e) {
                System.err.printf("[EDGE GATEWAY] Failed to initialize HTTP server on port %d: %s%n", httpPort, e.getMessage());
            }
        }

        // 5. Start Industrial Modbus/TCP Sensor Bridge
        if (!noModbus) {
            try {
                modbusBridge = new ModbusTcpSensorBridge(fieldBus, modbusPort);
                modbusBridge.start();
            } catch (Exception e) {
                System.err.printf("[MODBUS BRIDGE] Failed to start Modbus listener on port %d: %s%n", modbusPort, e.getMessage());
            }
        }

        // 6. Launch Industrial SCADA Dashboard non-blockingly on EDT (if display environment available)
        if (!headless) {
            SwingUtilities.invokeLater(() -> {
                try {
                    PlantationDashboardUI ui = new PlantationDashboardUI(
                        controlTask, watchdog, actuators, fieldBus, telemetryLogger);
                    ui.setVisible(true);
                    System.out.printf("%s [SCADA UI] Modern Operator Dashboard launched successfully.%n",
                        WatchdogMonitor.getTimestamp());
                } catch (Exception e) {
                    System.out.printf("%s [SCADA UI] Dashboard display unavailable: %s%n",
                        WatchdogMonitor.getTimestamp(), e.getMessage());
                }
            });
        } else {
            System.out.printf("%s [SCADA UI] Running in Headless Mode (GUI disabled). Control available via CLI / REST / Modbus.%n",
                WatchdogMonitor.getTimestamp());
        }

        // Register shutdown hook for SIGTERM / container stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                shutdownSystem();
            }
        }, "ShutdownHook"));

        printMenu(httpPort, modbusPort, zoneId, zoneName);

        // 7. Interactive Command Loop (or wait loop for containerized execution)
        Scanner scanner = new Scanner(System.in);
        while (running.get()) {
            if (!scanner.hasNextLine()) {
                // Non-interactive container or background execution
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
                continue;
            }

            String input = scanner.nextLine().trim().toUpperCase();
            if (input.isEmpty()) {
                continue;
            }

            switch (input) {
                case "1", "BIAS"    -> injectF1();
                case "2", "DISC"    -> injectF2();
                case "3", "DUAL"    -> injectF3();
                case "4", "AUDIT"   -> injectF4();
                case "5", "SURGE"   -> injectF5();
                case "6", "FREEZE"  -> injectStuck();
                case "7", "FAULT"   -> injectException();
                case "8", "CROP"    -> cycleCropProfile();
                case "9", "CSV"     -> exportCsv();
                case "R", "RESET"   -> resetAll();
                case "M", "MENU"    -> printMenu(httpPort, modbusPort, zoneId, zoneName);
                case "Q", "QUIT"    -> {
                    shutdownSystem();
                    return;
                }
                default -> System.out.printf("[ERROR] Unrecognized command '%s'. Press 'M' for the command menu.%n", input);
            }
        }
    }

    public static void injectF1() {
        fieldBus.setChannelState("T1", SimulatedFieldBus.ChannelState.NORMAL, 0.0);
        fieldBus.setChannelState("T3", SimulatedFieldBus.ChannelState.NORMAL, 0.0);
        System.out.printf("%s [FIELD TEST] Injected T2 Bias Drift (+15.0°C)...%n",
            WatchdogMonitor.getTimestamp());
        fieldBus.setChannelState("T2", SimulatedFieldBus.ChannelState.BIAS_OFFSET, 15.0);
        System.out.printf("%s [EXPECTATION] TMR Voter will isolate outlier T2 and maintain consensus using T1 and T3.%n",
            WatchdogMonitor.getTimestamp());
    }

    public static void injectF2() {
        fieldBus.setChannelState("T1", SimulatedFieldBus.ChannelState.NORMAL, 0.0);
        fieldBus.setChannelState("T2", SimulatedFieldBus.ChannelState.NORMAL, 0.0);
        System.out.printf("%s [FIELD TEST] Disconnecting T3 Fieldbus Channel (Omission / Loss)...%n",
            WatchdogMonitor.getTimestamp());
        fieldBus.setChannelState("T3", SimulatedFieldBus.ChannelState.DISCONNECTED, 0.0);
        System.out.printf("%s [EXPECTATION] Watchdog detects omission within 2.5s; TMR degrades gracefully to 2-channel consensus.%n",
            WatchdogMonitor.getTimestamp());
    }

    public static void injectF3() {
        fieldBus.setChannelState("T2", SimulatedFieldBus.ChannelState.NORMAL, 0.0);
        System.out.printf("%s [FIELD TEST] Inducing Dual-Channel Field Failure (T3 Disconnected + T1 Bias +12.0°C)...%n",
            WatchdogMonitor.getTimestamp());
        fieldBus.setChannelState("T3", SimulatedFieldBus.ChannelState.DISCONNECTED, 0.0);
        fieldBus.setChannelState("T1", SimulatedFieldBus.ChannelState.BIAS_OFFSET, 12.0);
        System.out.printf("%s [EXPECTATION] Consensus loss across redundant channels -> Fail-Safe Interlock activated.%n",
            WatchdogMonitor.getTimestamp());
    }

    public static void injectF4() {
        statusDisplayPaused = true;
        try {
            Thread.sleep(100);
            RaceConditionDemo.runDemo();
        } catch (InterruptedException ignored) {
        } finally {
            statusDisplayPaused = false;
        }
    }

    public static void injectF5() {
        System.out.printf("%s [FIELD TEST] Injecting Thermal Disturbance (+14.0°C Surge)...%n",
            WatchdogMonitor.getTimestamp());
        fieldBus.injectThermalSurge(14.0);
        System.out.printf("%s [EXPECTATION] Temperature exceeds critical threshold -> Emergency extraction fans and siren activated.%n",
            WatchdogMonitor.getTimestamp());
    }

    public static void injectStuck() {
        System.out.printf("%s [FIELD TEST] Freezing T1 Transmitter measurement...%n",
            WatchdogMonitor.getTimestamp());
        fieldBus.setChannelState("T1", SimulatedFieldBus.ChannelState.FROZEN, 0.0);
    }

    public static void injectException() {
        System.out.printf("%s [FIELD TEST] Triggering Simulated Controller Software Exception...%n",
            WatchdogMonitor.getTimestamp());
        controlTask.triggerSimulatedException();
    }

    public static void cycleCropProfile() {
        CropProfile current = controlTask.getActiveProfile();
        CropProfile[] all = CropProfile.values();
        int nextIdx = (current.ordinal() + 1) % all.length;
        CropProfile next = all[nextIdx];
        controlTask.setCropProfile(next);
    }

    public static void exportCsv() {
        File exportFile = new File("plantation_telemetry_export.csv");
        boolean ok = telemetryLogger.exportAuditLog(exportFile);
        if (ok) {
            System.out.printf("%s [HISTORIAN] Telemetry log exported successfully to: %s%n",
                WatchdogMonitor.getTimestamp(), exportFile.getAbsolutePath());
        } else {
            System.out.printf("%s [HISTORIAN] Failed to export telemetry log.%n",
                WatchdogMonitor.getTimestamp());
        }
    }

    public static void resetAll() {
        System.out.printf("%s [FIELD TEST] Resetting all field bus channels to NORMAL operational mode...%n",
            WatchdogMonitor.getTimestamp());
        fieldBus.resetAllChannels();
        System.out.printf("%s [SYSTEM] Field bus channels restored. Consensus and interlocks recovering automatically.%n",
            WatchdogMonitor.getTimestamp());
    }

    public static void shutdownSystem() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        System.out.printf("%s [SYSTEM] Initiating clean SCADA shutdown...%n",
            WatchdogMonitor.getTimestamp());
        statusDisplayPaused = true;

        if (edgeServer != null) {
            edgeServer.stop();
        }
        if (modbusBridge != null) {
            modbusBridge.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (telemetryLogger != null) {
            telemetryLogger.close();
        }
        if (timingMonitor != null) {
            timingMonitor.printSchedulabilityReport();
        }
        System.out.println("AgriGuard RT-SCADA terminated cleanly. Schedulability audit complete.");
    }

    private static void printMenu(int httpPort, int modbusPort, String zoneId, String zoneName) {
        System.out.println();
        System.out.println("==========================================================================================================");
        System.out.printf("               FIELD COMMISSIONING & DIAGNOSTIC MENU  [%s: %s]%n", zoneId, zoneName);
        System.out.println("==========================================================================================================");
        System.out.println("  [1] or BIAS    : Sensor Bias Drift       -> Inject T2 +15.0°C Outlier [TMR Outlier Masking]");
        System.out.println("  [2] or DISC    : Channel Disconnect      -> Disconnect T3 [Watchdog Omission & 2-CH Degradation]");
        System.out.println("  [3] or DUAL    : Dual-Channel Loss       -> T3 Disconnected + T1 Bias [Fail-Safe Trip]");
        System.out.println("  [4] or AUDIT   : Concurrency Audit       -> Execute Multithreaded Synchronization Benchmark");
        System.out.println("  [5] or SURGE   : Thermal Surge           -> Inject +14.0°C Disturbance [Emergency Siren & Extraction]");
        System.out.println("  [6] or FREEZE  : Sensor Freeze           -> Freeze T1 Transmitter Reading");
        System.out.println("  [7] or FAULT   : Controller Fault        -> Inject Software Exception [Forward Error Recovery]");
        System.out.println("  [8] or CROP    : Cycle Crop Profile      -> Switch Agronomic Setpoints (Pepper/Berry/Tea/Orchid)");
        System.out.println("  [9] or CSV     : Export Telemetry CSV    -> Snapshot Historian to plantation_telemetry_export.csv");
        System.out.println("  [R] or RESET   : Reset Field Bus         -> Restore all channels to NORMAL operation");
        System.out.println("  [M] or MENU    : Redisplay Menu          -> Print this engineering menu");
        System.out.println("  [Q] or QUIT    : Clean Shutdown          -> Terminate tasks and output Rate-Monotonic Audit");
        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.printf("  REST Status API : GET  http://localhost:%d/api/v1/status%n", httpPort);
        System.out.printf("  SSE Live Stream : GET  http://localhost:%d/api/v1/telemetry/stream%n", httpPort);
        System.out.printf("  Actuator Remote : POST http://localhost:%d/api/v1/control/actuator%n", httpPort);
        System.out.printf("  Audit Status    : GET  http://localhost:%d/api/v1/audit/status%n", httpPort);
        System.out.printf("  Prometheus Ex   : GET  http://localhost:%d/metrics%n", httpPort);
        System.out.printf("  Modbus/TCP Port : TCP  0.0.0.0:%d (Binary 10-Byte Transducer Protocol)%n", modbusPort);
        System.out.println("==========================================================================================================");
        System.out.println("Enter command below at any time (e.g., '1', 'BIAS', 'CROP', 'CSV', 'R', 'Q'):");
        System.out.println();
    }
}
