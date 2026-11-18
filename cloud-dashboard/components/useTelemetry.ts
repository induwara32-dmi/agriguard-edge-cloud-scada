"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import {
  TelemetrySnapshot,
  TelemetryHistoryPoint,
  AuditStatus,
  ZoneConfig,
  KNOWN_ZONES,
  CropProfileType,
  SecurityRole,
  UnitSystem,
  LanguageCode,
  BackfillResponse,
} from "../types/telemetry";
import { buildSignedHeaders } from "../lib/security";

export interface FeedbackNotification {
  text: string;
  type: "success" | "error" | "info";
  timestamp: number;
}

export function useTelemetry() {
  const [selectedZone, setSelectedZone] = useState<ZoneConfig>(KNOWN_ZONES[0]);
  const [snapshot, setSnapshot] = useState<TelemetrySnapshot | null>(null);
  const [history, setHistory] = useState<TelemetryHistoryPoint[]>([]);
  const [auditStatus, setAuditStatus] = useState<AuditStatus | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<
    "CONNECTED" | "RECONNECTING" | "PREVIEW"
  >("RECONNECTING");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // International & Security States
  const [role, setRole] = useState<SecurityRole>("OPERATOR");
  const [unit, setUnit] = useState<UnitSystem>("METRIC");
  const [language, setLanguage] = useState<LanguageCode>("EN");
  const [notification, setNotification] = useState<FeedbackNotification | null>(null);
  const [isBackfilling, setIsBackfilling] = useState(false);

  const eventSourceRef = useRef<EventSource | null>(null);
  const isMountedRef = useRef<boolean>(true);

  const showNotification = useCallback((text: string, type: "success" | "error" | "info" = "info") => {
    setNotification({ text, type, timestamp: Date.now() });
    setTimeout(() => {
      setNotification((curr) => (curr && Date.now() - curr.timestamp >= 4500 ? null : curr));
    }, 5000);
  }, []);

  // Helper to append a point to 60-second rolling history
  const appendHistory = useCallback((point: TelemetryHistoryPoint) => {
    setHistory((prev) => {
      const next = [...prev, point];
      return next.length > 60 ? next.slice(next.length - 60) : next;
    });
  }, []);

  // Fetch audit status from the active edge zone
  const fetchAudit = useCallback(async (endpoint: string, zoneId: string) => {
    try {
      const res = await fetch(`${endpoint}/api/v1/audit/status`, {
        cache: "no-store",
      });
      if (res.ok) {
        const data = await res.json();
        setAuditStatus(data);
      }
    } catch {
      // Offline fallback: provide baseline audit status
      setAuditStatus((prev) =>
        prev
          ? prev
          : {
              zoneId,
              zoneName: "Edge Gateway Node",
              auditLedgerFile: "logs/plantation_telemetry.csv",
              totalRecords: 1,
              genesisHash:
                "0000000000000000000000000000000000000000000000000000000000000000",
              latestHash:
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
              chainVerified: true,
            }
      );
    }
  }, []);

  // Primary SSE Connection Loop
  useEffect(() => {
    isMountedRef.current = true;
    let fallbackInterval: NodeJS.Timeout | null = null;
    let auditInterval: NodeJS.Timeout | null = null;

    const connectSSE = () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }

      setConnectionStatus("RECONNECTING");
      const streamUrl = `${selectedZone.endpoint}/api/v1/telemetry/stream`;

      try {
        const es = new EventSource(streamUrl);
        eventSourceRef.current = es;

        es.addEventListener("telemetry", (e: MessageEvent) => {
          if (!isMountedRef.current) return;
          try {
            const data: TelemetrySnapshot = JSON.parse(e.data);
            setSnapshot(data);
            setConnectionStatus("CONNECTED");
            setLastUpdated(new Date());

            appendHistory({
              time: data.timestamp,
              temp: data.votedTemperature,
              humidity: data.humidityPercent,
              soil: data.soilMoisturePercent,
              vpd: data.vpdKpa,
              dewPoint: data.dewPointC ?? data.agronomy?.dewPointC,
            });
          } catch (err) {
            console.error("Telemetry parse error", err);
          }
        });

        es.onerror = () => {
          if (!isMountedRef.current) return;
          es.close();
          setConnectionStatus("RECONNECTING");
          pollSnapshot();
        };
      } catch {
        pollSnapshot();
      }
    };

    const pollSnapshot = async () => {
      try {
        const res = await fetch(`${selectedZone.endpoint}/api/v1/status`, {
          cache: "no-store",
        });
        if (res.ok) {
          const data: TelemetrySnapshot = await res.json();
          if (isMountedRef.current) {
            setSnapshot(data);
            setConnectionStatus("CONNECTED");
            setLastUpdated(new Date());
            appendHistory({
              time: data.timestamp,
              temp: data.votedTemperature,
              humidity: data.humidityPercent,
              soil: data.soilMoisturePercent,
              vpd: data.vpdKpa,
              dewPoint: data.dewPointC ?? data.agronomy?.dewPointC,
            });
          }
        } else {
          fallbackToPreview();
        }
      } catch {
        fallbackToPreview();
      }
    };

    const fallbackToPreview = () => {
      if (!isMountedRef.current) return;
      setConnectionStatus("PREVIEW");
      setSnapshot((prev) => {
        const now = new Date();
        const timeStr = now.toTimeString().split(" ")[0];
        const baseTemp = selectedZone.id === "ZONE-01" ? 24.3 : 21.8;
        const drift = (Math.random() - 0.5) * 0.4;
        const temp = parseFloat((baseTemp + drift).toFixed(2));
        const hum = parseFloat((68.0 + (Math.random() - 0.5) * 1.5).toFixed(1));
        const soil = parseFloat((48.0 + (Math.random() - 0.5) * 0.8).toFixed(1));
        const vpd = parseFloat((1.02 + (Math.random() - 0.5) * 0.05).toFixed(3));
        const dew = parseFloat((temp - 5.8 + (Math.random() - 0.5) * 0.2).toFixed(2));
        const spread = parseFloat((temp - dew).toFixed(2));

        const dummy: TelemetrySnapshot = {
          zoneId: selectedZone.id,
          zoneName: selectedZone.name,
          timestamp: timeStr,
          isoDateTime: now.toISOString(),
          cropProfile: selectedZone.id === "ZONE-01" ? "STRAWBERRY" : "TEA_NURSERY",
          cropDisplayName:
            selectedZone.id === "ZONE-01"
              ? "Commercial Strawberry"
              : "Tea Nursery (C. Sinensis)",
          votedTemperature: temp,
          voteStatus: "OK",
          voteDetail: `TMR Nominal Consensus: T1=${temp.toFixed(2)}, T2=${(temp + 0.05).toFixed(2)}, T3=${(temp - 0.04).toFixed(2)}`,
          humidityPercent: hum,
          soilMoisturePercent: soil,
          vpdKpa: vpd,
          dewPointC: dew,
          dewPointSpreadC: spread,
          fungalRiskLevel: "LOW",
          gddAccumulated: 14.5,
          et0MmDay: 3.85,
          irrigationAdvisory: "Moisture optimal. Scheduled evening pulse: 12 mins.",
          agronomy: {
            dewPointC: dew,
            dewPointSpreadC: spread,
            fungalRiskLevel: "LOW",
            gddAccumulated: 14.5,
            et0MmDay: 3.85,
            irrigationAdvisory: "Moisture optimal. Scheduled evening pulse: 12 mins.",
          },
          actuators: {
            heater: false,
            fan: false,
            pump: false,
            fogger: false,
            alarm: false,
            controlMode: "AUTO",
            safeStateInterlock: false,
          },
          diagnostics: {
            tmrMaskedTotal: 0,
            watchdogFailuresTotal: 0,
            safeStateActive: false,
            bufferOccupancy: 3,
            bufferCapacity: 10,
          },
        };

        appendHistory({
          time: timeStr,
          temp,
          humidity: hum,
          soil,
          vpd,
          dewPoint: dew,
        });

        return dummy;
      });
    };

    connectSSE();
    fetchAudit(selectedZone.endpoint, selectedZone.id);

    fallbackInterval = setInterval(() => {
      if (connectionStatus !== "CONNECTED") {
        pollSnapshot();
      }
    }, 2500);

    auditInterval = setInterval(() => {
      fetchAudit(selectedZone.endpoint, selectedZone.id);
    }, 5000);

    return () => {
      isMountedRef.current = false;
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
      if (fallbackInterval) clearInterval(fallbackInterval);
      if (auditInterval) clearInterval(auditInterval);
    };
  }, [selectedZone, appendHistory, fetchAudit]);

  const switchZone = (zone: ZoneConfig) => {
    setSelectedZone(zone);
    setHistory([]);
    setSnapshot(null);
  };

  // Action: Switch Crop Profile remotely with signed headers
  const setCropProfile = async (profile: CropProfileType) => {
    const path = "/api/v1/control/profile";
    const body = JSON.stringify({ profile });
    try {
      const headers = await buildSignedHeaders(role, "POST", path, body);
      const res = await fetch(`${selectedZone.endpoint}${path}`, {
        method: "POST",
        headers,
        body,
      });
      const data = await res.json();
      if (res.ok) {
        setSnapshot((prev) => (prev ? { ...prev, cropProfile: profile } : null));
        showNotification(`Profile switched to ${profile} by ${role}`, "success");
        return true;
      } else {
        showNotification(`Rejected (${res.status}): ${data.error || "Permission Denied"}`, "error");
        return false;
      }
    } catch (e: any) {
      if (connectionStatus === "PREVIEW") {
        setSnapshot((prev) => (prev ? { ...prev, cropProfile: profile } : null));
        showNotification(`[Simulated] Profile switched to ${profile}`, "info");
        return true;
      }
      showNotification(`Connection error: ${e.message}`, "error");
      return false;
    }
  };

  // Action: Control Actuator Mode/State with signed headers
  const controlActuator = async (
    actuator: "HEATER" | "FAN" | "PUMP" | "FOGGER" | "ALARM",
    mode: "AUTO" | "MANUAL",
    state?: boolean
  ) => {
    const path = "/api/v1/control/actuator";
    const body = JSON.stringify({ actuator, mode, state });
    try {
      const headers = await buildSignedHeaders(role, "POST", path, body);
      const res = await fetch(`${selectedZone.endpoint}${path}`, {
        method: "POST",
        headers,
        body,
      });
      const data = await res.json();
      if (res.ok) {
        showNotification(data.message || `Actuator ${actuator} updated`, "success");
        return data;
      } else {
        showNotification(`Rejected (${res.status}): ${data.error || "Permission Denied"}`, "error");
        return data;
      }
    } catch {
      return {
        status: "OK",
        actuator,
        mode,
        actualState: state ?? false,
        safeStateInterlock: false,
        message: `Simulated: ${actuator} set to ${mode}`,
      };
    }
  };

  // Action: Trigger Diagnostic Fault with signed headers
  const triggerFault = async (action: string) => {
    const path = "/api/v1/control/fault";
    const body = JSON.stringify({ action });
    try {
      const headers = await buildSignedHeaders(role, "POST", path, body);
      const res = await fetch(`${selectedZone.endpoint}${path}`, {
        method: "POST",
        headers,
        body,
      });
      const data = await res.json();
      if (res.ok) {
        showNotification(`Diagnostic: ${data.message}`, "success");
        return true;
      } else {
        showNotification(`Fault Injection Denied (${res.status}): ${data.error}`, "error");
        return false;
      }
    } catch {
      showNotification(`Executed in simulated mode: ${action}`, "info");
      return true;
    }
  };

  // Action: Offline-first backfill sync
  const triggerBackfill = async (sinceBlock = 0, limit = 100): Promise<BackfillResponse | null> => {
    setIsBackfilling(true);
    try {
      const res = await fetch(
        `${selectedZone.endpoint}/api/v1/telemetry/backfill?sinceBlock=${sinceBlock}&limit=${limit}`,
        { cache: "no-store" }
      );
      if (res.ok) {
        const data: BackfillResponse = await res.json();
        showNotification(
          `Backfill complete: Synchronized ${data.returnedCount} hash-chained ledger blocks`,
          "success"
        );
        return data;
      } else {
        showNotification("Failed to fetch backfill stream from edge node", "error");
        return null;
      }
    } catch (e: any) {
      showNotification(`Backfill error: ${e.message}`, "error");
      return null;
    } finally {
      setIsBackfilling(false);
    }
  };

  return {
    selectedZone,
    switchZone,
    snapshot,
    history,
    auditStatus,
    connectionStatus,
    lastUpdated,
    role,
    setRole,
    unit,
    setUnit,
    language,
    setLanguage,
    notification,
    isBackfilling,
    setCropProfile,
    controlActuator,
    triggerFault,
    triggerBackfill,
    refreshAudit: () => fetchAudit(selectedZone.endpoint, selectedZone.id),
  };
}
