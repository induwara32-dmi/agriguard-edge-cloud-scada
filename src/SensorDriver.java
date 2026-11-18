/**
 * Hardware Abstraction Layer (HAL) interface for field telemetry channels.
 * Decouples real-time sampling and voting algorithms from concrete bus hardware
 * (Modbus RTU/TCP, MQTT edge brokers, SDI-12, or local simulation).
 */
public interface SensorDriver {

    /**
     * Reads the current analog/digital measurement for the specified field channel.
     *
     * @param channelId channel identifier (e.g., "T1", "T2", "T3", "H1", "S1")
     * @return engineering unit reading, or Double.NaN if channel is disconnected/unresponsive
     */
    double readChannel(String channelId);

    /**
     * Verifies if the field channel is responsive and operational.
     *
     * @param channelId channel identifier
     * @return true if responsive, false if disconnected or in bus timeout
     */
    boolean isChannelResponsive(String channelId);
}
