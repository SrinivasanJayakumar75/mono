
"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";

export function MonotronWidget() {
  const pathname = usePathname();

  useEffect(() => {
    if (pathname !== "/") return;

    const iframe = document.createElement("iframe");

    iframe.src =
      "http://localhost:3000/widget/75699813803d432fbd98d162219b9b6c";

    iframe.title = "Monotron chat";

    iframe.style.cssText =
      "position:fixed;bottom:0;right:0;width:420px;height:680px;max-width:100vw;border:0;z-index:2147483000;background:transparent";

    document.body.appendChild(iframe);

    return () => {
      iframe.remove();
    };
  }, [pathname]);

  return null;
}
