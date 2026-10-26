import Link from "next/link";
import clsx from "clsx";

type MonotronLogoProps = {
  href?: string;
  className?: string;
  markClassName?: string;
  wordmarkClassName?: string;
  showWordmark?: boolean;
  tone?: "dark" | "light";
};

export function MonotronMark({ className }: { className?: string }) {
  return (
    <span
      className={clsx(
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-[0.7rem] bg-brand-900 text-surface-50",
        className,
      )}
      aria-hidden="true"
    >
      <svg viewBox="0 0 24 24" className="h-[58%] w-[58%]" fill="none">
        <path
          d="M5 18V7.2L12 14l7-6.8V18"
          stroke="currentColor"
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="19" cy="5.5" r="1.8" fill="#adbea6" />
      </svg>
    </span>
  );
}

export function MonotronLogo({
  href,
  className,
  markClassName,
  wordmarkClassName,
  showWordmark = true,
  tone = "dark",
}: MonotronLogoProps) {
  const content = (
    <>
      <MonotronMark
        className={clsx(
          tone === "light" ? "bg-surface-50 text-brand-900" : "bg-brand-900 text-surface-50",
          markClassName,
        )}
      />
      {showWordmark ? (
        <span
          className={clsx(
            "font-semibold tracking-[-0.04em]",
            tone === "light" ? "text-surface-50" : "text-surface-900",
            wordmarkClassName,
          )}
        >
          Monotron
        </span>
      ) : null}
    </>
  );

  const classes = clsx("inline-flex items-center gap-2.5", className);
  return href ? (
    <Link href={href} className={classes} aria-label={showWordmark ? undefined : "Monotron"}>
      {content}
    </Link>
  ) : (
    <span className={classes} aria-label={showWordmark ? undefined : "Monotron"}>
      {content}
    </span>
  );
}
