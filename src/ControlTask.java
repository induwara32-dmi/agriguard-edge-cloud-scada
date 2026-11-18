import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Industrial Edge SCADA Primary Control Loop scheduled every 1000 ms.
 * Implements deterministic closed-loop microclimate regulation:
 * 1. Non-blockingly drains telemetry from the BoundedBuffer monitor.
 * 2. Validates channel health against the Watchdog Supervisor.
 * 3. Executes Triple Modular Redundancy (TMR) inexact majority voting on redundant canopy channels.
 * 4. Computes live Vapor Pressure Deficit (VPD) in kPa.
 * 5. Applies agronomic setpoint rules (heating, ventilation, drip irrigation, misting) with hysteresis.
 * 6. Enforces Critical Safety Interlocks (thermal cutoff, emergency siren, fail-safe trip).
 * 7. Dispatches telemetry point to the Historian and advances field bus dynamics.
 */
public class ControlTask implements Runnable {

    private final BoundedBuffer<SensorReading> buffer;
    private final ActuatorManager actuators;
    private final WatchdogMonitor watchdog;
    private final SimulatedFieldBus fieldBus;
    private final TimingMonitor timingMonitor;
    private final TelemetryLogger telemetryLogger;

    private volatile CropProfile activeProfile = CropProfile.BELL_PEPPER;

    private final Map<String, SensorReading> lastValidReadings = new ConcurrentHashMap<>();
    private volatile double lastValidTemperature = 24.5;
    private volatile TMRVoter.VoteResult latestVoteResult =
        new TMRVoter.VoteResult(TMRVoter.VoteStatus.OK, 24.5, "Initial nominal consensus");
    private volatile double latestHumidity = 68.0;
    private volatile double latestSoilMoisture = 48.0;
    private volatile double latestVpd = 1.05;
    private final AgronomyIntelligence agronomyEngine = new AgronomyIntelligence();
    private volatile AgronomyIntelligence.AgronomySnapshot latestAgronomySnapshot =
        agronomyEngine.evaluate(24.5, 68.0, 48.0, 1.05, CropProfile.BELL_PEPPER);
    private final AtomicLong tmrMaskedCount = new AtomicLong(0);

    private volatile boolean simulateException = false;

    public ControlTask(
            BoundedBuffer<SensorReading> buffer,
            ActuatorManager actuators,
            WatchdogMonitor watchdog,
            SimulatedFieldBus fieldBus,
            TimingMonitor timingMonitor,
            TelemetryLogger telemetryLogger) {
        this.buffer = buffer;
        this.actuators = actuators;
        this.watchdog = watchdog;
        this.fieldBus = fieldBus;
        this.timingMonitor = timingMonitor;
        this.telemetryLogger = telemetryLogger;
    }

    @Override
    public void run() {
        long startNanos = System.nanoTime();
        String diagnosticNotes = "Nominal cycle";

        try {
            if (simulateException) {
                simulateException = false;
                throw new RuntimeException("Simulated controller software fault injected by operator");
            }

            // 1. Drain all available packets non-blockingly from monitor buffer
            SensorReading reading;
            while ((reading = buffer.poll()) != null) {
                lastValidReadings.put(reading.sensorId(), reading);
            }

            // 2. Filter temperature channels verified active by Watchdog Supervisor
            List<SensorReading> aliveTempReadings = new ArrayList<>();
            for (String tid : List.of("T1", "T2", "T3")) {
                if (watchdog.isSensorAlive(tid)) {
                    SensorReading sr = lastValidReadings.get(tid);
                    if (sr != null) {
                        aliveTempReadings.add(sr);
                    }
                }
            }

            // 3. Perform TMR Inexact Majority Voting
            TMRVoter.VoteResult voteResult = TMRVoter.vote(aliveTempReadings);

            // 4. Evaluate Safety Integrity & Fail-Safe State
            if (voteResult.status() == TMRVoter.VoteStatus.FAILED) {
                this.latestVoteResult = new TMRVoter.VoteResult(
                    TMRVoter.VoteStatus.FAILED,
                    lastValidTemperature,
                    voteResult.detail()
                );
                diagnosticNotes = "Fail-Safe trip: " + voteResult.detail();
                watchdog.enterSafeState("TMR consensus loss: " + voteResult.detail());
                actuators.applySafeState("TMR Failure: " + voteResult.detail());
            } else {
                if (voteResult.status() == TMRVoter.VoteStatus.MASKED) {
                    tmrMaskedCount.incrementAndGet();
                }
                this.lastValidTemperature = voteResult.temperature();
                this.latestVoteResult = voteResult;

                double votedTemp = voteResult.temperature();

                // Telemetry acquisition for humidity and soil moisture
                SensorReading humReading = lastValidReadings.get("H1");
                double humidity = (humReading != null && watchdog.isSensorAlive("H1")) ? humReading.value() : 68.0;
                this.latestHumidity = humidity;

                SensorReading soilReading = lastValidReadings.get("S1");
                double soil = (soilReading != null && watchdog.isSensorAlive("S1")) ? soilReading.value() : 48.0;
                this.latestSoilMoisture = soil;

                // 5. Calculate Vapor Pressure Deficit (VPD) and Predictive Agronomy
                double vpd = CropProfile.calculateVPD(votedTemp, humidity);
                this.latestVpd = vpd;

                // 6. Closed-Loop Agronomic Regulation based on active CropProfile
                CropProfile profile = this.activeProfile;
                this.latestAgronomySnapshot = agronomyEngine.evaluate(
                    votedTemp,
                    humidity,
                    soil,
                    vpd,
                    profile
                );

                // A. Critical High Temperature Interlock
                if (votedTemp > profile.getCriticalHighTemp()) {
                    watchdog.enterSafeState(String.format("Critical Thermal Threshold Exceeded (%.1f°C > %.1f°C)",
                        votedTemp, profile.getCriticalHighTemp()));
                    actuators.applySafeState(String.format("Critical Thermal Threshold Exceeded (%.1f°C > %.1f°C)",
                        votedTemp, profile.getCriticalHighTemp()));
                    diagnosticNotes = "CRITICAL THERMAL HIGH: Safe State Active";
                } else {
                    if (watchdog.isSafeState()) {
                        watchdog.exitSafeState(String.format("Canopy condition restored (%s at %.2f°C)",
                            voteResult.status(), votedTemp));
                        actuators.clearSafeState("Canopy condition restored");
                    }
                    actuators.setAutoAlarm(false, String.format("Temperature normal (%.1f°C <= %.1f°C)",
                        votedTemp, profile.getCriticalHighTemp()));

                    // B. Heating Element Control with 2.0°C deadband hysteresis
                    if (votedTemp < profile.getMinTemp()) {
                        actuators.setAutoHeater(true, String.format("Canopy temp low (%.1f°C < %.1f°C target)",
                            votedTemp, profile.getMinTemp()));
                    } else if (votedTemp > profile.getMinTemp() + 2.0) {
                        actuators.setAutoHeater(false, String.format("Canopy warmed above target (%.1f°C)", votedTemp));
                    }

                    // C. Exhaust Ventilation Fan Control
                    if (votedTemp > profile.getMaxTemp() || humidity > profile.getMaxHumidity()) {
                        actuators.setAutoFan(true, String.format("Exhaust needed (Temp=%.1f°C, Hum=%.1f%%)",
                            votedTemp, humidity));
                    } else {
                        actuators.setAutoFan(false, "Climate parameters within target envelope");
                    }
                }

                // D. Misting / Fogger Control for humidity regulation
                if (humidity < profile.getMinHumidity() && votedTemp <= profile.getMaxTemp()) {
                    actuators.setAutoFogger(true, String.format("Low humidity misting active (%.1f%% < %.1f%%)",
                        humidity, profile.getMinHumidity()));
                } else if (humidity > profile.getMinHumidity() + 5.0) {
                    actuators.setAutoFogger(false, "Relative humidity target satisfied");
                }

                // E. Drip Irrigation Pump Control with hysteresis
                if (soil < profile.getMinSoilMoisture()) {
                    actuators.setAutoPump(true, String.format("Root zone dry (%.1f%% < %.1f%%)",
                        soil, profile.getMinSoilMoisture()));
                } else if (soil > profile.getMaxSoilMoisture()) {
                    actuators.setAutoPump(false, String.format("Root zone moisture satisfied (%.1f%%)", soil));
                }
            }

            // 7. Advance physical plant dynamics
            fieldBus.stepPhysics(
                actuators.isHeaterOn(),
                actuators.isFanOn(),
                actuators.isPumpOn(),
                actuators.isFoggerOn()
            );

        } catch (Exception e) {
            diagnosticNotes = "Forward Error Recovery: " + e.getMessage();
            System.err.printf("%s [SCADA CORE] Forward Error Recovery caught exception: %s%n",
                WatchdogMonitor.getTimestamp(), e.getMessage());
            watchdog.enterSafeState("Controller exception: " + e.getMessage());
            actuators.applySafeState("Forward Error Recovery: " + e.getClass().getSimpleName());
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            timingMonitor.recordExecution("SCADA Control Cycle", durationNanos);

            // Record to industrial historian
            if (telemetryLogger != null) {
                telemetryLogger.recordPoint(
                    activeProfile,
                    latestVoteResult.temperature(),
                    latestVoteResult.status().name(),
                    latestHumidity,
                    latestSoilMoisture,
                    latestVpd,
                    actuators.isHeaterOn(),
                    actuators.isFanOn(),
                    actuators.isPumpOn(),
                    actuators.isFoggerOn(),
                    actuators.isAlarmOn(),
                    actuators.isSafeStateActive() ? "FAIL-SAFE" : "ONLINE",
                    actuators.isSafeStateActive(),
                    diagnosticNotes
                );
            }
        }
    }

    public void setCropProfile(CropProfile profile) {
        if (profile != null) {
            this.activeProfile = profile;
            System.out.printf("%s [SCADA CORE] Active Agronomic Profile Switched to: %s%n",
                WatchdogMonitor.getTimestamp(), profile.getDisplayName());
        }
    }

    public CropProfile getActiveProfile() {
        return activeProfile;
    }

    public void triggerSimulatedException() {
        this.simulateException = true;
    }

    public TMRVoter.VoteResult getLatestVoteResult() {
        return latestVoteResult;
    }

    public double getLatestHumidity() {
        return latestHumidity;
    }

    public double getLatestSoilMoisture() {
        return latestSoilMoisture;
    }

    public double getLatestVpd() {
        return latestVpd;
    }

    public AgronomyIntelligence.AgronomySnapshot getAgronomySnapshot() {
        return latestAgronomySnapshot;
    }

    public AgronomyIntelligence getAgronomyEngine() {
        return agronomyEngine;
    }

    public SensorReading getLastReading(String channelId) {
        return lastValidReadings.get(channelId);
    }

    public long getTmrMaskedTotal() {
        return tmrMaskedCount.get();
    }

    public BoundedBuffer<SensorReading> getBuffer() {
        return buffer;
    }

    public WatchdogMonitor getWatchdog() {
        return watchdog;
    }

    public ActuatorManager getActuators() {
        return actuators;
    }

    public SimulatedFieldBus getFieldBus() {
        return fieldBus;
    }

    public TimingMonitor getTimingMonitor() {
        return timingMonitor;
    }

    public TelemetryLogger getTelemetryLogger() {
        return telemetryLogger;
    }
}
