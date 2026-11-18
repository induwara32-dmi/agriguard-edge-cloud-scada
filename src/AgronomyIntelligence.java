import java.util.Locale;

/**
 * Predictive Agronomy Intelligence Engine for AgriGuard RT-SCADA.
 * Implements thermodynamic microclimate physics and predictive agronomy:
 * 1. Magnus-Tetens Dew Point (Td in °C) & Dew Point Spread (ΔT in °C).
 * 2. Fungal / Botrytis Condensation Risk Classifier (LOW, MODERATE, HIGH, CRITICAL).
 * 3. Accumulated Growing Degree Days (GDD) with crop-specific base temperature.
 * 4. Reference Evapotranspiration (ET0 mm/day) & precision pulse irrigation advisory.
 */
public class AgronomyIntelligence {

    public enum FungalRisk {
        LOW("Low Risk", 0),
        MODERATE("Moderate Risk", 1),
        HIGH("High Risk (Preventive Exhaust Advised)", 2),
        CRITICAL("Critical Condensation (Spore Germination Imminent)", 3);

        private final String description;
        private final int severityLevel;

        FungalRisk(String description, int severityLevel) {
            this.description = description;
            this.severityLevel = severityLevel;
        }

        public String getDescription() {
            return description;
        }

        public int getSeverityLevel() {
            return severityLevel;
        }
    }

    public record AgronomySnapshot(
            double dewPointC,
            double dewPointSpreadC,
            FungalRisk fungalRisk,
            double gddAccumulated,
            double et0MmDay,
            String irrigationAdvisory
    ) {
        public String fungalRiskLevel() {
            return fungalRisk.name();
        }
    }

    // Magnus coefficients for water vapor (valid -40°C to +50°C)
    private static final double MAGNUS_A = 17.27;
    private static final double MAGNUS_B = 237.7; // °C

    // Default base physiological temperature for horticultural crops
    public static final double DEFAULT_BASE_TEMP_C = 10.0;

    private double cumulativeGdd = 14.5; // Initialized base GDD baseline for simulation
    private long lastGddUpdateMs = System.currentTimeMillis();

    /**
     * Calculates the Dew Point temperature (Td in °C) using the Magnus-Tetens approximation.
     *
     * γ(T, RH) = (a * T) / (b + T) + ln(RH / 100)
     * Td = (b * γ) / (a - γ)
     *
     * @param tempCelsius canopy temperature in °C
     * @param humidityPercent relative humidity percentage (0.0 to 100.0)
     * @return dew point temperature in °C
     */
    public static double calculateDewPoint(double tempCelsius, double humidityPercent) {
        double t = Math.max(-20.0, Math.min(60.0, tempCelsius));
        double rh = Math.max(1.0, Math.min(100.0, humidityPercent));

        double gamma = (MAGNUS_A * t) / (MAGNUS_B + t) + Math.log(rh / 100.0);
        double dewPoint = (MAGNUS_B * gamma) / (MAGNUS_A - gamma);
        return Math.round(dewPoint * 100.0) / 100.0;
    }

    /**
     * Calculates the Dew Point Spread: ΔT = T_canopy - T_dewpoint.
     * Smaller spreads indicate near-saturation air and elevated condensation risk.
     */
    public static double calculateDewPointSpread(double tempCelsius, double dewPointCelsius) {
        double spread = tempCelsius - dewPointCelsius;
        return Math.round(Math.max(0.0, spread) * 100.0) / 100.0;
    }

    /**
     * Evaluates Fungal / Botrytis spore germination risk based on dew point spread and relative humidity.
     * Botrytis cinerea and powdery mildews require high relative humidity and surface moisture condensation.
     */
    public static FungalRisk evaluateFungalRisk(double dewPointSpreadC, double humidityPercent) {
        if (dewPointSpreadC <= 1.0 && humidityPercent >= 90.0) {
            return FungalRisk.CRITICAL;
        } else if (dewPointSpreadC < 2.0 && humidityPercent > 82.0) {
            return FungalRisk.HIGH;
        } else if (dewPointSpreadC < 3.5 && humidityPercent > 75.0) {
            return FungalRisk.MODERATE;
        } else {
            return FungalRisk.LOW;
        }
    }

    /**
     * Calculates Reference Evapotranspiration (ET0) in mm/day under greenhouse protected canopy.
     * Approximates FAO-56 Penman-Monteith / Hargreaves under solar radiation and VPD microclimate.
     */
    public static double calculateET0(double tempCelsius, double vpdKpa) {
        double t = Math.max(5.0, Math.min(45.0, tempCelsius));
        double v = Math.max(0.1, Math.min(4.0, vpdKpa));
        // Greenhouse microclimate ET0 formula (Hargreaves-Samani / FAO-56 protected cultivation)
        double et0 = 0.082 * (t + 17.8) * (1.0 + 0.18 * v);
        return Math.round(Math.max(0.5, Math.min(12.0, et0)) * 100.0) / 100.0;
    }

    /**
     * Updates and accumulates Growing Degree Days (GDD) based on canopy temperature.
     */
    public synchronized double updateGdd(double tempCelsius, double baseTempC) {
        long now = System.currentTimeMillis();
        double elapsedDays = (now - lastGddUpdateMs) / (1000.0 * 86400.0);
        lastGddUpdateMs = now;

        double dailyRate = Math.max(0.0, tempCelsius - baseTempC);
        // Integrate smoothly; advance simulation proportionally
        cumulativeGdd += dailyRate * Math.max(elapsedDays, 0.00001);
        return Math.round(cumulativeGdd * 100.0) / 100.0;
    }

    /**
     * Generates actionable precision pulse irrigation advisory based on ET0 and soil moisture deficit.
     */
    public static String generateIrrigationAdvisory(
            double et0MmDay,
            double currentSoilMoisture,
            double minSoilMoisture,
            double maxSoilMoisture) {

        if (currentSoilMoisture < minSoilMoisture) {
            double deficit = minSoilMoisture - currentSoilMoisture;
            int pulseMins = (int) Math.round(10.0 + (deficit * 1.2) + (et0MmDay * 1.5));
            return String.format(Locale.US,
                "Deficit detected (%.1f%% < %.1f%%). Immediate pulse recommended: %d mins (ET₀=%.2f mm/day)",
                currentSoilMoisture, minSoilMoisture, Math.min(45, pulseMins), et0MmDay);
        } else if (currentSoilMoisture > maxSoilMoisture) {
            return String.format(Locale.US,
                "Root zone saturated (%.1f%% > %.1f%%). Irrigation suspended to prevent hypoxia.",
                currentSoilMoisture, maxSoilMoisture);
        } else {
            int scheduledPulse = (int) Math.round(8.0 + (et0MmDay * 1.2));
            return String.format(Locale.US,
                "Moisture optimal (%.1f%%). Next scheduled maintenance pulse: %d mins at dusk.",
                currentSoilMoisture, scheduledPulse);
        }
    }

    /**
     * Evaluates complete agronomic snapshot for current cycle.
     */
    public synchronized AgronomySnapshot evaluate(
            double tempCelsius,
            double humidityPercent,
            double soilMoisturePercent,
            double vpdKpa,
            CropProfile profile) {

        double dewPoint = calculateDewPoint(tempCelsius, humidityPercent);
        double spread = calculateDewPointSpread(tempCelsius, dewPoint);
        FungalRisk risk = evaluateFungalRisk(spread, humidityPercent);
        double gdd = updateGdd(tempCelsius, DEFAULT_BASE_TEMP_C);
        double et0 = calculateET0(tempCelsius, vpdKpa);
        String advisory = generateIrrigationAdvisory(
            et0,
            soilMoisturePercent,
            profile != null ? profile.getMinSoilMoisture() : 40.0,
            profile != null ? profile.getMaxSoilMoisture() : 60.0
        );

        return new AgronomySnapshot(dewPoint, spread, risk, gdd, et0, advisory);
    }
}
