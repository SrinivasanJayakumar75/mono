import type { Metadata, Viewport } from "next";

import { MonotronWidget } from "@/components/monotron-widget";
import { ToastProvider } from "@/components/ui";
import { AuthProvider } from "@/lib/auth";
import { BillingProvider } from "@/lib/billing";

import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "Monotron — AI customer support, in one calm inbox",
    template: "%s · Monotron",
  },
  description:
    "Monotron is an AI-powered customer support workspace: a shared inbox, AI answers grounded in your PDFs, a live chat widget and a CRM built for the conversation.",
  applicationName: "Monotron",
  openGraph: {
    title: "Monotron — AI customer support, in one calm inbox",
    description:
      "A shared inbox, grounded AI answers, a live chat widget and a CRM built for the conversation.",
    type: "website",
    siteName: "Monotron",
  },
  twitter: {
    card: "summary_large_image",
    title: "Monotron — AI customer support, in one calm inbox",
    description:
      "A shared inbox, grounded AI answers, a live chat widget and a CRM built for the conversation.",
  },
};

export const viewport: Viewport = {
  themeColor: "#12110e",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen">
        <ToastProvider>
          <AuthProvider>
            <BillingProvider>{children}</BillingProvider>
          </AuthProvider>
        </ToastProvider>

        <MonotronWidget />
      </body>
    </html>
  );
}
