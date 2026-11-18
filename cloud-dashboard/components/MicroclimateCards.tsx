"use client";

import React from "react";
import { Thermometer, Droplets, Wind, Waves } from "lucide-react";
import { TelemetrySnapshot, CROP_METAS, UnitSystem, LanguageCode } from "../types/telemetry";
import { formatTemp, formatVpd } from "../lib/units";
import { TRANSLATIONS } from "../lib/i18n";

interface MicroclimateCardsProps {
  snapshot: TelemetrySnapshot | null;
  unit?: UnitSystem;
  language?: LanguageCode;
}

export const MicroclimateCards: React.FC<MicroclimateCardsProps> = ({
  snapshot,
  unit = "METRIC",
  language = "EN",
}) => {
  const t = TRANSLATIONS[language] || TRANSLATIONS.EN;
  const crop = snapshot?.cropProfile || "STRAWBERRY";
  const meta = CROP_METAS[crop];

  const temp = snapshot?.votedTemperature ?? 24.5;
  const humidity = snapshot?.humidityPercent ?? 68.0;
  const soil = snapshot?.soilMoisturePercent ?? 48.0;
  const vpd = snapshot?.vpdKpa ?? 1.05;
  const voteStatus = snapshot?.voteStatus ?? "OK";

  const tempDisplay = formatTemp(temp, unit);
  const vpdDisplay = formatVpd(vpd, unit);
  const critTempDisplay = formatTemp(meta.criticalTemp, unit);

  // VPD Status Check
  const [vpdMin, vpdMax] = meta.optimalVpd;
  const isVpdOptimal = vpd >= vpdMin && vpd <= vpdMax;
  const vpdMinDisplay = formatVpd(vpdMin, unit);
  const vpdMaxDisplay = formatVpd(vpdMax, unit);

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {/* 1. TMR Voted Temperature */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between hover:border-slate-700 transition-all">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Thermometer className="h-5 w-5" />
            </div>
            <div>
              <span className="text-[11px] font-mono tracking-wider text-slate-400 uppercase">
                {t.temp_title}
              </span>
              <h4 className="text-xs font-semibold text-slate-200">Consensus</h4>
            </div>
          </div>
          <span
            className={`text-[10px] font-mono px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
              voteStatus === "OK"
                ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                : voteStatus === "MASKED"
                ? "bg-amber-500/20 text-amber-400 border border-amber-500/30"
                : "bg-red-500/20 text-red-400 border border-red-500/30"
            }`}
          >
            {voteStatus}
          </span>
        </div>

        <div className="my-3">
          <div className="flex items-baseline gap-1">
            <span className="text-3xl font-extrabold text-white font-mono tracking-tight">
              {tempDisplay.val.toFixed(1)}
            </span>
            <span className="text-sm font-semibold text-slate-400">{tempDisplay.unit}</span>
          </div>
        </div>

        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] font-mono text-slate-400">
          <span>Target: {meta.targetTemp}</span>
          <span className="text-red-400/80">Max: {critTempDisplay.text}</span>
        </div>
      </div>

      {/* 2. Vapor Pressure Deficit (VPD) */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between hover:border-slate-700 transition-all">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-sky-500/10 text-sky-400 border border-sky-500/20">
              <Wind className="h-5 w-5" />
            </div>
            <div>
              <span className="text-[11px] font-mono tracking-wider text-slate-400 uppercase">
                {t.vpd_title}
              </span>
              <h4 className="text-xs font-semibold text-slate-200">Tetens Model</h4>
            </div>
          </div>
          <span
            className={`text-[10px] font-mono px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
              isVpdOptimal
                ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                : "bg-amber-500/20 text-amber-400 border border-amber-500/30"
            }`}
          >
            {isVpdOptimal ? "OPTIMAL" : "STRESS"}
          </span>
        </div>

        <div className="my-3">
          <div className="flex items-baseline gap-1">
            <span className="text-3xl font-extrabold text-white font-mono tracking-tight">
              {vpdDisplay.val.toFixed(3)}
            </span>
            <span className="text-sm font-semibold text-slate-400">{vpdDisplay.unit}</span>
          </div>
          <div className="w-full bg-slate-800 h-1.5 rounded-full mt-2 overflow-hidden relative">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                isVpdOptimal ? "bg-emerald-400" : "bg-amber-400"
              }`}
              style={{
                width: `${Math.min(100, Math.max(5, (vpd / 2.0) * 100))}%`,
              }}
            />
          </div>
        </div>

        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] font-mono text-slate-400">
          <span>Target: {vpdMinDisplay.val.toFixed(2)} - {vpdMaxDisplay.val.toFixed(2)} {vpdDisplay.unit}</span>
          <span className="truncate max-w-[120px] text-[10px] text-slate-400">
            {isVpdOptimal ? "Nominal" : "Stress Risk"}
          </span>
        </div>
      </div>

      {/* 3. Relative Humidity */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between hover:border-slate-700 transition-all">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-400 border border-blue-500/20">
              <Droplets className="h-5 w-5" />
            </div>
            <div>
              <span className="text-[11px] font-mono tracking-wider text-slate-400 uppercase">
                {t.humidity_title}
              </span>
              <h4 className="text-xs font-semibold text-slate-200">Ambient RH</h4>
            </div>
          </div>
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30 font-bold uppercase tracking-wider">
            RH-ACTIVE
          </span>
        </div>

        <div className="my-3">
          <div className="flex items-baseline gap-1">
            <span className="text-3xl font-extrabold text-white font-mono tracking-tight">
              {humidity.toFixed(1)}
            </span>
            <span className="text-sm font-semibold text-slate-400">%</span>
          </div>
        </div>

        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] font-mono text-slate-400">
          <span>Target: {meta.targetHumidity}</span>
          <span className="text-blue-400/80">Closed-Loop</span>
        </div>
      </div>

      {/* 4. Volumetric Soil Moisture */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between hover:border-slate-700 transition-all">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/20">
              <Waves className="h-5 w-5" />
            </div>
            <div>
              <span className="text-[11px] font-mono tracking-wider text-slate-400 uppercase">
                {t.soil_title}
              </span>
              <h4 className="text-xs font-semibold text-slate-200">VWC Sensor</h4>
            </div>
          </div>
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-teal-500/20 text-teal-400 border border-teal-500/30 font-bold uppercase tracking-wider">
            IRRIGATION
          </span>
        </div>

        <div className="my-3">
          <div className="flex items-baseline gap-1">
            <span className="text-3xl font-extrabold text-white font-mono tracking-tight">
              {soil.toFixed(1)}
            </span>
            <span className="text-sm font-semibold text-slate-400">%</span>
          </div>
        </div>

        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] font-mono text-slate-400">
          <span>Target: {meta.targetSoil}</span>
          <span className="text-teal-400/80">Pulse Controlled</span>
        </div>
      </div>
    </div>
  );
};
