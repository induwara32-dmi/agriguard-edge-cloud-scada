"use client";

import React, { useState } from "react";
import { Activity } from "lucide-react";
import { TelemetryHistoryPoint, UnitSystem } from "../types/telemetry";
import { formatTemp } from "../lib/units";

interface TrendChartProps {
  history: TelemetryHistoryPoint[];
  unit?: UnitSystem;
}

export const TrendChart: React.FC<TrendChartProps> = ({ history, unit = "METRIC" }) => {
  const [showTemp, setShowTemp] = useState(true);
  const [showHumidity, setShowHumidity] = useState(true);
  const [showSoil, setShowSoil] = useState(true);

  // SVG dimensions
  const width = 800;
  const height = 220;
  const padding = { top: 20, right: 35, bottom: 30, left: 45 };
  const graphWidth = width - padding.left - padding.right;
  const graphHeight = height - padding.top - padding.bottom;

  const points = history.length > 0 ? history : [
    { time: "00:00:00", temp: 24.5, humidity: 68.0, soil: 48.0, vpd: 1.05 }
  ];

  // Temp: 15°C to 45°C
  const tempMin = 15;
  const tempMax = 45;
  const pctMin = 0;
  const pctMax = 100;

  const scaleX = (index: number) => {
    if (points.length <= 1) return padding.left;
    return padding.left + (index / (points.length - 1)) * graphWidth;
  };

  const scaleTempY = (val: number) => {
    const clamped = Math.max(tempMin, Math.min(tempMax, val));
    return (
      padding.top +
      graphHeight -
      ((clamped - tempMin) / (tempMax - tempMin)) * graphHeight
    );
  };

  const scalePctY = (val: number) => {
    const clamped = Math.max(pctMin, Math.min(pctMax, val));
    return (
      padding.top +
      graphHeight -
      ((clamped - pctMin) / (pctMax - pctMin)) * graphHeight
    );
  };

  const buildPath = (getY: (p: TelemetryHistoryPoint) => number) => {
    if (points.length === 0) return "";
    return points
      .map((p, i) => `${i === 0 ? "M" : "L"} ${scaleX(i).toFixed(1)} ${getY(p).toFixed(1)}`)
      .join(" ");
  };

  const tempPath = buildPath((p) => scaleTempY(p.temp));
  const humPath = buildPath((p) => scalePctY(p.humidity));
  const soilPath = buildPath((p) => scalePctY(p.soil));

  const latest = points[points.length - 1];
  const latestTempDisplay = latest ? formatTemp(latest.temp, unit) : null;

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between">
      {/* Header & Legend */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between pb-3 border-b border-slate-800 gap-3">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <Activity className="h-4 w-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-white flex items-center gap-2">
              <span>60-Second Real-Time Microclimate Telemetry</span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                1000 ms Rate
              </span>
            </h3>
            <p className="text-xs text-slate-400">
              High-resolution canopy temperature, relative humidity, and soil moisture trend
            </p>
          </div>
        </div>

        {/* Legend toggles */}
        <div className="flex items-center gap-2 text-xs font-mono">
          <button
            onClick={() => setShowTemp(!showTemp)}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded border transition-all ${
              showTemp
                ? "bg-amber-950/40 border-amber-500/50 text-amber-300"
                : "bg-slate-800/40 border-slate-700 text-slate-500"
            }`}
          >
            <span className="h-2 w-2 rounded-full bg-amber-400" />
            <span>Temp ({latestTempDisplay ? latestTempDisplay.text : "--"})</span>
          </button>

          <button
            onClick={() => setShowHumidity(!showHumidity)}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded border transition-all ${
              showHumidity
                ? "bg-blue-950/40 border-blue-500/50 text-blue-300"
                : "bg-slate-800/40 border-slate-700 text-slate-500"
            }`}
          >
            <span className="h-2 w-2 rounded-full bg-blue-400" />
            <span>RH ({latest ? `${latest.humidity.toFixed(1)}%` : "--"})</span>
          </button>

          <button
            onClick={() => setShowSoil(!showSoil)}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded border transition-all ${
              showSoil
                ? "bg-teal-950/40 border-teal-500/50 text-teal-300"
                : "bg-slate-800/40 border-slate-700 text-slate-500"
            }`}
          >
            <span className="h-2 w-2 rounded-full bg-teal-400" />
            <span>Soil ({latest ? `${latest.soil.toFixed(1)}%` : "--"})</span>
          </button>
        </div>
      </div>

      {/* SVG Chart Graphic */}
      <div className="w-full mt-3 overflow-hidden">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full h-auto drop-shadow-md select-none"
        >
          {/* Background Grid Lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((pct, idx) => {
            const y = padding.top + pct * graphHeight;
            const tempValC = tempMax - pct * (tempMax - tempMin);
            const tempFormatted = formatTemp(tempValC, unit);
            const pctVal = (pctMax - pct * (pctMax - pctMin)).toFixed(0);
            return (
              <g key={idx}>
                <line
                  x1={padding.left}
                  y1={y}
                  x2={width - padding.right}
                  y2={y}
                  stroke="#1e293b"
                  strokeDasharray="4 4"
                  strokeWidth="1"
                />
                <text
                  x={padding.left - 8}
                  y={y + 4}
                  fill="#64748b"
                  fontSize="10"
                  textAnchor="end"
                  fontFamily="monospace"
                >
                  {tempFormatted.text}
                </text>
                <text
                  x={width - padding.right + 8}
                  y={y + 4}
                  fill="#475569"
                  fontSize="10"
                  textAnchor="start"
                  fontFamily="monospace"
                >
                  {pctVal}%
                </text>
              </g>
            );
          })}

          {/* Humidity Line (Blue) */}
          {showHumidity && (
            <path
              d={humPath}
              fill="none"
              stroke="#60a5fa"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )}

          {/* Soil Moisture Line (Teal) */}
          {showSoil && (
            <path
              d={soilPath}
              fill="none"
              stroke="#2dd4bf"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )}

          {/* Temperature Line (Amber) */}
          {showTemp && (
            <path
              d={tempPath}
              fill="none"
              stroke="#fbbf24"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )}

          {/* Latest data indicator dots */}
          {points.length > 0 && (
            <g>
              {showTemp && (
                <circle
                  cx={scaleX(points.length - 1)}
                  cy={scaleTempY(latest.temp)}
                  r="4"
                  fill="#fbbf24"
                  stroke="#0f172a"
                  strokeWidth="2"
                />
              )}
              {showHumidity && (
                <circle
                  cx={scaleX(points.length - 1)}
                  cy={scalePctY(latest.humidity)}
                  r="3.5"
                  fill="#60a5fa"
                  stroke="#0f172a"
                  strokeWidth="2"
                />
              )}
              {showSoil && (
                <circle
                  cx={scaleX(points.length - 1)}
                  cy={scalePctY(latest.soil)}
                  r="3.5"
                  fill="#2dd4bf"
                  stroke="#0f172a"
                  strokeWidth="2"
                />
              )}
            </g>
          )}

          {/* Timeline bottom labels */}
          <text
            x={padding.left}
            y={height - 8}
            fill="#475569"
            fontSize="10"
            fontFamily="monospace"
          >
            -60s ({points[0]?.time || ""})
          </text>
          <text
            x={width - padding.right}
            y={height - 8}
            fill="#475569"
            fontSize="10"
            textAnchor="end"
            fontFamily="monospace"
          >
            Now ({latest?.time || ""})
          </text>
        </svg>
      </div>
    </div>
  );
};
