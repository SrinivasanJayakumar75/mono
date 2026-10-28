"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";

/**
 * Demo widget embedded on the Monotron marketing page.
 *
 * The production workspace id is the fallback. Local development can point at
 * a workspace in the local database with NEXT_PUBLIC_WIDGET_APP_ID in
 * frontend/.env.local. The widget page itself is excluded so the iframe never
 * embeds another copy of itself.
 */
const DEFAULT_APP_ID = "de8003c7921a4aadc7391c9560bb63b6";

export function MonotronWidget() {
  const pathname = usePathname();
  const appId = process.env.NEXT_PUBLIC_WIDGET_APP_ID ?? DEFAULT_APP_ID;

  useEffect(() => {
    if (pathname !== "/") return;

    const iframe = document.createElement("iframe");
    iframe.src = `${window.location.origin}/widget/${appId}`;
    iframe.title = "Monotron chat";
    iframe.style.cssText =
      "position:fixed;bottom:0;right:0;width:420px;height:680px;max-width:100vw;border:0;z-index:2147483000;background:transparent";
    document.body.appendChild(iframe);

    return () => {
      iframe.remove();
    };
  }, [appId, pathname]);

  return null;
}
