/**
 * Immutable telemetry carrier representing an acquired field channel reading.
 * Encapsulates channel identifier, environmental physical metric type,
 * engineering value, and system acquisition timestamp in epoch milliseconds.
 */
public record SensorReading(
    String sensorId,
    SensorType type,
    double value,
    long timestampMs
) {
    public enum SensorType {
        TEMPERATURE,
        HUMIDITY,
        SOIL_MOISTURE
    }

    @Override
    public String toString() {
        return String.format("%s (%s): %.2f at %d ms", sensorId, type, value, timestampMs);
    }
}
