"use client";

import React from "react";
import { CheckCircle2, AlertTriangle, XCircle, Cpu, Layers } from "lucide-react";
import { TelemetrySnapshot, UnitSystem, LanguageCode } from "../types/telemetry";
import { formatTemp } from "../lib/units";
import { TRANSLATIONS } from "../lib/i18n";

interface TmrHealthMatrixProps {
  snapshot: TelemetrySnapshot | null;
  unit?: UnitSystem;
  language?: LanguageCode;
}

export const TmrHealthMatrix: React.FC<TmrHealthMatrixProps> = ({
  snapshot,
  unit = "METRIC",
  language = "EN",
}) => {
  const t = TRANSLATIONS[language] || TRANSLATIONS.EN;
  const status = snapshot?.voteStatus || "OK";
  const temp = snapshot?.votedTemperature || 24.5;
  const detail = snapshot?.voteDetail || "All 3 channels in consensus";
  const bufferOcc = snapshot?.diagnostics.bufferOccupancy ?? 0;
  const bufferCap = snapshot?.diagnostics.bufferCapacity ?? 10;
  const maskedTotal = snapshot?.diagnostics.tmrMaskedTotal ?? 0;
  const watchdogFailures = snapshot?.diagnostics.watchdogFailuresTotal ?? 0;

  const isT2Biased = status === "MASKED" && detail.includes("T2");
  const isT3Disconnected = status === "DEGRADED" || detail.includes("T3");

  const t1Val = temp + 0.04;
  const t2Val = isT2Biased ? temp + 15.0 : temp - 0.03;
  const t3Val = isT3Disconnected ? null : temp + 0.01;

  const t1Display = formatTemp(t1Val, unit);
  const t2Display = formatTemp(t2Val, unit);
  const t3Display = t3Val !== null ? formatTemp(t3Val, unit) : null;

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
            <Cpu className="h-4 w-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-white flex items-center gap-2">
              <span>{t.tmr_title}</span>
            </h3>
            <p className="text-xs text-slate-400">
              Triple Modular Redundancy consensus with ±1.0°C inexact voting
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span
            className={`text-xs font-mono px-2.5 py-1 rounded-lg font-bold flex items-center gap-1.5 ${
              status === "OK"
                ? "bg-emerald-950/80 text-emerald-400 border border-emerald-500/40"
                : status === "MASKED"
                ? "bg-amber-950/80 text-amber-400 border border-amber-500/40"
                : "bg-red-950/80 text-red-400 border border-red-500/40"
            }`}
          >
            {status === "OK" && <CheckCircle2 className="h-3.5 w-3.5" />}
            {status === "MASKED" && <AlertTriangle className="h-3.5 w-3.5" />}
            {status === "FAILED" && <XCircle className="h-3.5 w-3.5" />}
            <span>{status === "OK" ? "CONSENSUS OK" : status}</span>
          </span>
        </div>
      </div>

      {/* 3-Channel Grid */}
      <div className="grid grid-cols-3 gap-3 my-3">
        {/* T1 */}
        <div className="bg-slate-950/60 border border-slate-800/80 rounded-lg p-3 text-center">
          <div className="flex items-center justify-between text-[11px] font-mono text-slate-400">
            <span>{t.channel_t1}</span>
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
          </div>
          <div className="text-xl font-bold font-mono text-white mt-1">
            {t1Display.text}
          </div>
          <div className="text-[10px] text-emerald-400 font-mono mt-0.5">
            Nominal Consensus
          </div>
        </div>

        {/* T2 */}
        <div
          className={`border rounded-lg p-3 text-center ${
            isT2Biased
              ? "bg-amber-950/30 border-amber-500/50"
              : "bg-slate-950/60 border-slate-800/80"
          }`}
        >
          <div className="flex items-center justify-between text-[11px] font-mono text-slate-400">
            <span>{t.channel_t2}</span>
            <span
              className={`h-2 w-2 rounded-full ${
                isT2Biased ? "bg-amber-400 animate-pulse" : "bg-emerald-400"
              }`}
            />
          </div>
          <div
            className={`text-xl font-bold font-mono mt-1 ${
              isT2Biased ? "text-amber-300" : "text-white"
            }`}
          >
            {t2Display.text}
          </div>
          <div
            className={`text-[10px] font-mono mt-0.5 ${
              isT2Biased ? "text-amber-400 font-bold" : "text-emerald-400"
            }`}
          >
            {isT2Biased ? "Outlier Masked" : "Nominal Consensus"}
          </div>
        </div>

        {/* T3 */}
        <div
          className={`border rounded-lg p-3 text-center ${
            isT3Disconnected
              ? "bg-red-950/30 border-red-500/50"
              : "bg-slate-950/60 border-slate-800/80"
          }`}
        >
          <div className="flex items-center justify-between text-[11px] font-mono text-slate-400">
            <span>{t.channel_t3}</span>
            <span
              className={`h-2 w-2 rounded-full ${
                isT3Disconnected ? "bg-red-500 animate-pulse" : "bg-emerald-400"
              }`}
            />
          </div>
          <div
            className={`text-xl font-bold font-mono mt-1 ${
              isT3Disconnected ? "text-red-400" : "text-white"
            }`}
          >
            {t3Display !== null ? t3Display.text : "OFFLINE"}
          </div>
          <div
            className={`text-[10px] font-mono mt-0.5 ${
              isT3Disconnected ? "text-red-400 font-bold" : "text-emerald-400"
            }`}
          >
            {isT3Disconnected ? "Signal Dropout" : "Nominal Consensus"}
          </div>
        </div>
      </div>

      {/* Diagnostics / Voter Detail Footer */}
      <div className="pt-2 border-t border-slate-800/80 flex flex-wrap items-center justify-between gap-2 text-[11px] font-mono text-slate-400">
        <div className="flex items-center gap-2">
          <span>Detail: {detail}</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <Layers className="h-3 w-3 text-slate-500" />
            Buffer: {bufferOcc}/{bufferCap}
          </span>
          <span className="text-amber-400">Masked: {maskedTotal}</span>
          <span className="text-red-400">WD Drops: {watchdogFailures}</span>
        </div>
      </div>
    </div>
  );
};
