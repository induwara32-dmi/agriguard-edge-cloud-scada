/**
 * Periodic field channel acquisition task executing every 500 ms.
 * Communicates through the SensorDriver hardware abstraction layer to acquire
 * analog/digital field telemetry across fieldbus channels.
 * Validates channel response, pushes valid telemetry packets into the bounded monitor buffer,
 * and emits real-time heartbeat pulses to the Watchdog Supervisor.
 */
public class SensorTask implements Runnable {

    private final String sensorId;
    private final SensorReading.SensorType sensorType;
    private final SensorDriver driver;
    private final BoundedBuffer<SensorReading> buffer;
    private final WatchdogMonitor watchdog;
    private final TimingMonitor timingMonitor;

    public SensorTask(
            String sensorId,
            SensorReading.SensorType sensorType,
            SensorDriver driver,
            BoundedBuffer<SensorReading> buffer,
            WatchdogMonitor watchdog,
            TimingMonitor timingMonitor) {
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.driver = driver;
        this.buffer = buffer;
        this.watchdog = watchdog;
        this.timingMonitor = timingMonitor;

        this.watchdog.registerSensor(sensorId);
    }

    @Override
    public void run() {
        long startNanos = System.nanoTime();

        try {
            // Read from field bus hardware abstraction layer
            if (!driver.isChannelResponsive(sensorId)) {
                // Channel is disconnected or in timeout: omit telemetry and heartbeat
                return;
            }

            double measuredVal = driver.readChannel(sensorId);
            if (Double.isNaN(measuredVal)) {
                return;
            }

            long nowMs = System.currentTimeMillis();
            SensorReading reading = new SensorReading(sensorId, sensorType, measuredVal, nowMs);

            try {
                buffer.put(reading);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Pulse heartbeat to Watchdog Supervisor
            watchdog.heartbeat(sensorId, nowMs);

        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            timingMonitor.recordExecution("Sensor sampling", durationNanos);
        }
    }

    public String getSensorId() {
        return sensorId;
    }

    public SensorReading.SensorType getSensorType() {
        return sensorType;
    }
}
