/**
 * Production Agronomic Crop Profiles for AgriGuard RT-SCADA.
 * Defines commercial crop climate boundaries, irrigation setpoints,
 * critical thermal shutdown thresholds, and Vapor Pressure Deficit (VPD) targets.
 */
public enum CropProfile {

    BELL_PEPPER(
        "Capsicum (Bell Pepper)",
        21.0, 27.0, // Temperature range (°C)
        65.0, 80.0, // Relative Humidity range (%)
        40.0, 55.0, // Volumetric Soil Moisture range (%)
        34.0,       // Critical Thermal High (°C)
        0.80, 1.20  // Target VPD range (kPa)
    ),

    STRAWBERRY(
        "Commercial Strawberry",
        18.0, 24.0,
        60.0, 75.0,
        45.0, 60.0,
        30.0,
        0.70, 1.10
    ),

    TEA_NURSERY(
        "Camellia Sinensis (Tea Nursery)",
        20.0, 26.0,
        75.0, 88.0,
        50.0, 65.0,
        32.0,
        0.40, 0.80
    ),

    ORCHID_HOUSE(
        "Tropical Orchid House",
        22.0, 29.0,
        70.0, 85.0,
        35.0, 50.0,
        35.0,
        0.60, 1.00
    );

    private final String displayName;
    private final double minTemp;
    private final double maxTemp;
    private final double minHumidity;
    private final double maxHumidity;
    private final double minSoilMoisture;
    private final double maxSoilMoisture;
    private final double criticalHighTemp;
    private final double targetVpdMin;
    private final double targetVpdMax;

    CropProfile(
            String displayName,
            double minTemp,
            double maxTemp,
            double minHumidity,
            double maxHumidity,
            double minSoilMoisture,
            double maxSoilMoisture,
            double criticalHighTemp,
            double targetVpdMin,
            double targetVpdMax) {
        this.displayName = displayName;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.minHumidity = minHumidity;
        this.maxHumidity = maxHumidity;
        this.minSoilMoisture = minSoilMoisture;
        this.maxSoilMoisture = maxSoilMoisture;
        this.criticalHighTemp = criticalHighTemp;
        this.targetVpdMin = targetVpdMin;
        this.targetVpdMax = targetVpdMax;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMinTemp() {
        return minTemp;
    }

    public double getMaxTemp() {
        return maxTemp;
    }

    public double getMinHumidity() {
        return minHumidity;
    }

    public double getMaxHumidity() {
        return maxHumidity;
    }

    public double getMinSoilMoisture() {
        return minSoilMoisture;
    }

    public double getMaxSoilMoisture() {
        return maxSoilMoisture;
    }

    public double getCriticalHighTemp() {
        return criticalHighTemp;
    }

    public double getTargetVpdMin() {
        return targetVpdMin;
    }

    public double getTargetVpdMax() {
        return targetVpdMax;
    }

    /**
     * Calculates Vapor Pressure Deficit (VPD) in kilopascals (kPa).
     *
     * Saturation Vapor Pressure (VPsat):
     *   VPsat = 0.61078 * exp((17.27 * T) / (T + 237.3))  [kPa]
     * Actual Vapor Pressure (VPair):
     *   VPair = VPsat * (RH / 100.0)  [kPa]
     * VPD = VPsat - VPair
     *
     * @param tempCelsius      canopy temperature in °C
     * @param humidityPercent relative humidity percentage (0 - 100%)
     * @return calculated VPD in kPa (clamped >= 0.0)
     */
    public static double calculateVPD(double tempCelsius, double humidityPercent) {
        double t = Math.max(-10.0, Math.min(65.0, tempCelsius));
        double rh = Math.max(0.0, Math.min(100.0, humidityPercent));

        double vpSat = 0.61078 * Math.exp((17.27 * t) / (t + 237.3));
        double vpAir = vpSat * (rh / 100.0);
        return Math.max(0.0, vpSat - vpAir);
    }

    /**
     * Diagnoses whether the current VPD is in optimal transpiration range for this crop.
     *
     * @param vpd current calculated VPD in kPa
     * @return diagnostic classification (LOW_TRANSPIRATION, OPTIMAL, EXCESSIVE_STRESS)
     */
    public String evaluateVpdStatus(double vpd) {
        if (vpd < targetVpdMin) {
            return "LOW (Under-transpiring)";
        } else if (vpd > targetVpdMax) {
            return "HIGH (Water Stress)";
        } else {
            return "OPTIMAL";
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
