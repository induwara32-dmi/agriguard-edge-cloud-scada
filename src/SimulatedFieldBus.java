import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Industrial Fieldbus Simulator & Physical Climate Dynamic Model.
 * Implements SensorDriver to provide field telemetry across RS-485 / Modbus / SDI-12 virtual channels.
 * Encapsulates multi-variable microclimate physics including natural ambient drift,
 * actuator thermodynamic forces, evaporative cooling from foggers, and thermal disturbances.
 */
public class SimulatedFieldBus implements SensorDriver {

    public enum ChannelState {
        NORMAL,
        BIAS_OFFSET,
        FROZEN,
        DISCONNECTED
    }

    private static class ChannelConfig {
        volatile ChannelState state = ChannelState.NORMAL;
        volatile double biasOffset = 0.0;
        volatile double frozenValue = Double.NaN;
    }

    private double temperature = 24.5; // °C
    private double humidity = 68.0;    // % RH
    private double soilMoisture = 48.0; // % VWC

    private static final double MIN_TEMP = 2.0;
    private static final double MAX_TEMP = 60.0;
    private static final double MIN_HUMIDITY = 15.0;
    private static final double MAX_HUMIDITY = 100.0;
    private static final double MIN_SOIL = 5.0;
    private static final double MAX_SOIL = 100.0;

    private final Map<String, ChannelConfig> channels = new ConcurrentHashMap<>();

    public SimulatedFieldBus() {
        channels.put("T1", new ChannelConfig());
        channels.put("T2", new ChannelConfig());
        channels.put("T3", new ChannelConfig());
        channels.put("H1", new ChannelConfig());
        channels.put("S1", new ChannelConfig());
    }

    @Override
    public double readChannel(String channelId) {
        ChannelConfig cfg = channels.get(channelId);
        if (cfg == null || cfg.state == ChannelState.DISCONNECTED) {
            return Double.NaN;
        }

        double raw = switch (channelId) {
            case "T1", "T2", "T3" -> getTemperature();
            case "H1" -> getHumidity();
            case "S1" -> getSoilMoisture();
            default -> Double.NaN;
        };

        if (Double.isNaN(raw)) {
            return Double.NaN;
        }

        // Apply realistic field transducer electronic noise (±0.15 units)
        double noise = (ThreadLocalRandom.current().nextDouble() * 0.30) - 0.15;

        return switch (cfg.state) {
            case NORMAL -> {
                double val = raw + noise;
                cfg.frozenValue = val;
                yield val;
            }
            case BIAS_OFFSET -> {
                double val = raw + noise + cfg.biasOffset;
                cfg.frozenValue = val;
                yield val;
            }
            case FROZEN -> {
                if (Double.isNaN(cfg.frozenValue)) {
                    cfg.frozenValue = raw + noise;
                }
                yield cfg.frozenValue;
            }
            case DISCONNECTED -> Double.NaN;
        };
    }

    @Override
    public boolean isChannelResponsive(String channelId) {
        ChannelConfig cfg = channels.get(channelId);
        return cfg != null && cfg.state != ChannelState.DISCONNECTED;
    }

    /**
     * Executes discrete physics time-step incorporating natural ambient drift and active actuators.
     *
     * @param heaterOn thermal curtain / heating element active
     * @param fanOn    exhaust ventilation fans active
     * @param pumpOn   drip irrigation pump active
     * @param foggerOn high-pressure humidity misting fogger active
     */
    public synchronized void stepPhysics(boolean heaterOn, boolean fanOn, boolean pumpOn, boolean foggerOn) {
        // Natural environmental drift
        temperature += 0.12;
        humidity += 0.25;
        soilMoisture -= 0.35;

        // Actuator impacts
        if (heaterOn) {
            temperature += 0.85;
        }
        if (fanOn) {
            temperature -= 0.95;
            humidity -= 1.60;
        }
        if (pumpOn) {
            soilMoisture += 2.80;
            humidity += 0.40;
        }
        if (foggerOn) {
            humidity += 2.50;
            temperature -= 0.20; // Evaporative cooling effect
        }

        // Clamp to physical bounds
        temperature = Math.max(MIN_TEMP, Math.min(MAX_TEMP, temperature));
        humidity = Math.max(MIN_HUMIDITY, Math.min(MAX_HUMIDITY, humidity));
        soilMoisture = Math.max(MIN_SOIL, Math.min(MAX_SOIL, soilMoisture));
    }

    /**
     * Injects an instantaneous thermal surge into the plantation climate (e.g., sudden solar radiation burst).
     *
     * @param deltaC temperature increase in degrees Celsius
     */
    public synchronized void injectThermalSurge(double deltaC) {
        this.temperature = Math.max(MIN_TEMP, Math.min(MAX_TEMP, this.temperature + deltaC));
    }

    /**
     * Ingests a direct calibrated measurement from an external Modbus/TCP or fieldbus transducer.
     */
    public synchronized void setDirectChannelValue(String channelId, double value) {
        ChannelConfig cfg = channels.get(channelId);
        if (cfg != null) {
            cfg.state = ChannelState.NORMAL;
            cfg.biasOffset = 0.0;
        }
        switch (channelId) {
            case "T1", "T2", "T3" -> this.temperature = Math.max(MIN_TEMP, Math.min(MAX_TEMP, value));
            case "H1" -> this.humidity = Math.max(MIN_HUMIDITY, Math.min(MAX_HUMIDITY, value));
            case "S1" -> this.soilMoisture = Math.max(MIN_SOIL, Math.min(MAX_SOIL, value));
        }
    }

    public synchronized double getTemperature() {
        return temperature;
    }

    public synchronized double getHumidity() {
        return humidity;
    }

    public synchronized double getSoilMoisture() {
        return soilMoisture;
    }

    public void setChannelState(String channelId, ChannelState state, double biasOffset) {
        ChannelConfig cfg = channels.get(channelId);
        if (cfg != null) {
            cfg.state = state;
            cfg.biasOffset = biasOffset;
            if (state == ChannelState.FROZEN) {
                cfg.frozenValue = switch (channelId) {
                    case "T1", "T2", "T3" -> getTemperature();
                    case "H1" -> getHumidity();
                    case "S1" -> getSoilMoisture();
                    default -> Double.NaN;
                };
            }
        }
    }

    public void resetAllChannels() {
        for (ChannelConfig cfg : channels.values()) {
            cfg.state = ChannelState.NORMAL;
            cfg.biasOffset = 0.0;
            cfg.frozenValue = Double.NaN;
        }
    }

    public ChannelState getChannelState(String channelId) {
        ChannelConfig cfg = channels.get(channelId);
        return (cfg != null) ? cfg.state : ChannelState.NORMAL;
    }
}
