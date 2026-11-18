import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Industrial Telemetry Historian & Cryptographic Tamper-Evident Audit Chain.
 * Non-blockingly records edge sensor metrics, TMR consensus, calculated VPD,
 * actuator states, and safety trip events to disk in standard CSV format with
 * a continuous SHA-256 hash chain (blockchain-style ledger integrity).
 *
 * Each row appends:
 * - PrevHash: SHA-256 digest of the preceding row (Genesis seed: 64 zeros)
 * - RowHash: SHA-256(PrevHash + "|" + RowPayload)
 *
 * Features:
 * - Dedicated low-priority background daemon thread eliminating filesystem I/O jitter.
 * - Dynamic recovery of latest block hash across application restarts.
 * - Deterministic, standalone audit verification tool via {@link #verifyAuditChain(Path)}.
 */
public class TelemetryLogger {

    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String CSV_HEADER =
        "Timestamp,IsoDateTime,CropProfile,VotedTemp_C,VoteStatus,Humidity_Pct,SoilMoisture_Pct,VPD_kPa," +
        "Heater,Fan,Pump,Fogger,Alarm,ControlMode,SafeState,EventNotes,PrevHash,RowHash";

    public static final String DEFAULT_LOG_FILE = "logs/plantation_telemetry.csv";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(5000);
    private final File logFile;
    private volatile boolean running = true;
    private final Thread writerThread;

    private volatile String currentLastHash = GENESIS_HASH;
    private volatile long rowCount = 0;

    public TelemetryLogger() {
        this(DEFAULT_LOG_FILE);
    }

    public TelemetryLogger(String filePath) {
        this.logFile = new File(filePath);
        initStorage();

        writerThread = new Thread(this::processQueue, "TelemetryHistorian-Writer");
        writerThread.setDaemon(true);
        writerThread.setPriority(Thread.MIN_PRIORITY);
        writerThread.start();
    }

    private void initStorage() {
        File parentDir = logFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (!logFile.exists() || logFile.length() == 0) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, false))) {
                bw.write(CSV_HEADER);
                bw.newLine();
                bw.flush();
            } catch (IOException e) {
                System.err.printf("[HISTORIAN] Failed to write CSV header: %s%n", e.getMessage());
            }
            this.currentLastHash = GENESIS_HASH;
            this.rowCount = 0;
        } else {
            // Existing log file detected: scan to recover latest ledger block hash
            recoverLatestHash();
        }
    }

    private void recoverLatestHash() {
        String lastLine = null;
        long count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lastLine = line.trim();
                    count++;
                }
            }
        } catch (IOException e) {
            System.err.printf("[HISTORIAN] Error recovering ledger tail: %s%n", e.getMessage());
        }

        this.rowCount = count;
        if (lastLine != null) {
            int lastComma = lastLine.lastIndexOf(',');
            if (lastComma > 0 && lastComma + 1 < lastLine.length()) {
                String candidateHash = lastLine.substring(lastComma + 1).trim();
                if (candidateHash.length() == 64) {
                    this.currentLastHash = candidateHash;
                    System.out.printf("[HISTORIAN] Resumed existing audit ledger (%d rows). Tail Hash: %s...%n",
                        count, candidateHash.substring(0, 16));
                    return;
                }
            }
        }
        this.currentLastHash = GENESIS_HASH;
    }

    /**
     * Non-blocking telemetry ingestion method called from real-time control loops.
     */
    public void recordPoint(
            CropProfile profile,
            double temp,
            String voteStatus,
            double humidity,
            double soil,
            double vpd,
            boolean heater,
            boolean fan,
            boolean pump,
            boolean fogger,
            boolean alarm,
            String controlMode,
            boolean safeState,
            String notes) {

        String timeStr = LocalTime.now().format(TIME_FMT);
        String isoTime = Instant.now().toString();

        String payload = String.format("%s,%s,%s,%.2f,%s,%.1f,%.1f,%.3f,%s,%s,%s,%s,%s,%s,%s,\"%s\"",
            timeStr,
            isoTime,
            profile.name(),
            temp,
            voteStatus,
            humidity,
            soil,
            vpd,
            heater ? "ON" : "OFF",
            fan ? "ON" : "OFF",
            pump ? "ON" : "OFF",
            fogger ? "ON" : "OFF",
            alarm ? "ON" : "OFF",
            controlMode,
            safeState ? "TRIPPED" : "NOMINAL",
            (notes == null) ? "" : notes.replace("\"", "'")
        );

        // Offer non-blockingly; if queue is full, drop oldest point to prioritize real-time guarantees
        if (!logQueue.offer(payload)) {
            logQueue.poll();
            logQueue.offer(payload);
        }
    }

    private void processQueue() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
            while (running || !logQueue.isEmpty()) {
                String payload = logQueue.poll(200, TimeUnit.MILLISECONDS);
                if (payload != null) {
                    String prevHash = currentLastHash;
                    String rowHash = computeSha256(prevHash + "|" + payload);

                    String chainedLine = payload + "," + prevHash + "," + rowHash;
                    bw.write(chainedLine);
                    bw.newLine();

                    currentLastHash = rowHash;
                    rowCount++;

                    if (logQueue.isEmpty()) {
                        bw.flush();
                    }
                }
            }
            bw.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.printf("[HISTORIAN] Disk I/O Exception: %s%n", e.getMessage());
        }
    }

    public void flush() {
        int retries = 0;
        while (!logQueue.isEmpty() && retries++ < 20) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 digest unavailable in standard library", e);
        }
    }

    /**
     * Cryptographically audits the target CSV file from start to finish.
     * Verifies that:
     * 1. Row 1 PrevHash matches {@link #GENESIS_HASH}.
     * 2. For every Row i > 1, PrevHash_i matches RowHash_{i-1}.
     * 3. For every Row, RowHash matches SHA-256(PrevHash + "|" + Payload).
     *
     * @param csvPath Path to the CSV audit ledger file
     * @return true if ledger integrity is completely intact, false if tampered or corrupt
     */
    public static boolean verifyAuditChain(Path csvPath) {
        return verifyAuditChain(csvPath, true);
    }

    public static boolean verifyAuditChain(Path csvPath, boolean verbose) {
        if (verbose) {
            System.out.println("================================================================================");
            System.out.println("          AGRIGUARD RT-SCADA: CRYPTOGRAPHIC AUDIT TRAIL VERIFICATION           ");
            System.out.println("================================================================================");
            System.out.printf("Target Audit Trail : %s%n", csvPath.toAbsolutePath());
            System.out.printf("Genesis Hash Seed  : %s%n", GENESIS_HASH);
            System.out.printf("Hashing Algorithm  : SHA-256 (FIPS 180-4 Standard)%n");
            System.out.println("--------------------------------------------------------------------------------");
        }

        if (!Files.exists(csvPath)) {
            if (verbose) System.err.printf("[AUDIT FAILED] File does not exist: %s%n", csvPath);
            return false;
        }

        long count = 0;
        String expectedPrevHash = GENESIS_HASH;

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                if (verbose) System.out.println("[AUDIT PASSED] Empty ledger file (zero records).");
                return true;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                count++;

                int lastComma = line.lastIndexOf(',');
                if (lastComma < 0) {
                    if (verbose) System.err.printf("[AUDIT FAILED] Malformed row #%d (missing comma delimiters): %s%n", count, line);
                    return false;
                }
                int prevComma = line.lastIndexOf(',', lastComma - 1);
                if (prevComma < 0) {
                    if (verbose) System.err.printf("[AUDIT FAILED] Malformed row #%d (missing hash fields): %s%n", count, line);
                    return false;
                }

                String payload = line.substring(0, prevComma);
                String recordedPrevHash = line.substring(prevComma + 1, lastComma).trim();
                String recordedRowHash = line.substring(lastComma + 1).trim();

                // Check 1: PrevHash linkage check
                if (!recordedPrevHash.equalsIgnoreCase(expectedPrevHash)) {
                    if (verbose) {
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        System.err.printf("[TAMPER DETECTED] Broken chain linkage at Record #%d!%n", count);
                        System.err.printf("  Expected PrevHash : %s%n", expectedPrevHash);
                        System.err.printf("  Recorded PrevHash : %s%n", recordedPrevHash);
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    }
                    return false;
                }

                // Check 2: Digest recomputation check
                String recomputedHash = computeSha256(recordedPrevHash + "|" + payload);
                if (!recomputedHash.equalsIgnoreCase(recordedRowHash)) {
                    if (verbose) {
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        System.err.printf("[TAMPER DETECTED] Payload digest mismatch at Record #%d!%n", count);
                        System.err.printf("  Payload           : %s%n", payload);
                        System.err.printf("  Recorded Digest   : %s%n", recordedRowHash);
                        System.err.printf("  Recomputed Digest : %s%n", recomputedHash);
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    }
                    return false;
                }

                if (verbose && (count <= 3 || count % 20 == 0)) {
                    System.out.printf("  [VERIFIED] Block #%04d | PrevHash: %s... | RowHash: %s...%n",
                        count, recordedPrevHash.substring(0, 10), recordedRowHash.substring(0, 10));
                }

                expectedPrevHash = recordedRowHash;
            }

        } catch (IOException e) {
            if (verbose) System.err.printf("[AUDIT FAILED] Read exception during ledger scan: %s%n", e.getMessage());
            return false;
        }

        if (verbose) {
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Audit Trail Verification Summary:");
            System.out.printf("  Total Records Checked : %d%n", count);
            System.out.printf("  Chain Genesis Anchor  : %s%n", GENESIS_HASH);
            System.out.printf("  Latest Ledger Hash    : %s%n", expectedPrevHash);
            System.out.println("  Tamper Detection      : ZERO INTEGRITY ANOMALIES DETECTED");
            System.out.println("  Ledger Status         : CRYPTOGRAPHICALLY VALID & VERIFIED (PASSED)");
            System.out.println("================================================================================");
        }
        return true;
    }

    public boolean exportAuditLog(File destination) {
        try {
            if (destination.getParentFile() != null) {
                destination.getParentFile().mkdirs();
            }
            Files.copy(logFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.printf("[HISTORIAN] Export failed: %s%n", e.getMessage());
            return false;
        }
    }

    public String getCurrentLastHash() {
        return currentLastHash;
    }

    public long getRowCount() {
        return rowCount;
    }

    public File getLogFile() {
        return logFile;
    }

    /**
     * Reads backfill records from disk for offline-first store-and-forward edge sync.
     * Streams missed SHA-256 hash-chained records starting after `sinceBlock`.
     */
    public String getBackfillJson(long sinceBlock, int limit, String zoneId) {
        flush();
        int safeLimit = Math.max(1, Math.min(500, limit));
        long currentTotal = getRowCount();
        long startBlock = Math.max(0, sinceBlock);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format(java.util.Locale.US, "  \"zoneId\": \"%s\",\n", (zoneId != null ? zoneId : "ZONE-01")));
        sb.append(String.format(java.util.Locale.US, "  \"totalRecords\": %d,\n", currentTotal));
        sb.append(String.format(java.util.Locale.US, "  \"sinceBlock\": %d,\n", startBlock));

        java.util.List<String> recordsJson = new java.util.ArrayList<>();
        long lastReadBlock = startBlock;

        if (logFile != null && logFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile, StandardCharsets.UTF_8))) {
                String header = reader.readLine(); // Skip CSV header
                long blockNum = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    blockNum++;
                    if (blockNum <= startBlock) {
                        continue;
                    }
                    if (recordsJson.size() >= safeLimit) {
                        break;
                    }

                    int lastComma = line.lastIndexOf(',');
                    if (lastComma < 0) continue;
                    int prevComma = line.lastIndexOf(',', lastComma - 1);
                    if (prevComma < 0) continue;

                    String rowHash = line.substring(lastComma + 1).trim();
                    String prevHash = line.substring(prevComma + 1, lastComma).trim();
                    String payload = line.substring(0, prevComma);

                    String[] tokens = parseCsvLine(payload);
                    if (tokens.length >= 15) {
                        String time = tokens[0];
                        String iso = tokens[1];
                        String profile = tokens[2];
                        double temp = parseDoubleSafe(tokens[3]);
                        String voteStatus = tokens[4];
                        double hum = parseDoubleSafe(tokens[5]);
                        double soil = parseDoubleSafe(tokens[6]);
                        double vpd = parseDoubleSafe(tokens[7]);
                        boolean heater = "ON".equalsIgnoreCase(tokens[8]);
                        boolean fan = "ON".equalsIgnoreCase(tokens[9]);
                        boolean pump = "ON".equalsIgnoreCase(tokens[10]);
                        boolean fogger = "ON".equalsIgnoreCase(tokens[11]);
                        boolean alarm = "ON".equalsIgnoreCase(tokens[12]);
                        String mode = tokens[13];
                        boolean safe = "TRIPPED".equalsIgnoreCase(tokens[14]);
                        String notes = (tokens.length > 15) ? tokens[15] : "";

                        String rec = String.format(java.util.Locale.US,
                            "    {\n" +
                            "      \"block\": %d,\n" +
                            "      \"timestamp\": \"%s\",\n" +
                            "      \"isoDateTime\": \"%s\",\n" +
                            "      \"cropProfile\": \"%s\",\n" +
                            "      \"votedTemp\": %.2f,\n" +
                            "      \"voteStatus\": \"%s\",\n" +
                            "      \"humidity\": %.1f,\n" +
                            "      \"soilMoisture\": %.1f,\n" +
                            "      \"vpd\": %.3f,\n" +
                            "      \"heater\": %b,\n" +
                            "      \"fan\": %b,\n" +
                            "      \"pump\": %b,\n" +
                            "      \"fogger\": %b,\n" +
                            "      \"alarm\": %b,\n" +
                            "      \"controlMode\": \"%s\",\n" +
                            "      \"safeState\": %b,\n" +
                            "      \"eventNotes\": \"%s\",\n" +
                            "      \"prevHash\": \"%s\",\n" +
                            "      \"rowHash\": \"%s\"\n" +
                            "    }",
                            blockNum, time, iso, profile, temp, voteStatus, hum, soil, vpd,
                            heater, fan, pump, fogger, alarm, mode, safe,
                            notes.replace("\"", "'"), prevHash, rowHash
                        );
                        recordsJson.add(rec);
                        lastReadBlock = blockNum;
                    }
                }
            } catch (IOException e) {
                System.err.printf("[HISTORIAN] Backfill read error: %s%n", e.getMessage());
            }
        }

        boolean hasMore = (lastReadBlock < currentTotal);
        long nextBlock = lastReadBlock + 1;

        sb.append(String.format(java.util.Locale.US, "  \"returnedCount\": %d,\n", recordsJson.size()));
        sb.append(String.format(java.util.Locale.US, "  \"hasMore\": %b,\n", hasMore));
        sb.append(String.format(java.util.Locale.US, "  \"nextBlock\": %d,\n", nextBlock));
        sb.append("  \"records\": [\n");
        sb.append(String.join(",\n", recordsJson));
        sb.append("\n  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private static double parseDoubleSafe(String val) {
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String[] parseCsvLine(String line) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    public void close() {
        running = false;
        try {
            // Give writer up to 1 second to flush queue
            if (writerThread != null) {
                writerThread.join(1000);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
