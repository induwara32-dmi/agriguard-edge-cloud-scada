"use client";

import React, { useState } from "react";
import {
  Flame,
  Fan,
  Droplet,
  CloudFog,
  BellRing,
  Lock,
  Sliders,
  Power,
  Shield,
} from "lucide-react";
import { TelemetrySnapshot, SecurityRole, LanguageCode } from "../types/telemetry";
import { TRANSLATIONS } from "../lib/i18n";

interface ActuatorMatrixProps {
  snapshot: TelemetrySnapshot | null;
  onControlActuator: (
    actuator: "HEATER" | "FAN" | "PUMP" | "FOGGER" | "ALARM",
    mode: "AUTO" | "MANUAL",
    state?: boolean
  ) => Promise<any>;
  role?: SecurityRole;
  language?: LanguageCode;
}

export const ActuatorMatrix: React.FC<ActuatorMatrixProps> = ({
  snapshot,
  onControlActuator,
  role = "OPERATOR",
  language = "EN",
}) => {
  const t = TRANSLATIONS[language] || TRANSLATIONS.EN;
  const actuators = snapshot?.actuators;
  const isInterlocked =
    actuators?.safeStateInterlock || snapshot?.diagnostics.safeStateActive;
  const isViewer = role === "VIEWER";

  const [loadingActuator, setLoadingActuator] = useState<string | null>(null);

  const actuatorList: Array<{
    id: "HEATER" | "FAN" | "PUMP" | "FOGGER" | "ALARM";
    label: string;
    sublabel: string;
    isOn: boolean;
    icon: React.ReactNode;
    color: string;
  }> = [
    {
      id: "HEATER",
      label: t.actuator_heater,
      sublabel: "Root & Canopy Curtain",
      isOn: actuators?.heater ?? false,
      icon: <Flame className="h-4 w-4" />,
      color: "amber",
    },
    {
      id: "FAN",
      label: t.actuator_fan,
      sublabel: "Extraction Cooling",
      isOn: actuators?.fan ?? false,
      icon: <Fan className="h-4 w-4" />,
      color: "blue",
    },
    {
      id: "PUMP",
      label: t.actuator_pump,
      sublabel: "Substrate Drip Line",
      isOn: actuators?.pump ?? false,
      icon: <Droplet className="h-4 w-4" />,
      color: "emerald",
    },
    {
      id: "FOGGER",
      label: t.actuator_fogger,
      sublabel: "High-Pressure RH Mist",
      isOn: actuators?.fogger ?? false,
      icon: <CloudFog className="h-4 w-4" />,
      color: "teal",
    },
    {
      id: "ALARM",
      label: t.actuator_alarm,
      sublabel: "Audio-Visual Beacon",
      isOn: actuators?.alarm ?? false,
      icon: <BellRing className="h-4 w-4" />,
      color: "red",
    },
  ];

  const handleToggleState = async (
    id: "HEATER" | "FAN" | "PUMP" | "FOGGER" | "ALARM",
    currentState: boolean
  ) => {
    setLoadingActuator(id);
    try {
      await onControlActuator(id, "MANUAL", !currentState);
    } finally {
      setLoadingActuator(null);
    }
  };

  const handleSetAuto = async (
    id: "HEATER" | "FAN" | "PUMP" | "FOGGER" | "ALARM"
  ) => {
    setLoadingActuator(id);
    try {
      await onControlActuator(id, "AUTO");
    } finally {
      setLoadingActuator(null);
    }
  };

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/20">
            <Sliders className="h-4 w-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-white flex items-center gap-2">
              <span>{t.actuators_title}</span>
              {isInterlocked && (
                <span className="text-[10px] font-mono bg-red-500/20 text-red-400 border border-red-500/30 px-2 py-0.5 rounded font-bold uppercase">
                  Interlocked
                </span>
              )}
              {isViewer && (
                <span className="text-[10px] font-mono bg-slate-800 text-slate-400 border border-slate-700 px-2 py-0.5 rounded flex items-center gap-1">
                  <Shield className="w-2.5 h-2.5" />
                  RBAC: VIEWER
                </span>
              )}
            </h3>
            <p className="text-xs text-slate-400">
              Closed-Loop AUTO vs Manual Override with Critical Safety Interlock
            </p>
          </div>
        </div>

        <div className="text-[11px] font-mono text-slate-400 hidden sm:block">
          Safety Interlock:{" "}
          <span
            className={
              isInterlocked ? "text-red-400 font-bold" : "text-emerald-400"
            }
          >
            {isInterlocked ? "FORCED OVERRIDE ACTIVE" : "ARMED"}
          </span>
        </div>
      </div>

      {/* Grid of 5 Actuators */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-3 mt-4">
        {actuatorList.map((item) => {
          const isLoading = loadingActuator === item.id;
          const disabled = isInterlocked || isViewer || isLoading;

          return (
            <div
              key={item.id}
              className={`border rounded-xl p-3 flex flex-col justify-between transition-all ${
                item.isOn
                  ? "bg-slate-900 border-emerald-500/40 shadow-sm shadow-emerald-500/10"
                  : "bg-slate-950/60 border-slate-800/80"
              }`}
            >
              <div>
                <div className="flex items-center justify-between mb-2">
                  <div
                    className={`p-1.5 rounded-lg ${
                      item.isOn
                        ? "bg-emerald-500/20 text-emerald-300"
                        : "bg-slate-800 text-slate-400"
                    }`}
                  >
                    {item.icon}
                  </div>
                  <span
                    className={`text-[10px] font-mono px-2 py-0.5 rounded font-bold uppercase tracking-wider ${
                      item.isOn
                        ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                        : "bg-slate-800 text-slate-400 border border-slate-700"
                    }`}
                  >
                    {item.isOn ? t.state_on : t.state_off}
                  </span>
                </div>

                <h4 className="text-xs font-bold text-white tracking-wide truncate">
                  {item.label}
                </h4>
                <p className="text-[10px] text-slate-400 truncate mb-3">
                  {item.sublabel}
                </p>
              </div>

              {/* Controls */}
              <div className="space-y-1.5 pt-2 border-t border-slate-800/80">
                <button
                  onClick={() => handleToggleState(item.id, item.isOn)}
                  disabled={disabled}
                  title={
                    isViewer
                      ? "Role VIEWER lacks permission"
                      : isInterlocked
                      ? t.override_locked
                      : "Toggle manual state"
                  }
                  className={`w-full py-1 px-2 rounded text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${
                    disabled
                      ? "opacity-50 cursor-not-allowed bg-slate-800 text-slate-500"
                      : item.isOn
                      ? "bg-red-600/80 hover:bg-red-500 text-white shadow-sm"
                      : "bg-emerald-600/80 hover:bg-emerald-500 text-white shadow-sm"
                  }`}
                >
                  <Power className="h-3 w-3" />
                  <span>{item.isOn ? "Turn OFF" : "Turn ON"}</span>
                </button>

                <button
                  onClick={() => handleSetAuto(item.id)}
                  disabled={disabled}
                  className={`w-full py-0.5 px-2 rounded text-[10px] font-mono transition-all ${
                    disabled
                      ? "opacity-40 cursor-not-allowed text-slate-600"
                      : "text-slate-400 hover:text-white bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60"
                  }`}
                >
                  Set to AUTO
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
