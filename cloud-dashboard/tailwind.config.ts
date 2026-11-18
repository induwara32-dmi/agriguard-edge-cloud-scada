import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        scada: {
          dark: "#0b0f17",
          panel: "#121926",
          border: "#1e293b",
          subtle: "#334155",
          highlight: "#38bdf8",
        },
      },
    },
  },
  plugins: [],
};
export default config;
