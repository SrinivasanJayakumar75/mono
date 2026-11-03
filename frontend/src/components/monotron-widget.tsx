"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";

export function MonotronWidget() {
  const pathname = usePathname();

  useEffect(() => {
    if (pathname !== "/") return;

    const iframe = document.createElement("iframe");

    iframe.src =
      "https://www.monotron.in/widget/8b0338dd0f6ad1a7aa7bdc1ffedd8e66";

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
