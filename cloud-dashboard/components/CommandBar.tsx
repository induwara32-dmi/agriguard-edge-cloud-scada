"use client";

import React from "react";
import {
  Layers,
  Sprout,
  Server,
  RefreshCw,
  Shield,
  Languages,
  Thermometer,
  Lock,
} from "lucide-react";
import {
  ZoneConfig,
  KNOWN_ZONES,
  CropProfileType,
  CROP_METAS,
  TelemetrySnapshot,
  SecurityRole,
  UnitSystem,
  LanguageCode,
} from "../types/telemetry";
import { TRANSLATIONS } from "../lib/i18n";
import { FeedbackNotification } from "./useTelemetry";

interface CommandBarProps {
  selectedZone: ZoneConfig;
  onSelectZone: (zone: ZoneConfig) => void;
  snapshot: TelemetrySnapshot | null;
  connectionStatus: "CONNECTED" | "RECONNECTING" | "PREVIEW";
  onSelectProfile: (profile: CropProfileType) => void;
  role: SecurityRole;
  onSelectRole: (role: SecurityRole) => void;
  unit: UnitSystem;
  onToggleUnit: () => void;
  language: LanguageCode;
  onSelectLanguage: (lang: LanguageCode) => void;
  notification: FeedbackNotification | null;
}

export const CommandBar: React.FC<CommandBarProps> = ({
  selectedZone,
  onSelectZone,
  snapshot,
  connectionStatus,
  onSelectProfile,
  role,
  onSelectRole,
  unit,
  onToggleUnit,
  language,
  onSelectLanguage,
  notification,
}) => {
  const activeCrop = snapshot?.cropProfile || "STRAWBERRY";
  const t = TRANSLATIONS[language] || TRANSLATIONS.EN;

  return (
    <header className="bg-slate-900/90 border-b border-slate-800/80 backdrop-blur-md sticky top-0 z-50 px-4 py-2.5 shadow-2xl">
      <div className="max-w-7xl mx-auto flex flex-col gap-2">
        <div className="flex flex-col lg:flex-row items-center justify-between gap-3">
          {/* Brand & Connection Status */}
          <div className="flex items-center gap-3 w-full lg:w-auto justify-between lg:justify-start">
            <div className="flex items-center gap-3">
              <div className="h-9 w-9 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-700 flex items-center justify-center shadow-lg shadow-emerald-500/20 ring-1 ring-emerald-400/30">
                <Sprout className="h-5 w-5 text-white" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-extrabold tracking-tight text-white text-base">
                    AgriGuard
                  </span>
                  <span className="text-[10px] font-mono tracking-widest uppercase px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-semibold">
                    SCADA Cloud
                  </span>
                </div>
                <p className="text-[11px] text-slate-400 hidden sm:block">
                  {t.brand_subtitle}
                </p>
              </div>
            </div>

            {/* Connection Status Pill */}
            <div className="flex items-center gap-2">
              {connectionStatus === "CONNECTED" && (
                <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-950/80 border border-emerald-500/40 text-emerald-400 text-xs font-mono">
                  <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                  <span>{t.status_sse}</span>
                </div>
              )}
              {connectionStatus === "RECONNECTING" && (
                <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-amber-950/80 border border-amber-500/40 text-amber-400 text-xs font-mono">
                  <RefreshCw className="h-3 w-3 animate-spin" />
                  <span>{t.status_reconnecting}</span>
                </div>
              )}
              {connectionStatus === "PREVIEW" && (
                <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-slate-800 border border-slate-700 text-slate-300 text-xs font-mono">
                  <span className="h-2 w-2 rounded-full bg-sky-400" />
                  <span>{t.status_preview}</span>
                </div>
              )}
            </div>
          </div>

          {/* Middle: Zone Switcher Tabs */}
          <div className="flex items-center bg-slate-950/70 p-1 rounded-xl border border-slate-800 shadow-inner">
            <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider px-2 flex items-center gap-1.5 hidden md:flex">
              <Layers className="h-3.5 w-3.5 text-slate-400" />
              <span>{t.zone_label}:</span>
            </div>
            <div className="flex gap-1">
              {KNOWN_ZONES.map((zone) => {
                const active = selectedZone.id === zone.id;
                return (
                  <button
                    key={zone.id}
                    onClick={() => onSelectZone(zone)}
                    className={`px-3 py-1 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 ${
                      active
                        ? "bg-emerald-600 text-white shadow-md shadow-emerald-600/30 ring-1 ring-emerald-400/40"
                        : "text-slate-400 hover:text-slate-200 hover:bg-slate-800/60"
                    }`}
                  >
                    <Server className="h-3.5 w-3.5" />
                    <span>{zone.id}</span>
                    <span
                      className={`text-[10px] px-1 py-0.2 rounded font-mono ${
                        active ? "bg-emerald-700/60 text-white" : "bg-slate-800 text-slate-400"
                      }`}
                    >
                      :{zone.port}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right Controls: Role, Units, Language, Crop */}
          <div className="flex flex-wrap items-center gap-2 w-full lg:w-auto justify-end">
            {/* RBAC Role Selector */}
            <div className="flex items-center bg-slate-950/70 border border-slate-800 rounded-lg px-2 py-1 gap-1.5">
              <Shield className={`w-3.5 h-3.5 ${role === "AGRONOMIST" ? "text-purple-400" : role === "OPERATOR" ? "text-emerald-400" : "text-slate-400"}`} />
              <select
                value={role}
                onChange={(e) => onSelectRole(e.target.value as SecurityRole)}
                className="bg-transparent text-xs font-semibold text-slate-200 focus:outline-none cursor-pointer"
                title="Select Operator Role (IEC 62443 RBAC)"
              >
                <option value="OPERATOR" className="bg-slate-900 text-white">OPERATOR</option>
                <option value="AGRONOMIST" className="bg-slate-900 text-white">AGRONOMIST</option>
                <option value="VIEWER" className="bg-slate-900 text-white">VIEWER (Read-Only)</option>
              </select>
            </div>

            {/* Metric / Imperial Unit Toggle */}
            <button
              onClick={onToggleUnit}
              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-950/70 border border-slate-800 text-xs font-mono text-slate-300 hover:text-white hover:border-slate-700 transition"
              title="Toggle Unit System"
            >
              <Thermometer className="w-3.5 h-3.5 text-amber-400" />
              <span>{unit === "METRIC" ? "°C / kPa" : "°F / psi"}</span>
            </button>

            {/* Language Switcher */}
            <div className="flex items-center bg-slate-950/70 border border-slate-800 rounded-lg px-2 py-1 gap-1">
              <Languages className="w-3.5 h-3.5 text-sky-400" />
              <select
                value={language}
                onChange={(e) => onSelectLanguage(e.target.value as LanguageCode)}
                className="bg-transparent text-xs font-semibold text-slate-200 focus:outline-none cursor-pointer"
              >
                <option value="EN" className="bg-slate-900 text-white">EN</option>
                <option value="SI" className="bg-slate-900 text-white">සිංහල</option>
                <option value="TA" className="bg-slate-900 text-white">தமிழ்</option>
              </select>
            </div>

            {/* Crop Preset Dropdown */}
            <div className="flex items-center bg-slate-950/70 border border-slate-800 rounded-lg px-2.5 py-1 gap-1.5">
              <Sprout className="h-3.5 w-3.5 text-emerald-400" />
              <select
                value={activeCrop}
                onChange={(e) =>
                  onSelectProfile(e.target.value as CropProfileType)
                }
                disabled={role === "VIEWER"}
                className={`bg-transparent text-xs font-semibold text-white focus:outline-none cursor-pointer ${role === "VIEWER" ? "opacity-50 cursor-not-allowed" : ""}`}
              >
                {(Object.keys(CROP_METAS) as CropProfileType[]).map((key) => (
                  <option key={key} value={key} className="bg-slate-900 text-white">
                    {CROP_METAS[key].name}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Global Action Notification / Error Toast */}
        {notification && (
          <div
            className={`text-xs px-3 py-1.5 rounded-lg flex items-center justify-between border animate-fadeIn transition ${
              notification.type === "error"
                ? "bg-red-950/80 border-red-500/50 text-red-300"
                : notification.type === "success"
                ? "bg-emerald-950/80 border-emerald-500/50 text-emerald-300"
                : "bg-blue-950/80 border-blue-500/50 text-blue-300"
            }`}
          >
            <div className="flex items-center gap-2">
              {notification.type === "error" && <Lock className="w-3.5 h-3.5 text-red-400" />}
              <span className="font-mono">{notification.text}</span>
            </div>
            <span className="text-[10px] text-slate-400 uppercase font-mono">
              IEC 62443 Security Log
            </span>
          </div>
        )}
      </div>
    </header>
  );
};
