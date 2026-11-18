export type CropProfileType =
  | "BELL_PEPPER"
  | "STRAWBERRY"
  | "TEA_NURSERY"
  | "ORCHID_HOUSE";

export type VoteStatus = "OK" | "MASKED" | "DEGRADED" | "FAILED";

export type SecurityRole = "OPERATOR" | "AGRONOMIST" | "VIEWER";
export type UnitSystem = "METRIC" | "IMPERIAL";
export type LanguageCode = "EN" | "SI" | "TA";
export type FungalRiskLevel = "LOW" | "MODERATE" | "HIGH" | "CRITICAL";

export interface ActuatorsState {
  heater: boolean;
  fan: boolean;
  pump: boolean;
  fogger: boolean;
  alarm: boolean;
  controlMode: string;
  safeStateInterlock: boolean;
}

export interface DiagnosticsState {
  tmrMaskedTotal: number;
  watchdogFailuresTotal: number;
  safeStateActive: boolean;
  bufferOccupancy: number;
  bufferCapacity: number;
}

export interface AgronomyData {
  dewPointC: number;
  dewPointSpreadC: number;
  fungalRiskLevel: FungalRiskLevel;
  fungalRiskSeverity?: number;
  fungalRiskDescription?: string;
  gddAccumulated: number;
  et0MmDay: number;
  irrigationAdvisory: string;
}

export interface TelemetrySnapshot {
  zoneId: string;
  zoneName: string;
  timestamp: string;
  isoDateTime: string;
  cropProfile: CropProfileType;
  cropDisplayName: string;
  votedTemperature: number;
  voteStatus: VoteStatus;
  voteDetail: string;
  humidityPercent: number;
  soilMoisturePercent: number;
  vpdKpa: number;
  dewPointC?: number;
  dewPointSpreadC?: number;
  fungalRiskLevel?: FungalRiskLevel;
  gddAccumulated?: number;
  et0MmDay?: number;
  irrigationAdvisory?: string;
  agronomy?: AgronomyData;
  actuators: ActuatorsState;
  diagnostics: DiagnosticsState;
}

export interface AuditStatus {
  zoneId: string;
  zoneName: string;
  auditLedgerFile: string;
  totalRecords: number;
  genesisHash: string;
  latestHash: string;
  chainVerified: boolean;
}

export interface TelemetryHistoryPoint {
  time: string;
  temp: number;
  humidity: number;
  soil: number;
  vpd: number;
  dewPoint?: number;
}

export interface BackfillRecord {
  block: number;
  timestamp: string;
  isoDateTime: string;
  cropProfile: CropProfileType;
  votedTemp: number;
  voteStatus: VoteStatus;
  humidity: number;
  soilMoisture: number;
  vpd: number;
  heater: boolean;
  fan: boolean;
  pump: boolean;
  fogger: boolean;
  alarm: boolean;
  controlMode: string;
  safeState: boolean;
  eventNotes: string;
  prevHash: string;
  rowHash: string;
}

export interface BackfillResponse {
  zoneId: string;
  totalRecords: number;
  sinceBlock: number;
  returnedCount: number;
  hasMore: boolean;
  nextBlock: number;
  records: BackfillRecord[];
}

export interface ZoneConfig {
  id: string;
  name: string;
  port: number;
  endpoint: string;
}

export const KNOWN_ZONES: ZoneConfig[] = [
  {
    id: "ZONE-01",
    name: "Highland Strawberry Canopy A",
    port: 8085,
    endpoint: "http://localhost:8085",
  },
  {
    id: "ZONE-02",
    name: "Highland Tea Nursery House B",
    port: 8086,
    endpoint: "http://localhost:8086",
  },
];

export interface CropProfileMeta {
  key: CropProfileType;
  name: string;
  targetTemp: string;
  targetHumidity: string;
  targetSoil: string;
  criticalTemp: number;
  optimalVpd: [number, number]; // [min, max]
}

export const CROP_METAS: Record<CropProfileType, CropProfileMeta> = {
  BELL_PEPPER: {
    key: "BELL_PEPPER",
    name: "Capsicum (Bell Pepper)",
    targetTemp: "21.0 - 27.0°C",
    targetHumidity: "65.0 - 80.0%",
    targetSoil: "40.0 - 55.0%",
    criticalTemp: 34.0,
    optimalVpd: [0.8, 1.2],
  },
  STRAWBERRY: {
    key: "STRAWBERRY",
    name: "Commercial Strawberry",
    targetTemp: "18.0 - 24.0°C",
    targetHumidity: "60.0 - 75.0%",
    targetSoil: "45.0 - 60.0%",
    criticalTemp: 30.0,
    optimalVpd: [0.7, 1.1],
  },
  TEA_NURSERY: {
    key: "TEA_NURSERY",
    name: "Tea Nursery (C. Sinensis)",
    targetTemp: "20.0 - 26.0°C",
    targetHumidity: "75.0 - 88.0%",
    targetSoil: "50.0 - 65.0%",
    criticalTemp: 32.0,
    optimalVpd: [0.4, 0.8],
  },
  ORCHID_HOUSE: {
    key: "ORCHID_HOUSE",
    name: "Tropical Orchid House",
    targetTemp: "22.0 - 29.0°C",
    targetHumidity: "70.0 - 85.0%",
    targetSoil: "35.0 - 50.0%",
    criticalTemp: 35.0,
    optimalVpd: [0.6, 1.0],
  },
};
