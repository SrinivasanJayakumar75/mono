import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Primary action colour: warm near-black ink rather than a saturated hue.
        brand: {
          50: "#f7f6f4",
          100: "#efedea",
          200: "#dedbd5",
          300: "#c5c1b9",
          400: "#a19c92",
          500: "#7c776d",
          600: "#33302a",
          700: "#23211c",
          800: "#1a1814",
          900: "#12110e",
          950: "#0a0908",
        },
        // Restrained secondary accent (muted sage) used sparingly.
        accent: {
          50: "#f4f6f3",
          100: "#e7ece4",
          200: "#cfd9ca",
          300: "#adbea6",
          400: "#86997d",
          500: "#667a5d",
          600: "#4f6247",
          700: "#3e4d39",
          800: "#343f31",
          900: "#2c352a",
        },
        // Warm neutral surfaces.
        surface: {
          50: "#faf9f7",
          100: "#f4f3f0",
          200: "#e9e7e2",
          300: "#d7d4cd",
          400: "#a9a59c",
          500: "#7d7971",
          600: "#5e5a54",
          700: "#46433e",
          800: "#2b2926",
          900: "#1a1917",
          950: "#0e0d0b",
        },
      },
      fontFamily: {
        sans: [
          "Inter",
          "Segoe UI Variable Text",
          "Segoe UI",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
        mono: [
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "Consolas",
          "Liberation Mono",
          "monospace",
        ],
      },
      borderRadius: {
        card: "0.75rem",
        panel: "1rem",
        pill: "9999px",
      },
      boxShadow: {
        card: "0 1px 2px 0 rgb(26 24 21 / 0.03)",
        lift: "0 24px 48px -32px rgb(26 24 21 / 0.35), 0 2px 8px -6px rgb(26 24 21 / 0.10)",
        glow: "0 0 0 1px rgb(26 24 21 / 0.05), 0 10px 24px -18px rgb(26 24 21 / 0.30)",
      },
      keyframes: {
        "fade-in": {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        "fade-up": {
          "0%": { opacity: "0", transform: "translateY(6px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "bubble-in": {
          "0%": { opacity: "0", transform: "translateY(8px) scale(0.98)" },
          "100%": { opacity: "1", transform: "translateY(0) scale(1)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-500px 0" },
          "100%": { backgroundPosition: "500px 0" },
        },
        "typing-dot": {
          "0%, 60%, 100%": { opacity: "0.25", transform: "translateY(0)" },
          "30%": { opacity: "1", transform: "translateY(-3px)" },
        },
      },
      animation: {
        "fade-in": "fade-in 200ms ease-out",
        "fade-up": "fade-up 250ms ease-out",
        "bubble-in": "bubble-in 200ms ease-out",
        shimmer: "shimmer 1.6s linear infinite",
        "typing-dot": "typing-dot 1.2s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};

export default config;
