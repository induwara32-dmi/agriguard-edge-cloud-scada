"use client";

import React from "react";
import { TelemetrySnapshot, UnitSystem, LanguageCode, FungalRiskLevel } from "../types/telemetry";
import { formatTemp, formatSpread } from "../lib/units";
import { TRANSLATIONS } from "../lib/i18n";
import { ShieldAlert, Droplets, Sprout, SunMedium, AlertTriangle, CheckCircle2 } from "lucide-react";

interface AgronomyCardProps {
  snapshot: TelemetrySnapshot | null;
  unit: UnitSystem;
  language: LanguageCode;
}

export function AgronomyCard({ snapshot, unit, language }: AgronomyCardProps) {
  const t = TRANSLATIONS[language] || TRANSLATIONS.EN;

  const agro = snapshot?.agronomy;
  const dewC = snapshot?.dewPointC ?? agro?.dewPointC ?? 18.2;
  const spreadC = snapshot?.dewPointSpreadC ?? agro?.dewPointSpreadC ?? 6.2;
  const fungalLevel: FungalRiskLevel = (snapshot?.fungalRiskLevel ?? agro?.fungalRiskLevel ?? "LOW") as FungalRiskLevel;
  const gdd = snapshot?.gddAccumulated ?? agro?.gddAccumulated ?? 14.5;
  const et0 = snapshot?.et0MmDay ?? agro?.et0MmDay ?? 3.8;
  const advisory = snapshot?.irrigationAdvisory ?? agro?.irrigationAdvisory ?? "Moisture optimal. Regular pulse schedule active.";

  const dewDisplay = formatTemp(dewC, unit);
  const spreadDisplay = formatSpread(spreadC, unit);

  // Risk Badge Color & Text
  const getRiskBadge = (level: FungalRiskLevel) => {
    switch (level) {
      case "CRITICAL":
        return {
          bg: "bg-red-500/20 border-red-500/50 text-red-400 animate-pulse",
          icon: <AlertTriangle className="w-4 h-4 text-red-400" />,
          label: t.risk_critical,
        };
      case "HIGH":
        return {
          bg: "bg-amber-500/20 border-amber-500/50 text-amber-300",
          icon: <ShieldAlert className="w-4 h-4 text-amber-400" />,
          label: t.risk_high,
        };
      case "MODERATE":
        return {
          bg: "bg-yellow-500/20 border-yellow-500/50 text-yellow-300",
          icon: <ShieldAlert className="w-4 h-4 text-yellow-400" />,
          label: t.risk_moderate,
        };
      default:
        return {
          bg: "bg-emerald-500/15 border-emerald-500/30 text-emerald-400",
          icon: <CheckCircle2 className="w-4 h-4 text-emerald-400" />,
          label: t.risk_low,
        };
    }
  };

  const riskBadge = getRiskBadge(fungalLevel);

  return (
    <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 shadow-lg relative overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-800/80">
        <div className="flex items-center gap-2">
          <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <Sprout className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white tracking-wide">
              {t.agronomy_card_title}
            </h3>
            <p className="text-xs text-slate-400">
              Magnus-Tetens thermodynamic Dew Point & Spore Germination Defense
            </p>
          </div>
        </div>

        {/* Fungal Risk Badge */}
        <div className={`flex items-center gap-1.5 px-3 py-1 rounded-full border text-xs font-medium ${riskBadge.bg}`}>
          {riskBadge.icon}
          <span>{riskBadge.label}</span>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
        {/* Dew Point */}
        <div className="bg-slate-950/60 border border-slate-800/60 rounded-lg p-3">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-1">
            <span>{t.dew_point}</span>
            <Droplets className="w-3.5 h-3.5 text-blue-400" />
          </div>
          <div className="text-xl font-bold text-slate-100 font-mono">
            {dewDisplay.text}
          </div>
          <p className="text-[10px] text-slate-400 mt-0.5">
            Canopy saturation threshold
          </p>
        </div>

        {/* Dew Point Spread */}
        <div className="bg-slate-950/60 border border-slate-800/60 rounded-lg p-3">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-1">
            <span>{t.dew_spread}</span>
            <span className={`text-[10px] font-mono px-1 rounded ${spreadC < 2.0 ? "bg-red-500/20 text-red-300" : "bg-emerald-500/20 text-emerald-300"}`}>
              {spreadC < 2.0 ? "RISK" : "SAFE"}
            </span>
          </div>
          <div className={`text-xl font-bold font-mono ${spreadC < 2.0 ? "text-amber-300" : "text-emerald-400"}`}>
            {spreadDisplay.text}
          </div>
          <p className="text-[10px] text-slate-400 mt-0.5">
            Condensation margin: T - Td
          </p>
        </div>

        {/* GDD */}
        <div className="bg-slate-950/60 border border-slate-800/60 rounded-lg p-3">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-1">
            <span>{t.gdd_accumulated}</span>
            <SunMedium className="w-3.5 h-3.5 text-amber-400" />
          </div>
          <div className="text-xl font-bold text-amber-300 font-mono">
            {gdd.toFixed(1)} <span className="text-xs font-normal text-slate-400">°D</span>
          </div>
          <p className="text-[10px] text-slate-400 mt-0.5">
            Physiological thermal sum (Tbase=10°C)
          </p>
        </div>

        {/* Reference ET0 */}
        <div className="bg-slate-950/60 border border-slate-800/60 rounded-lg p-3">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-1">
            <span>{t.et0_rate}</span>
            <Droplets className="w-3.5 h-3.5 text-cyan-400" />
          </div>
          <div className="text-xl font-bold text-cyan-300 font-mono">
            {et0.toFixed(2)} <span className="text-xs font-normal text-slate-400">mm/day</span>
          </div>
          <p className="text-[10px] text-slate-400 mt-0.5">
            FAO-56 evapotranspiration demand
          </p>
        </div>
      </div>

      {/* Advisory Banner */}
      <div className="bg-slate-950/80 border border-slate-800 rounded-lg p-3 flex items-start gap-2.5">
        <div className="mt-0.5 p-1 rounded bg-blue-500/10 text-blue-400">
          <Droplets className="w-4 h-4" />
        </div>
        <div>
          <div className="text-xs font-medium text-slate-300">
            {t.irrigation_advisory}
          </div>
          <div className="text-xs text-slate-400 mt-0.5 font-mono">
            {advisory}
          </div>
        </div>
      </div>
    </div>
  );
}
