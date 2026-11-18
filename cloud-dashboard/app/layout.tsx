import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "AgriGuard Cloud Command Center | Enterprise SCADA",
  description:
    "Real-Time Multi-Zone Plantation Climate, Irrigation, and Cryptographic Telemetry Gateway",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="bg-[#080c14] text-slate-100 min-h-screen antialiased selection:bg-emerald-500 selection:text-white">
        {children}
      </body>
    </html>
  );
}
