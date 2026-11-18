import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Industrial Real-Time Watchdog Supervisor.
 * Executes every 1000 ms to enforce maximum heartbeat timeouts (1500 ms) across all field channels.
 * Detects sensor bus dropouts, wiring disconnections, and transmitter crash failures within 2.5 seconds.
 * Orchestrates plant-wide Fail-Safe Interlock state transitions with millisecond-precision audit logs.
 */
public class WatchdogMonitor implements Runnable {

    public static final long HEARTBEAT_TIMEOUT_MS = 1500;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ConcurrentHashMap<String, Long> lastHeartbeats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> channelActive = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong omissionCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final TimingMonitor timingMonitor;
    private final long timeoutMs;

    private volatile boolean safeState = false;

    public WatchdogMonitor(TimingMonitor timingMonitor) {
        this(HEARTBEAT_TIMEOUT_MS, timingMonitor);
    }

    public WatchdogMonitor(long timeoutMs) {
        this(timeoutMs, null);
    }

    public WatchdogMonitor(long timeoutMs, TimingMonitor timingMonitor) {
        this.timeoutMs = timeoutMs;
        this.timingMonitor = timingMonitor;
    }

    /**
     * Registers a field channel with initial timestamp to prevent cold-start false alarms.
     *
     * @param channelId unique channel identifier (e.g., T1, T2, T3, H1, S1)
     */
    public void registerSensor(String channelId) {
        long now = System.currentTimeMillis();
        lastHeartbeats.put(channelId, now);
        channelActive.put(channelId, true);
    }

    /**
     * Records a fresh telemetry heartbeat from a field transducer.
     * Automatically clears channel failure state when communications are restored.
     *
     * @param channelId   unique channel identifier
     * @param timestampMs acquisition epoch millisecond timestamp
     */
    public void heartbeat(String channelId) {
        heartbeat(channelId, System.currentTimeMillis());
    }

    public void heartbeat(String channelId, long timestampMs) {
        lastHeartbeats.put(channelId, timestampMs);
        Boolean wasActive = channelActive.put(channelId, true);
        if (Boolean.FALSE.equals(wasActive)) {
            System.out.printf("%s [WATCHDOG] Channel %s RECONNECTED (telemetry link restored)%n",
                getTimestamp(), channelId);
        }
    }

    public void checkDeadlines() {
        run();
    }

    /**
     * Periodic watchdog scan running every 1000 ms.
     * Evaluates channel freshness against configured timeoutMs.
     */
    @Override
    public void run() {
        long startNanos = System.nanoTime();
        long now = System.currentTimeMillis();

        try {
            for (Map.Entry<String, Long> entry : lastHeartbeats.entrySet()) {
                String channelId = entry.getKey();
                long lastTime = entry.getValue();
                long elapsed = now - lastTime;

                if (elapsed > timeoutMs) {
                    Boolean wasActive = channelActive.put(channelId, false);
                    if (Boolean.TRUE.equals(wasActive)) {
                        omissionCount.incrementAndGet();
                        System.out.printf("%s [WATCHDOG] Channel %s TIMEOUT (silent for %d ms > %d ms limit)%n",
                            getTimestamp(), channelId, elapsed, timeoutMs);
                    }
                }
            }

            // If temperature channel redundancy is completely lost, trigger fail-safe interlock
            if (!isSensorAlive("T1") && !isSensorAlive("T2") && !isSensorAlive("T3") && !lastHeartbeats.isEmpty()) {
                enterSafeState("Loss of all temperature telemetry channels");
            }
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            if (timingMonitor != null) {
                timingMonitor.recordExecution("Watchdog Supervisor", durationNanos);
            }
        }
    }

    /**
     * Activates the emergency Fail-Safe State if not already active.
     *
     * @param reason diagnostic cause for safety trip
     */
    public synchronized void enterSafeState(String reason) {
        if (!safeState) {
            safeState = true;
            System.out.printf("%s [FAIL-SAFE INTERLOCK] ACTIVATED -> Reason: %s%n", getTimestamp(), reason);
        }
    }

    /**
     * Clears the emergency Fail-Safe State when field conditions return to normal.
     *
     * @param reason diagnostic cause for clearing
     */
    public synchronized void exitSafeState(String reason) {
        if (safeState) {
            safeState = false;
            System.out.printf("%s [FAIL-SAFE INTERLOCK] CLEARED   -> Reason: %s%n", getTimestamp(), reason);
        }
    }

    public boolean isSafeState() {
        return safeState;
    }

    public boolean isSensorAlive(String channelId) {
        return channelActive.getOrDefault(channelId, false);
    }

    public long getWatchdogFailuresTotal() {
        return omissionCount.get();
    }

    public static String getTimestamp() {
        return "[" + LocalTime.now().format(TIME_FORMATTER) + "]";
    }
}
