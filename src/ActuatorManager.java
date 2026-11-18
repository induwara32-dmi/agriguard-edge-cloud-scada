import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Industrial Edge Actuator Subsystem Manager.
 * Governs physical plant outputs: Thermal Curtain/Heater, Exhaust Fans, Drip Irrigation,
 * Misting Fogger, and Emergency Siren/Beacon.
 * Supports individual AUTO (Closed-Loop) vs MANUAL OVERRIDE operation with deterministic
 * Critical Safety Interlock overriding all manual inputs during fail-safe events.
 */
public class ActuatorManager {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public enum ControlMode {
        AUTO,
        MANUAL_OVERRIDE
    }

    // Physical Actuator States
    private volatile boolean heaterOn = false;
    private volatile boolean fanOn = false;
    private volatile boolean pumpOn = false;
    private volatile boolean foggerOn = false;
    private volatile boolean alarmOn = false;

    // Control Modes per Actuator
    private volatile ControlMode heaterMode = ControlMode.AUTO;
    private volatile ControlMode fanMode = ControlMode.AUTO;
    private volatile ControlMode pumpMode = ControlMode.AUTO;
    private volatile ControlMode foggerMode = ControlMode.AUTO;
    private volatile ControlMode alarmMode = ControlMode.AUTO;

    // Safety Interlock (Safe State) flag
    private volatile boolean safeStateActive = false;

    // Closed-loop target desires
    private volatile boolean autoHeaterTarget = false;
    private volatile boolean autoFanTarget = false;
    private volatile boolean autoPumpTarget = false;
    private volatile boolean autoFoggerTarget = false;
    private volatile boolean autoAlarmTarget = false;

    // Manual operator desires
    private volatile boolean manualHeaterTarget = false;
    private volatile boolean manualFanTarget = false;
    private volatile boolean manualPumpTarget = false;
    private volatile boolean manualFoggerTarget = false;
    private volatile boolean manualAlarmTarget = false;

    // --- AUTO Closed-Loop Setters ---

    public synchronized void setAutoHeater(boolean target, String reason) {
        this.autoHeaterTarget = target;
        if (!safeStateActive && heaterMode == ControlMode.AUTO) {
            applyState("Heater", this.heaterOn, target, reason, () -> this.heaterOn = target);
        }
    }

    public synchronized void setAutoFan(boolean target, String reason) {
        this.autoFanTarget = target;
        if (!safeStateActive && fanMode == ControlMode.AUTO) {
            applyState("Exhaust Fan", this.fanOn, target, reason, () -> this.fanOn = target);
        }
    }

    public synchronized void setAutoPump(boolean target, String reason) {
        this.autoPumpTarget = target;
        if (!safeStateActive && pumpMode == ControlMode.AUTO) {
            applyState("Drip Pump", this.pumpOn, target, reason, () -> this.pumpOn = target);
        }
    }

    public synchronized void setAutoFogger(boolean target, String reason) {
        this.autoFoggerTarget = target;
        if (!safeStateActive && foggerMode == ControlMode.AUTO) {
            applyState("Misting Fogger", this.foggerOn, target, reason, () -> this.foggerOn = target);
        }
    }

    public synchronized void setAutoAlarm(boolean target, String reason) {
        this.autoAlarmTarget = target;
        if (!safeStateActive && alarmMode == ControlMode.AUTO) {
            applyState("Emergency Alarm", this.alarmOn, target, reason, () -> this.alarmOn = target);
        }
    }

    // --- Manual Override Setters ---

    public synchronized void setManualHeater(boolean state) {
        this.manualHeaterTarget = state;
        if (!safeStateActive && heaterMode == ControlMode.MANUAL_OVERRIDE) {
            applyState("Heater", this.heaterOn, state, "Manual Operator Override", () -> this.heaterOn = state);
        }
    }

    public synchronized void setManualFan(boolean state) {
        this.manualFanTarget = state;
        if (!safeStateActive && fanMode == ControlMode.MANUAL_OVERRIDE) {
            applyState("Exhaust Fan", this.fanOn, state, "Manual Operator Override", () -> this.fanOn = state);
        }
    }

    public synchronized void setManualPump(boolean state) {
        this.manualPumpTarget = state;
        if (!safeStateActive && pumpMode == ControlMode.MANUAL_OVERRIDE) {
            applyState("Drip Pump", this.pumpOn, state, "Manual Operator Override", () -> this.pumpOn = state);
        }
    }

    public synchronized void setManualFogger(boolean state) {
        this.manualFoggerTarget = state;
        if (!safeStateActive && foggerMode == ControlMode.MANUAL_OVERRIDE) {
            applyState("Misting Fogger", this.foggerOn, state, "Manual Operator Override", () -> this.foggerOn = state);
        }
    }

    public synchronized void setManualAlarm(boolean state) {
        this.manualAlarmTarget = state;
        if (!safeStateActive && alarmMode == ControlMode.MANUAL_OVERRIDE) {
            applyState("Emergency Alarm", this.alarmOn, state, "Manual Operator Override", () -> this.alarmOn = state);
        }
    }

    public synchronized void setManualOverride(String actuatorName, boolean state) {
        if ("Heater".equalsIgnoreCase(actuatorName)) {
            setHeaterMode(ControlMode.MANUAL_OVERRIDE);
            setManualHeater(state);
        } else if ("Fan".equalsIgnoreCase(actuatorName) || "Exhaust Fan".equalsIgnoreCase(actuatorName)) {
            setFanMode(ControlMode.MANUAL_OVERRIDE);
            setManualFan(state);
        } else if ("Pump".equalsIgnoreCase(actuatorName)) {
            setPumpMode(ControlMode.MANUAL_OVERRIDE);
            setManualPump(state);
        } else if ("Fogger".equalsIgnoreCase(actuatorName)) {
            setFoggerMode(ControlMode.MANUAL_OVERRIDE);
            setManualFogger(state);
        } else if ("Alarm".equalsIgnoreCase(actuatorName)) {
            setAlarmMode(ControlMode.MANUAL_OVERRIDE);
            setManualAlarm(state);
        }
    }

    public synchronized boolean getActuatorState(String actuatorName) {
        if (actuatorName == null) return false;
        return switch (actuatorName.toUpperCase().trim()) {
            case "HEATER" -> heaterOn;
            case "FAN", "EXHAUST FAN" -> fanOn;
            case "PUMP", "DRIP PUMP" -> pumpOn;
            case "FOGGER", "MISTING FOGGER" -> foggerOn;
            case "ALARM", "EMERGENCY ALARM" -> alarmOn;
            default -> false;
        };
    }

    public synchronized ControlMode getActuatorControlMode(String actuatorName) {
        if (actuatorName == null) return ControlMode.AUTO;
        return switch (actuatorName.toUpperCase().trim()) {
            case "HEATER" -> heaterMode;
            case "FAN", "EXHAUST FAN" -> fanMode;
            case "PUMP", "DRIP PUMP" -> pumpMode;
            case "FOGGER", "MISTING FOGGER" -> foggerMode;
            case "ALARM", "EMERGENCY ALARM" -> alarmMode;
            default -> ControlMode.AUTO;
        };
    }

    public synchronized boolean setRemoteActuator(String actuatorName, String modeStr, Boolean state) {
        if (actuatorName == null) return false;
        String nameNorm = actuatorName.toUpperCase().trim();
        ControlMode newMode = null;
        if (modeStr != null && !modeStr.isBlank()) {
            String m = modeStr.toUpperCase().trim();
            if (m.equals("MANUAL") || m.equals("MANUAL_OVERRIDE")) {
                newMode = ControlMode.MANUAL_OVERRIDE;
            } else if (m.equals("AUTO")) {
                newMode = ControlMode.AUTO;
            }
        }

        switch (nameNorm) {
            case "HEATER" -> {
                if (newMode != null) setHeaterMode(newMode);
                if (state != null && (newMode == ControlMode.MANUAL_OVERRIDE || heaterMode == ControlMode.MANUAL_OVERRIDE)) {
                    setManualHeater(state);
                }
            }
            case "FAN", "EXHAUST FAN" -> {
                if (newMode != null) setFanMode(newMode);
                if (state != null && (newMode == ControlMode.MANUAL_OVERRIDE || fanMode == ControlMode.MANUAL_OVERRIDE)) {
                    setManualFan(state);
                }
            }
            case "PUMP", "DRIP PUMP" -> {
                if (newMode != null) setPumpMode(newMode);
                if (state != null && (newMode == ControlMode.MANUAL_OVERRIDE || pumpMode == ControlMode.MANUAL_OVERRIDE)) {
                    setManualPump(state);
                }
            }
            case "FOGGER", "MISTING FOGGER" -> {
                if (newMode != null) setFoggerMode(newMode);
                if (state != null && (newMode == ControlMode.MANUAL_OVERRIDE || foggerMode == ControlMode.MANUAL_OVERRIDE)) {
                    setManualFogger(state);
                }
            }
            case "ALARM", "EMERGENCY ALARM" -> {
                if (newMode != null) setAlarmMode(newMode);
                if (state != null && (newMode == ControlMode.MANUAL_OVERRIDE || alarmMode == ControlMode.MANUAL_OVERRIDE)) {
                    setManualAlarm(state);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // --- Mode Selectors ---

    public synchronized void setHeaterMode(ControlMode mode) {
        this.heaterMode = mode;
        if (!safeStateActive) {
            boolean target = (mode == ControlMode.AUTO) ? autoHeaterTarget : manualHeaterTarget;
            applyState("Heater", this.heaterOn, target, "Switched to " + mode, () -> this.heaterOn = target);
        }
    }

    public synchronized void setFanMode(ControlMode mode) {
        this.fanMode = mode;
        if (!safeStateActive) {
            boolean target = (mode == ControlMode.AUTO) ? autoFanTarget : manualFanTarget;
            applyState("Exhaust Fan", this.fanOn, target, "Switched to " + mode, () -> this.fanOn = target);
        }
    }

    public synchronized void setPumpMode(ControlMode mode) {
        this.pumpMode = mode;
        if (!safeStateActive) {
            boolean target = (mode == ControlMode.AUTO) ? autoPumpTarget : manualPumpTarget;
            applyState("Drip Pump", this.pumpOn, target, "Switched to " + mode, () -> this.pumpOn = target);
        }
    }

    public synchronized void setFoggerMode(ControlMode mode) {
        this.foggerMode = mode;
        if (!safeStateActive) {
            boolean target = (mode == ControlMode.AUTO) ? autoFoggerTarget : manualFoggerTarget;
            applyState("Misting Fogger", this.foggerOn, target, "Switched to " + mode, () -> this.foggerOn = target);
        }
    }

    public synchronized void setAlarmMode(ControlMode mode) {
        this.alarmMode = mode;
        if (!safeStateActive) {
            boolean target = (mode == ControlMode.AUTO) ? autoAlarmTarget : manualAlarmTarget;
            applyState("Emergency Alarm", this.alarmOn, target, "Switched to " + mode, () -> this.alarmOn = target);
        }
    }

    // --- Safety Interlocks ---

    /**
     * Trips all actuators into the deterministic Fail-Safe State:
     * - Heater: OFF (prevent thermal runaway)
     * - Fan: ON (maximum extraction ventilation)
     * - Pump: OFF (flood prevention)
     * - Fogger: OFF (prevent short circuit/humidity saturation)
     * - Alarm: ON (audio-visual emergency indication)
     * Overrides manual mode unconditionally.
     */
    public synchronized void applySafeState(String reason) {
        this.safeStateActive = true;
        applyState("Heater", this.heaterOn, false, reason + " [FAIL-SAFE INTERLOCK]", () -> this.heaterOn = false);
        applyState("Exhaust Fan", this.fanOn, true, reason + " [FAIL-SAFE INTERLOCK]", () -> this.fanOn = true);
        applyState("Drip Pump", this.pumpOn, false, reason + " [FAIL-SAFE INTERLOCK]", () -> this.pumpOn = false);
        applyState("Misting Fogger", this.foggerOn, false, reason + " [FAIL-SAFE INTERLOCK]", () -> this.foggerOn = false);
        applyState("Emergency Alarm", this.alarmOn, true, reason + " [FAIL-SAFE INTERLOCK]", () -> this.alarmOn = true);
    }

    public synchronized void clearSafeState(String reason) {
        if (this.safeStateActive) {
            this.safeStateActive = false;
            System.out.printf("[%s] [INTERLOCK] Fail-Safe State Cleared -> Reason: %s%n",
                LocalTime.now().format(TIME_FMT), reason);

            // Re-apply desired mode states
            boolean targetHeater = (heaterMode == ControlMode.AUTO) ? autoHeaterTarget : manualHeaterTarget;
            boolean targetFan = (fanMode == ControlMode.AUTO) ? autoFanTarget : manualFanTarget;
            boolean targetPump = (pumpMode == ControlMode.AUTO) ? autoPumpTarget : manualPumpTarget;
            boolean targetFogger = (foggerMode == ControlMode.AUTO) ? autoFoggerTarget : manualFoggerTarget;
            boolean targetAlarm = (alarmMode == ControlMode.AUTO) ? autoAlarmTarget : manualAlarmTarget;

            applyState("Heater", this.heaterOn, targetHeater, "Restored normal control", () -> this.heaterOn = targetHeater);
            applyState("Exhaust Fan", this.fanOn, targetFan, "Restored normal control", () -> this.fanOn = targetFan);
            applyState("Drip Pump", this.pumpOn, targetPump, "Restored normal control", () -> this.pumpOn = targetPump);
            applyState("Misting Fogger", this.foggerOn, targetFogger, "Restored normal control", () -> this.foggerOn = targetFogger);
            applyState("Emergency Alarm", this.alarmOn, targetAlarm, "Restored normal control", () -> this.alarmOn = targetAlarm);
        }
    }

    private void applyState(String name, boolean current, boolean target, String reason, Runnable setter) {
        if (current != target) {
            setter.run();
            System.out.printf("[%s] [ACTUATOR] %s: %s -> %s | Reason: %s%n",
                LocalTime.now().format(TIME_FMT),
                name,
                current ? "ON" : "OFF",
                target ? "ON" : "OFF",
                reason);
        }
    }

    // --- State Accessors ---

    public boolean isHeaterOn() { return heaterOn; }
    public boolean isFanOn() { return fanOn; }
    public boolean isPumpOn() { return pumpOn; }
    public boolean isFoggerOn() { return foggerOn; }
    public boolean isAlarmOn() { return alarmOn; }

    public ControlMode getHeaterMode() { return heaterMode; }
    public ControlMode getFanMode() { return fanMode; }
    public ControlMode getPumpMode() { return pumpMode; }
    public ControlMode getFoggerMode() { return foggerMode; }
    public ControlMode getAlarmMode() { return alarmMode; }

    public boolean isSafeStateActive() { return safeStateActive; }

    public String getStatusSummary() {
        return String.format("[Heater: %s(%s), Fan: %s(%s), Pump: %s(%s), Fogger: %s(%s), Alarm: %s(%s)]",
            heaterOn ? "ON" : "OFF", heaterMode.name().charAt(0),
            fanOn ? "ON" : "OFF", fanMode.name().charAt(0),
            pumpOn ? "ON" : "OFF", pumpMode.name().charAt(0),
            foggerOn ? "ON" : "OFF", foggerMode.name().charAt(0),
            alarmOn ? "ON" : "OFF", alarmMode.name().charAt(0)
        );
    }
}
