"use client";

import React from "react";
import { CommandBar } from "../components/CommandBar";
import { SafetyBanner } from "../components/SafetyBanner";
import { MicroclimateCards } from "../components/MicroclimateCards";
import { AgronomyCard } from "../components/AgronomyCard";
import { TrendChart } from "../components/TrendChart";
import { TmrHealthMatrix } from "../components/TmrHealthMatrix";
import { ActuatorMatrix } from "../components/ActuatorMatrix";
import { AuditCommissioningPanel } from "../components/AuditCommissioningPanel";
import { useTelemetry } from "../components/useTelemetry";
import { CROP_METAS } from "../types/telemetry";

export default function CloudDashboardPage() {
  const {
    selectedZone,
    switchZone,
    snapshot,
    history,
    auditStatus,
    connectionStatus,
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
    refreshAudit,
  } = useTelemetry();

  const crop = snapshot?.cropProfile || "STRAWBERRY";
  const meta = CROP_METAS[crop];
  const safeStateActive =
    snapshot?.actuators?.safeStateInterlock ||
    snapshot?.diagnostics?.safeStateActive ||
    false;

  return (
    <div className="min-h-screen bg-[#080c14] text-slate-100 flex flex-col font-sans">
      {/* 1. Top Estate Command Bar */}
      <CommandBar
        selectedZone={selectedZone}
        onSelectZone={switchZone}
        snapshot={snapshot}
        connectionStatus={connectionStatus}
        onSelectProfile={setCropProfile}
        role={role}
        onSelectRole={setRole}
        unit={unit}
        onToggleUnit={() => setUnit((u) => (u === "METRIC" ? "IMPERIAL" : "METRIC"))}
        language={language}
        onSelectLanguage={setLanguage}
        notification={notification}
      />

      {/* Main Content Dashboard */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 md:p-6 space-y-5">
        {/* 2. Critical Safety Interlock Banner */}
        <SafetyBanner
          safeStateActive={safeStateActive}
          voteDetail={snapshot?.voteDetail}
          criticalTemp={meta.criticalTemp}
          currentTemp={snapshot?.votedTemperature ?? 24.5}
          language={language}
          unit={unit}
        />

        {/* 3. Microclimate Telemetry Cards (Temp, VPD, RH, Soil) */}
        <MicroclimateCards snapshot={snapshot} unit={unit} language={language} />

        {/* 4. Predictive Agronomy & Fungal Risk Card */}
        <AgronomyCard snapshot={snapshot} unit={unit} language={language} />

        {/* 5. 60-Second Real-Time SVG Trend Chart */}
        <TrendChart history={history} unit={unit} />

        {/* 6. Two-Column Grid: TMR Sensor Health & Actuator Matrix */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <TmrHealthMatrix snapshot={snapshot} unit={unit} language={language} />
          <ActuatorMatrix
            snapshot={snapshot}
            onControlActuator={controlActuator}
            role={role}
            language={language}
          />
        </div>

        {/* 7. Remote Diagnostics & Cryptographic Audit Panel */}
        <AuditCommissioningPanel
          auditStatus={auditStatus}
          onTriggerFault={triggerFault}
          onRefreshAudit={refreshAudit}
          onTriggerBackfill={() => triggerBackfill(0, 100)}
          isBackfilling={isBackfilling}
          role={role}
          language={language}
        />
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-slate-950/60 py-4 px-6 text-center text-xs font-mono text-slate-500">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>
            AgriGuard Cloud Command Center &bull; IEC 62443 Zero-Trust Edge SCADA Architecture
          </span>
          <span className="text-slate-400">
            Rate-Monotonic Bounds (Liu &amp; Layland U &le; 75.68%) &bull; Pure Java Standard Library
          </span>
        </div>
      </footer>
    </div>
  );
}
