import java.util.List;

/**
 * Triple Modular Redundancy (TMR) Inexact Majority Voter.
 * Processes triple-redundant canopy temperature channels (T1, T2, T3)
 * using an inexact consensus window of ±1.0°C.
 * Discards transient sensor outliers, provides degraded dual-channel consensus,
 * and signals fail-safe trip on consensus loss.
 */
public class TMRVoter {

    public static final double TOLERANCE_CELSIUS = 1.0;

    public enum VoteStatus {
        OK,       // Full consensus across all 3 redundant channels (±1.0°C)
        MASKED,   // 3 channels online: 2 agree within ±1.0°C, 1 outlier isolated & masked
        DEGRADED, // Exactly 2 channels online and agreeing within ±1.0°C
        FAILED    // Fewer than 2 channels online or disagreement exceeding tolerance
    }

    public record VoteResult(VoteStatus status, double temperature, String detail) {
        @Override
        public String toString() {
            return String.format("[%s] Temp: %.2f°C (%s)", status, temperature, detail);
        }

        public String getDisplayLabel() {
            return switch (status) {
                case OK -> "CONSENSUS OK";
                case MASKED -> "OUTLIER MASKED";
                case DEGRADED -> "DEGRADED (2-CH)";
                case FAILED -> "CONSENSUS LOSS";
            };
        }
    }

    /**
     * Conducts inexact majority voting on the list of active temperature sensor readings.
     *
     * @param aliveReadings active channel readings
     * @return VoteResult containing consensus temperature and diagnostic classification
     */
    public static VoteResult vote(List<SensorReading> aliveReadings) {
        if (aliveReadings == null || aliveReadings.size() < 2) {
            int count = (aliveReadings == null) ? 0 : aliveReadings.size();
            return new VoteResult(
                VoteStatus.FAILED,
                0.0,
                String.format("Insufficient active channels (%d online, minimum 2 required)", count)
            );
        }

        // Case 1: Exactly 2 channels online
        if (aliveReadings.size() == 2) {
            SensorReading s1 = aliveReadings.get(0);
            SensorReading s2 = aliveReadings.get(1);
            double diff = Math.abs(s1.value() - s2.value());

            if (diff <= TOLERANCE_CELSIUS) {
                double avg = (s1.value() + s2.value()) / 2.0;
                return new VoteResult(
                    VoteStatus.DEGRADED,
                    avg,
                    String.format("2-channel consensus: %s=%.2f, %s=%.2f (diff=%.2f°C <= %.1f°C)",
                        s1.sensorId(), s1.value(), s2.sensorId(), s2.value(), diff, TOLERANCE_CELSIUS)
                );
            } else {
                return new VoteResult(
                    VoteStatus.FAILED,
                    0.0,
                    String.format("Dual-channel disagreement: %s=%.2f vs %s=%.2f (diff=%.2f°C > %.1f°C)",
                        s1.sensorId(), s1.value(), s2.sensorId(), s2.value(), diff, TOLERANCE_CELSIUS)
                );
            }
        }

        // Case 2: 3 or more channels online
        SensorReading s1 = aliveReadings.get(0);
        SensorReading s2 = aliveReadings.get(1);
        SensorReading s3 = aliveReadings.get(2);

        double v1 = s1.value();
        double v2 = s2.value();
        double v3 = s3.value();

        double d12 = Math.abs(v1 - v2);
        double d13 = Math.abs(v1 - v3);
        double d23 = Math.abs(v2 - v3);

        boolean a12 = d12 <= TOLERANCE_CELSIUS;
        boolean a13 = d13 <= TOLERANCE_CELSIUS;
        boolean a23 = d23 <= TOLERANCE_CELSIUS;

        // Sub-case 2A: Mutual consensus across all 3 channels
        if (a12 && a13 && a23) {
            double avg = (v1 + v2 + v3) / 3.0;
            return new VoteResult(
                VoteStatus.OK,
                avg,
                String.format("All 3 channels agree: %s=%.2f, %s=%.2f, %s=%.2f",
                    s1.sensorId(), v1, s2.sensorId(), v2, s3.sensorId(), v3)
            );
        }

        // Sub-case 2B: 2 channels agree, 1 outlier isolated and masked
        double minDiff = Double.MAX_VALUE;
        int bestPair = 0;

        if (a12 && d12 < minDiff) { minDiff = d12; bestPair = 12; }
        if (a13 && d13 < minDiff) { minDiff = d13; bestPair = 13; }
        if (a23 && d23 < minDiff) { minDiff = d23; bestPair = 23; }

        if (bestPair == 12) {
            double avg = (v1 + v2) / 2.0;
            return new VoteResult(
                VoteStatus.MASKED,
                avg,
                String.format("Outlier %s=%.2f masked; %s=%.2f & %s=%.2f in consensus (diff=%.2f°C)",
                    s3.sensorId(), v3, s1.sensorId(), v1, s2.sensorId(), v2, d12)
            );
        } else if (bestPair == 13) {
            double avg = (v1 + v3) / 2.0;
            return new VoteResult(
                VoteStatus.MASKED,
                avg,
                String.format("Outlier %s=%.2f masked; %s=%.2f & %s=%.2f in consensus (diff=%.2f°C)",
                    s2.sensorId(), v2, s1.sensorId(), v1, s3.sensorId(), v3, d13)
            );
        } else if (bestPair == 23) {
            double avg = (v2 + v3) / 2.0;
            return new VoteResult(
                VoteStatus.MASKED,
                avg,
                String.format("Outlier %s=%.2f masked; %s=%.2f & %s=%.2f in consensus (diff=%.2f°C)",
                    s1.sensorId(), v1, s2.sensorId(), v2, s3.sensorId(), v3, d23)
            );
        }

        // Sub-case 2C: Disagreement across all channel pairs
        return new VoteResult(
            VoteStatus.FAILED,
            0.0,
            String.format("No agreeing channel pair: %s=%.2f, %s=%.2f, %s=%.2f (d12=%.2f, d13=%.2f, d23=%.2f > %.1f°C)",
                s1.sensorId(), v1, s2.sensorId(), v2, s3.sensorId(), v3, d12, d13, d23, TOLERANCE_CELSIUS)
        );
    }
}
