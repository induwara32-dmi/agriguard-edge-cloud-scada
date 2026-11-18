import { UnitSystem } from "@/types/telemetry";

export function toFahrenheit(celsius: number): number {
  return Number((celsius * 1.8 + 32).toFixed(2));
}

export function toPsi(kpa: number): number {
  return Number((kpa * 0.145038).toFixed(3));
}

export function toSpreadFahrenheit(celsiusSpread: number): number {
  return Number((celsiusSpread * 1.8).toFixed(2));
}

export function formatTemp(celsius: number, unit: UnitSystem) {
  if (unit === "IMPERIAL") {
    const val = toFahrenheit(celsius);
    return { val, unit: "°F", text: `${val.toFixed(1)}°F` };
  }
  const val = Number(celsius.toFixed(2));
  return { val, unit: "°C", text: `${val.toFixed(1)}°C` };
}

export function formatVpd(kpa: number, unit: UnitSystem) {
  if (unit === "IMPERIAL") {
    const val = toPsi(kpa);
    return { val, unit: "psi", text: `${val.toFixed(3)} psi` };
  }
  const val = Number(kpa.toFixed(3));
  return { val, unit: "kPa", text: `${val.toFixed(3)} kPa` };
}

export function formatSpread(celsiusSpread: number, unit: UnitSystem) {
  if (unit === "IMPERIAL") {
    const val = toSpreadFahrenheit(celsiusSpread);
    return { val, unit: "°F", text: `${val.toFixed(1)}°F` };
  }
  const val = Number(celsiusSpread.toFixed(2));
  return { val, unit: "°C", text: `${val.toFixed(1)}°C` };
}
