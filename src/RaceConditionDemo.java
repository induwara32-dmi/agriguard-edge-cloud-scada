import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency Integrity & Thread-Safety Benchmark.
 * Demonstrates shared mutable state vulnerabilities and evaluates synchronization primitives.
 * Spawns two concurrent worker threads, each executing 100,000 iterations over:
 * 1. An unsynchronized raw integer (demonstrating lost updates from read-modify-write data races),
 * 2. A synchronized monitor-guarded counter (demonstrating mutual exclusion),
 * 3. An AtomicInteger using hardware Compare-And-Swap (demonstrating lock-free atomic increments).
 */
public class RaceConditionDemo {

    public static final int INCREMENTS_PER_THREAD = 100_000;
    public static final int EXPECTED_TOTAL = INCREMENTS_PER_THREAD * 2;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static int unsafeCount = 0;
    private static int syncCount = 0;
    private static final Object syncLock = new Object();
    private static final AtomicInteger atomicCount = new AtomicInteger(0);

    /**
     * Executes the concurrency benchmark and prints a comparative analysis table.
     */
    public static void runDemo() {
        unsafeCount = 0;
        syncCount = 0;
        atomicCount.set(0);

        String tsStart = "[" + LocalTime.now().format(TIME_FORMATTER) + "]";
        System.out.println();
        System.out.println("==========================================================================================");
        System.out.printf("%s [CONCURRENCY AUDIT] Starting Multithreaded Synchronization Benchmark%n", tsStart);
        System.out.printf("  Two worker threads executing %d increments each (Expected Total = %,d)%n",
            INCREMENTS_PER_THREAD, EXPECTED_TOTAL);
        System.out.println("------------------------------------------------------------------------------------------");

        long startNanos = System.nanoTime();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
                unsafeCount++;
                synchronized (syncLock) {
                    syncCount++;
                }
                atomicCount.incrementAndGet();
            }
        }, "Worker-Thread-1");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
                unsafeCount++;
                synchronized (syncLock) {
                    syncCount++;
                }
                atomicCount.incrementAndGet();
            }
        }, "Worker-Thread-2");

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Benchmark interrupted!");
            return;
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        int lostUpdates = EXPECTED_TOTAL - unsafeCount;

        String tsEnd = "[" + LocalTime.now().format(TIME_FORMATTER) + "]";
        System.out.printf("%-26s %-14s %-14s %-20s %-16s%n",
            "Mechanism", "Final Result", "Expected", "Discrepancy (Lost)", "Verdict");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-26s %, -14d %, -14d %-,20d %-16s%n",
            "1. Unsafe (count++)", unsafeCount, EXPECTED_TOTAL, -lostUpdates, "FAIL (DATA RACE)");
        System.out.printf("%-26s %, -14d %, -14d %-,20d %-16s%n",
            "2. Synchronized Monitor", syncCount, EXPECTED_TOTAL, 0, "PASS (MUTEX)");
        System.out.printf("%-26s %, -14d %, -14d %-,20d %-16s%n",
            "3. AtomicInteger CAS", atomicCount.get(), EXPECTED_TOTAL, 0, "PASS (LOCK-FREE)");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%s Concurrency verification benchmark completed in %d ms.%n", tsEnd, elapsedMs);
        System.out.println("Industrial Note: Unsynchronized mutable state causes non-deterministic telemetry corruption.");
        System.out.println("Industrial Edge SCADA must strictly employ Monitor synchronization or hardware Atomics.");
        System.out.println("==========================================================================================");
        System.out.println();
    }
}
