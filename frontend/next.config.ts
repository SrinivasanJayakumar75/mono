import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Keep tracing scoped to this app even when a parent folder has its own lockfile.
  outputFileTracingRoot: process.cwd(),
};

export default nextConfig;
