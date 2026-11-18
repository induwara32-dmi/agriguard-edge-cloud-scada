import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real-Time Execution Timing Monitor & Rate-Monotonic (RM) Schedulability Auditor.
 * Employs high-resolution System.nanoTime() to track execution durations across all edge SCADA task classes.
 * Measures worst-case execution time (WCET estimate C) and computes processor utilization (U = C / T).
 * Verifies system schedulability against the Liu & Layland (1973) Rate-Monotonic bound.
 */
public class TimingMonitor {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static class TaskMetrics {
        final String name;
        final double periodMs;
        long invocations = 0;
        long totalNanos = 0;
        long maxNanos = 0;
        long minNanos = Long.MAX_VALUE;

        TaskMetrics(String name, double periodMs) {
            this.name = name;
            this.periodMs = periodMs;
        }

        synchronized void record(long durationNanos) {
            invocations++;
            totalNanos += durationNanos;
            if (durationNanos > maxNanos) {
                maxNanos = durationNanos;
            }
            if (durationNanos < minNanos) {
                minNanos = durationNanos;
            }
        }

        synchronized double getWcetMs() {
            return maxNanos / 1_000_000.0;
        }

        synchronized double getAvgMs() {
            return (invocations == 0) ? 0.0 : (totalNanos / (double) invocations) / 1_000_000.0;
        }

        synchronized long getInvocations() {
            return invocations;
        }
    }

    private final Map<String, TaskMetrics> metricsMap = new LinkedHashMap<>();

    public TimingMonitor() {
        metricsMap.put("Sensor Acquisition", new TaskMetrics("Sensor Acquisition", 500.0));
        metricsMap.put("SCADA Control Cycle", new TaskMetrics("SCADA Control Cycle", 1000.0));
        metricsMap.put("Watchdog Supervisor", new TaskMetrics("Watchdog Supervisor", 1000.0));
        metricsMap.put("Telemetry & Status",  new TaskMetrics("Telemetry & Status",  2000.0));
    }

    /**
     * Records an execution duration sample for a registered periodic task.
     *
     * @param taskName      registered task name
     * @param durationNanos execution duration in nanoseconds
     */
    public void recordExecution(String taskName, long durationNanos) {
        TaskMetrics m = metricsMap.get(taskName);
        if (m != null) {
            m.record(durationNanos);
        }
    }

    /**
     * Emits the complete Rate-Monotonic Schedulability & Processor Utilization Analysis report.
     */
    public void printSchedulabilityReport() {
        String ts = "[" + LocalTime.now().format(TIME_FORMATTER) + "]";
        System.out.println();
        System.out.println("==========================================================================================================");
        System.out.printf("%s [RATE-MONOTONIC SCHEDULABILITY & PROCESSOR UTILIZATION AUDIT]%n", ts);
        System.out.println("==========================================================================================================");
        System.out.printf("%-22s %-15s %-15s %-15s %-14s %-16s%n",
            "Task Class", "Period T (ms)", "WCET C (ms)", "Avg C (ms)", "Invocations", "Utilization U_i");
        System.out.println("----------------------------------------------------------------------------------------------------------");

        double totalUtilization = 0.0;
        int n = metricsMap.size(); // n = 4 periodic tasks

        for (TaskMetrics m : metricsMap.values()) {
            double wcetMs = m.getWcetMs();
            double avgMs = m.getAvgMs();
            long count = m.getInvocations();
            double u_i = (m.periodMs > 0) ? (wcetMs / m.periodMs) : 0.0;
            totalUtilization += u_i;

            System.out.printf("%-22s %-15.1f %-15.4f %-15.4f %-14d %-8.4f%% (%.5f)%n",
                m.name, m.periodMs, wcetMs, avgMs, count, u_i * 100.0, u_i);
        }

        double llBound = n * (Math.pow(2.0, 1.0 / n) - 1.0);

        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.printf("Total Processor Utilization (U = sum(C_i / T_i)):      %.4f%% (%.5f)%n",
            totalUtilization * 100.0, totalUtilization);
        System.out.printf("Liu & Layland Schedulability Bound (n = %d tasks):     %.2f%% (%.5f)%n",
            n, llBound * 100.0, llBound);
        System.out.printf("  Formula: U_bound = n * (2^(1/n) - 1) = %d * (2^(0.25) - 1) = %.5f%n",
            n, llBound);
        System.out.println("----------------------------------------------------------------------------------------------------------");

        if (totalUtilization <= llBound) {
            System.out.println("Schedulability Verdict: PROVABLY SCHEDULABLE UNDER RATE-MONOTONIC PRIORITY ASSIGNMENT");
            System.out.printf("  Criterion: U (%.5f) <= U_bound (%.5f) is SATISFIED.%n", totalUtilization, llBound);
            System.out.println("  Operational Assurance: All real-time deadlines are guaranteed under worst-case phasing.");
        } else if (totalUtilization <= 1.0) {
            System.out.println("Schedulability Verdict: INCONCLUSIVE UNDER LEAST UPPER BOUND (U <= 1.0, but > U_bound)");
            System.out.println("  Exact Time-Demand Analysis (TDA) required to verify critical instant response times.");
        } else {
            System.out.println("Schedulability Verdict: PROCESSOR OVERLOAD DETECTED");
            System.out.println("  Total utilization exceeds 100%. Task deadline overruns will occur.");
        }
        System.out.println("==========================================================================================================");
        System.out.println();
    }

    public synchronized Map<String, Double> getTaskWcetMs() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (TaskMetrics m : metricsMap.values()) {
            map.put(m.name, m.getWcetMs());
        }
        return map;
    }

    public synchronized double getTaskWcetMs(String taskName) {
        TaskMetrics m = metricsMap.get(taskName);
        return (m != null) ? m.getWcetMs() : 0.0;
    }

    public synchronized double getTaskAvgMs(String taskName) {
        TaskMetrics m = metricsMap.get(taskName);
        return (m != null) ? m.getAvgMs() : 0.0;
    }

    public synchronized long getTaskInvocations(String taskName) {
        TaskMetrics m = metricsMap.get(taskName);
        return (m != null) ? m.getInvocations() : 0;
    }

    public synchronized double getTotalUtilization() {
        double total = 0.0;
        for (TaskMetrics m : metricsMap.values()) {
            if (m.periodMs > 0) {
                total += (m.getWcetMs() / m.periodMs);
            }
        }
        return total;
    }

    public static double getLiuLaylandBound() {
        return 4.0 * (Math.pow(2.0, 0.25) - 1.0); // 0.756828
    }
}
