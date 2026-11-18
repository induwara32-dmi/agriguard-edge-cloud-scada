"use client";

import React from "react";
import { ShieldCheck, AlertOctagon, Lock } from "lucide-react";
import { LanguageCode, UnitSystem } from "../types/telemetry";
import { TRANSLATIONS } from "../lib/i18n";
import { formatTemp } from "../lib/units";

interface SafetyBannerProps {
  safeStateActive: boolean;
  voteDetail?: string;
  criticalTemp: number;
  currentTemp: number;
  language?: LanguageCode;
  unit?: UnitSystem;
}

export const SafetyBanner: React.FC<SafetyBannerProps> = ({
  safeStateActive,
  voteDetail,
  criticalTemp,
  currentTemp,
  language = "EN",
  unit = "METRIC",
}) => {
  const t = TRANSLATIONS[language] || TRANSLATIONS.EN;
  const currentTempDisplay = formatTemp(currentTemp, unit);
  const criticalTempDisplay = formatTemp(criticalTemp, unit);

  if (safeStateActive) {
    return (
      <div className="bg-red-950/90 border border-red-500/60 rounded-xl p-4 shadow-xl shadow-red-900/30 text-red-200 animate-pulse">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-lg bg-red-600/40 border border-red-500 flex items-center justify-center text-red-100 shrink-0">
              <AlertOctagon className="h-6 w-6 text-red-300" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-sm md:text-base text-red-100 tracking-wide">
                  {t.safety_tripped_title}
                </span>
                <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-red-500 text-white font-bold uppercase tracking-wider">
                  FAIL-SAFE TRIP
                </span>
              </div>
              <p className="text-xs text-red-300 mt-0.5">
                {voteDetail ||
                  `Critical excursion detected (${currentTempDisplay.text} > ${criticalTempDisplay.text} limit). Real-time safety interlocks engaged.`}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 text-xs font-mono bg-red-900/70 border border-red-600/60 px-3 py-1.5 rounded-lg text-white">
            <Lock className="h-4 w-4 text-red-300" />
            <span>HEATER OFF | FAN ON | ALARM ON | OVERRIDES LOCKED</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-emerald-950/40 border border-emerald-500/30 rounded-xl px-4 py-2.5 shadow-md text-emerald-200">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <ShieldCheck className="h-5 w-5 text-emerald-400 shrink-0" />
          <div className="flex items-center gap-2">
            <span className="font-bold text-xs md:text-sm text-emerald-300 tracking-wide">
              {t.safety_nominal_title}
            </span>
            <span className="text-[10px] font-mono text-emerald-400/80 bg-emerald-900/40 px-2 py-0.5 rounded border border-emerald-500/20">
              RATE-MONOTONIC ASSURANCE VALID
            </span>
          </div>
        </div>
        <div className="text-[11px] font-mono text-slate-400 flex items-center gap-2">
          <span>Safety Cutoff: {criticalTempDisplay.text}</span>
          <span className="text-slate-600">|</span>
          <span className="text-emerald-400 font-semibold">TMR Consensus Active</span>
        </div>
      </div>
    </div>
  );
};
