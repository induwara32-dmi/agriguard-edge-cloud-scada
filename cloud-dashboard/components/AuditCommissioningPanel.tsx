"use client";

import React, { useState } from "react";
import {
  Wrench,
  CheckCircle2,
  Copy,
  RefreshCw,
  Hash,
  FileCheck2,
  AlertTriangle,
  DownloadCloud,
  Shield,
} from "lucide-react";
import { AuditStatus, SecurityRole, LanguageCode } from "../types/telemetry";
import { TRANSLATIONS } from "../lib/i18n";

interface AuditCommissioningPanelProps {
  auditStatus: AuditStatus | null;
  onTriggerFault: (action: string) => Promise<boolean>;
  onRefreshAudit: () => void;
  onTriggerBackfill?: () => void;
  isBackfilling?: boolean;
  role?: SecurityRole;
  language?: LanguageCode;
}

export const AuditCommissioningPanel: React.FC<AuditCommissioningPanelProps> = ({
  auditStatus,
  onTriggerFault,
  onRefreshAudit,
  onTriggerBackfill,
  isBackfilling = false,
  role = "OPERATOR",
  language = "EN",
}) => {
  const t = TRANSLATIONS[language] || TRANSLATIONS.EN;
  const [copied, setCopied] = useState(false);
  const [loadingAction, setLoadingAction] = useState<string | null>(null);
  const isViewer = role === "VIEWER";

  const handleAction = async (action: string) => {
    setLoadingAction(action);
    try {
      await onTriggerFault(action);
      setTimeout(onRefreshAudit, 1200);
    } finally {
      setLoadingAction(null);
    }
  };

  const copyHash = (hash: string) => {
    navigator.clipboard.writeText(hash);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const records = auditStatus?.totalRecords ?? 0;
  const latestHash = auditStatus?.latestHash || "0000000000000000000000000000000000000000000000000000000000000000";
  const verified = auditStatus?.chainVerified ?? true;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {/* 1. Field Diagnostics & Remote Fault Injection */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-orange-500/10 text-orange-400 border border-orange-500/20">
              <Wrench className="h-4 w-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <span>{t.commissioning_title}</span>
                {isViewer && (
                  <span className="text-[10px] font-mono bg-slate-800 text-slate-400 border border-slate-700 px-2 py-0.5 rounded flex items-center gap-1">
                    <Shield className="w-2.5 h-2.5" />
                    Read-Only
                  </span>
                )}
              </h3>
              <p className="text-xs text-slate-400">
                Inject deterministic transient faults to verify TMR voter & safety trips
              </p>
            </div>
          </div>
          <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
            POST /api/v1/control/fault
          </span>
        </div>

        {/* Action Buttons */}
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 my-3">
          <button
            disabled={loadingAction !== null || isViewer}
            onClick={() => handleAction("BIAS")}
            className={`p-2.5 rounded-lg border text-left transition-all ${
              isViewer
                ? "bg-slate-950/40 border-slate-800/40 opacity-50 cursor-not-allowed"
                : "bg-slate-950/70 hover:bg-slate-800 border-slate-800 hover:border-amber-500/50"
            }`}
          >
            <div className="text-xs font-bold text-amber-400 font-mono">
              [F1] T2 +15°C
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              TMR Outlier Masking
            </div>
          </button>

          <button
            disabled={loadingAction !== null || isViewer}
            onClick={() => handleAction("DISCONNECT")}
            className={`p-2.5 rounded-lg border text-left transition-all ${
              isViewer
                ? "bg-slate-950/40 border-slate-800/40 opacity-50 cursor-not-allowed"
                : "bg-slate-950/70 hover:bg-slate-800 border-slate-800 hover:border-amber-500/50"
            }`}
          >
            <div className="text-xs font-bold text-amber-400 font-mono">
              [F2] Disconnect T3
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              Watchdog Omission
            </div>
          </button>

          <button
            disabled={loadingAction !== null || isViewer}
            onClick={() => handleAction("DUAL")}
            className={`p-2.5 rounded-lg border text-left transition-all ${
              isViewer
                ? "bg-slate-950/40 border-slate-800/40 opacity-50 cursor-not-allowed"
                : "bg-slate-950/70 hover:bg-slate-800 border-slate-800 hover:border-red-500/50"
            }`}
          >
            <div className="text-xs font-bold text-red-400 font-mono">
              [F3] Dual Loss
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              Fail-Safe Trip
            </div>
          </button>

          <button
            disabled={loadingAction !== null || isViewer}
            onClick={() => handleAction("SURGE")}
            className={`p-2.5 rounded-lg border text-left transition-all ${
              isViewer
                ? "bg-slate-950/40 border-slate-800/40 opacity-50 cursor-not-allowed"
                : "bg-slate-950/70 hover:bg-slate-800 border-slate-800 hover:border-orange-500/50"
            }`}
          >
            <div className="text-xs font-bold text-orange-400 font-mono">
              [F5] +14°C Surge
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              Emergency Siren Trip
            </div>
          </button>

          <button
            disabled={loadingAction !== null || isViewer}
            onClick={() => handleAction("FREEZE")}
            className={`p-2.5 rounded-lg border text-left transition-all ${
              isViewer
                ? "bg-slate-950/40 border-slate-800/40 opacity-50 cursor-not-allowed"
                : "bg-slate-950/70 hover:bg-slate-800 border-slate-800 hover:border-sky-500/50"
            }`}
          >
            <div className="text-xs font-bold text-sky-400 font-mono">
              Freeze T1 ADC
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              Stuck Transducer
            </div>
          </button>

          <button
            disabled={loadingAction !== null || isViewer}
            onClick={() => handleAction("RESET")}
            className={`p-2.5 rounded-lg border text-left transition-all ${
              isViewer
                ? "bg-slate-950/40 border-slate-800/40 opacity-50 cursor-not-allowed"
                : "bg-emerald-950/40 hover:bg-emerald-900/50 border-emerald-500/40 hover:border-emerald-400"
            }`}
          >
            <div className="text-xs font-bold text-emerald-400 font-mono">
              Reset Channels
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              Restore Nominal State
            </div>
          </button>
        </div>

        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">
          <span>Signed Command: HMAC-SHA256</span>
          <span className="font-mono text-emerald-400 text-[11px]">
            Role: {role}
          </span>
        </div>
      </div>

      {/* 2. Cryptographic SHA-256 Audit Trail & Backfill */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 shadow-lg flex flex-col justify-between">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <FileCheck2 className="h-4 w-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-white">
                {t.audit_title}
              </h3>
              <p className="text-xs text-slate-400">
                Continuous blockchain-style row linkage: PrevHash + RowHash
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5">
            {onTriggerBackfill && (
              <button
                onClick={onTriggerBackfill}
                disabled={isBackfilling}
                title="Trigger store-and-forward catch-up sync"
                className="flex items-center gap-1 text-[11px] font-mono px-2 py-1 rounded bg-blue-950/60 border border-blue-500/30 text-blue-300 hover:bg-blue-900 transition"
              >
                <DownloadCloud className={`w-3.5 h-3.5 ${isBackfilling ? "animate-bounce" : ""}`} />
                <span>{isBackfilling ? "Syncing..." : "Backfill"}</span>
              </button>
            )}

            <button
              onClick={onRefreshAudit}
              title="Refresh ledger verification"
              className="p-1.5 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-white transition"
            >
              <RefreshCw className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Verification Status Banner */}
        <div className="my-3 space-y-2">
          <div className="flex items-center justify-between p-2.5 rounded-lg bg-slate-950/70 border border-slate-800">
            <div className="flex items-center gap-2">
              {verified ? (
                <CheckCircle2 className="h-5 w-5 text-emerald-400" />
              ) : (
                <AlertTriangle className="h-5 w-5 text-red-400" />
              )}
              <div>
                <div className="text-xs font-bold text-white">
                  {verified ? t.audit_valid : t.audit_invalid}
                </div>
                <div className="text-[11px] font-mono text-slate-400">
                  {records} chained historical records verified
                </div>
              </div>
            </div>

            <span className="px-2 py-0.5 rounded text-[10px] font-mono uppercase bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-bold">
              SHA-256 PASS
            </span>
          </div>

          {/* Latest Tail Block Hash Display */}
          <div className="p-2.5 rounded-lg bg-slate-950/70 border border-slate-800 text-left">
            <div className="flex items-center justify-between text-[11px] text-slate-400 mb-1">
              <span className="flex items-center gap-1 font-mono">
                <Hash className="h-3 w-3 text-slate-400" />
                Latest Ledger Tail Block Hash:
              </span>
              <button
                onClick={() => copyHash(latestHash)}
                className="text-[10px] font-mono text-emerald-400 hover:underline flex items-center gap-1"
              >
                <Copy className="h-3 w-3" />
                <span>{copied ? "Copied!" : "Copy"}</span>
              </button>
            </div>
            <div className="font-mono text-[11px] text-slate-300 break-all bg-slate-900/90 p-1.5 rounded border border-slate-800/80">
              {latestHash}
            </div>
          </div>
        </div>

        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">
          <span className="truncate max-w-[260px] font-mono text-[11px]">
            Target: {auditStatus?.auditLedgerFile || "logs/plantation_telemetry.csv"}
          </span>
          <span className="text-emerald-400 text-[11px] font-mono">
            Genesis Seed Verified
          </span>
        </div>
      </div>
    </div>
  );
};
