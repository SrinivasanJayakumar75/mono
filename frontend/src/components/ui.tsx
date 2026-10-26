"use client";

import clsx from "clsx";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";

import { AlertIcon, CheckCircleIcon, CloseIcon, InfoIcon } from "./icons";

/* --------------------------------- Button --------------------------------- */

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "outline";
type ButtonSize = "sm" | "md" | "lg" | "icon";

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary:
    "bg-brand-900 text-white hover:bg-brand-800 focus-visible:outline-brand-900 disabled:bg-brand-300",
  secondary:
    "bg-surface-100 text-surface-800 hover:bg-surface-200 focus-visible:outline-surface-500 disabled:text-surface-400",
  ghost:
    "bg-transparent text-surface-600 hover:bg-surface-100 hover:text-surface-900 focus-visible:outline-surface-400",
  danger:
    "bg-rose-600 text-white hover:bg-rose-700 focus-visible:outline-rose-600 disabled:bg-rose-300",
  outline:
    "border border-surface-300 bg-white text-surface-800 hover:border-surface-400 hover:bg-surface-50 focus-visible:outline-brand-500 disabled:text-surface-400",
};

const BUTTON_SIZES: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-[12.5px] gap-1.5",
  md: "h-9 px-3.5 text-[13.5px] gap-2",
  lg: "h-10 px-5 text-sm gap-2",
  icon: "h-8 w-8 p-0",
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: ReactNode;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  icon,
  className,
  children,
  disabled,
  type = "button",
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={clsx(
        "inline-flex select-none items-center justify-center rounded-md font-medium tracking-[-0.01em] transition-colors",
        "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2",
        "disabled:cursor-not-allowed disabled:opacity-70",
        BUTTON_VARIANTS[variant],
        BUTTON_SIZES[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Spinner className="h-4 w-4" /> : icon}
      {children}
    </button>
  );
}

/* --------------------------------- Inputs --------------------------------- */

export interface FieldProps {
  label?: string;
  error?: string | null;
  hint?: string;
  required?: boolean;
  className?: string;
}

const FIELD_BASE =
  "w-full rounded-md border bg-white px-3 py-2 text-[13.5px] text-surface-900 shadow-[0_1px_1px_rgb(26_24_21_/_0.02)] placeholder:text-surface-400 transition-colors focus:outline-none focus:ring-[3px] disabled:cursor-not-allowed disabled:bg-surface-50";

function fieldClasses(hasError: boolean): string {
  return clsx(
    FIELD_BASE,
    hasError
      ? "border-rose-400 focus:border-rose-500 focus:ring-rose-500/15"
      : "border-surface-300 focus:border-surface-500 focus:ring-brand-900/[0.07]",
  );
}

function Field({
  label,
  error,
  hint,
  required,
  className,
  htmlFor,
  children,
}: FieldProps & { htmlFor?: string; children: ReactNode }) {
  return (
    <div className={clsx("space-y-1.5", className)}>
      {label ? (
        <label htmlFor={htmlFor} className="block text-[12.5px] font-medium text-surface-600">
          {label}
          {required ? <span className="ml-0.5 text-rose-500">*</span> : null}
        </label>
      ) : null}
      {children}
      {error ? (
        <p className="text-xs font-medium text-rose-600" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs text-surface-500">{hint}</p>
      ) : null}
    </div>
  );
}

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "size">, FieldProps {
  size?: "sm" | "md";
}

export const Input = ({
  label,
  error,
  hint,
  required,
  className,
  size = "md",
  id,
  ...rest
}: InputProps) => {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  return (
    <Field
      label={label}
      error={error}
      hint={hint}
      required={required}
      className={className}
      htmlFor={inputId}
    >
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        className={clsx(fieldClasses(Boolean(error)), size === "sm" && "h-9 py-1.5 text-[13px]")}
        {...rest}
      />
    </Field>
  );
};

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement>, FieldProps {}

export const Textarea = ({
  label,
  error,
  hint,
  required,
  className,
  id,
  rows = 4,
  ...rest
}: TextareaProps) => {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  return (
    <Field
      label={label}
      error={error}
      hint={hint}
      required={required}
      className={className}
      htmlFor={inputId}
    >
      <textarea
        id={inputId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        className={clsx(fieldClasses(Boolean(error)), "resize-y leading-relaxed")}
        {...rest}
      />
    </Field>
  );
};

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement>, FieldProps {
  options?: { value: string; label: string }[];
  placeholder?: string;
}

export const Select = ({
  label,
  error,
  hint,
  required,
  className,
  id,
  options,
  placeholder,
  children,
  ...rest
}: SelectProps) => {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  return (
    <Field
      label={label}
      error={error}
      hint={hint}
      required={required}
      className={className}
      htmlFor={inputId}
    >
      <select
        id={inputId}
        aria-invalid={error ? true : undefined}
        className={clsx(fieldClasses(Boolean(error)), "cursor-pointer pr-8")}
        {...rest}
      >
        {placeholder ? <option value="">{placeholder}</option> : null}
        {options?.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
        {children}
      </select>
    </Field>
  );
};

/* ---------------------------------- Card ---------------------------------- */

export interface CardProps {
  title?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
  bodyClassName?: string;
  children?: ReactNode;
  padded?: boolean;
}

export function Card({
  title,
  description,
  action,
  className,
  bodyClassName,
  children,
  padded = true,
}: CardProps) {
  return (
    <section
      className={clsx(
        "rounded-card border border-surface-200 bg-white shadow-card",
        className,
      )}
    >
      {title || description || action ? (
        <header className="flex items-start justify-between gap-4 border-b border-surface-100 px-5 py-3.5">
          <div className="min-w-0">
            {title ? (
              <h2 className="text-[13px] font-semibold tracking-[-0.005em] text-surface-900">
                {title}
              </h2>
            ) : null}
            {description ? (
              <p className="mt-0.5 text-xs text-surface-500">{description}</p>
            ) : null}
          </div>
          {action ? <div className="shrink-0">{action}</div> : null}
        </header>
      ) : null}
      <div className={clsx(padded && "p-5", bodyClassName)}>{children}</div>
    </section>
  );
}

/* --------------------------------- Badge ---------------------------------- */

type BadgeVariant = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

const BADGE_VARIANTS: Record<BadgeVariant, string> = {
  neutral: "bg-surface-100 text-surface-600 ring-surface-200",
  brand: "bg-brand-50 text-brand-700 ring-brand-200",
  success: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  warning: "bg-amber-50 text-amber-700 ring-amber-200",
  danger: "bg-rose-50 text-rose-700 ring-rose-200",
  info: "bg-sky-50 text-sky-700 ring-sky-200",
};

export function Badge({
  variant = "neutral",
  className,
  children,
}: {
  variant?: BadgeVariant;
  className?: string;
  children: ReactNode;
}) {
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10.5px] font-medium tracking-wide ring-1 ring-inset",
        BADGE_VARIANTS[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}

/* -------------------------------- Spinner --------------------------------- */

export function Spinner({ className }: { className?: string }) {
  return (
    <svg
      className={clsx("animate-spin", className ?? "h-5 w-5")}
      viewBox="0 0 24 24"
      fill="none"
      role="status"
      aria-label="Loading"
    >
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path
        d="M21 12a9 9 0 0 0-9-9"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  );
}

/* -------------------------------- Skeleton -------------------------------- */

export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={clsx(
        "animate-shimmer rounded-md bg-surface-200 bg-gradient-to-r from-surface-100 via-surface-200 to-surface-100 bg-[length:1000px_100%]",
        className,
      )}
    />
  );
}

/* ------------------------------- EmptyState ------------------------------- */

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={clsx(
        "flex flex-col items-center justify-center gap-3 rounded-card border border-dashed border-surface-300 bg-white px-6 py-14 text-center",
        className,
      )}
    >
      {icon ? (
        <div className="flex h-10 w-10 items-center justify-center rounded-md border border-surface-200 bg-surface-50 text-lg text-surface-500">
          {icon}
        </div>
      ) : null}
      <div className="space-y-1">
        <p className="text-sm font-medium text-surface-800">{title}</p>
        {description ? (
          <p className="mx-auto max-w-sm text-sm text-surface-500">{description}</p>
        ) : null}
      </div>
      {action}
    </div>
  );
}

/* --------------------------------- Modal ---------------------------------- */

export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = "md",
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children?: ReactNode;
  footer?: ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
}) {
  const titleId = useId();

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = previous;
    };
  }, [open, onClose]);

  if (!open) return null;

  const widths = {
    sm: "max-w-sm",
    md: "max-w-lg",
    lg: "max-w-2xl",
    xl: "max-w-4xl",
  } as const;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-6">
      <div
        className="absolute inset-0 bg-surface-950/40 backdrop-blur-[2px] animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={clsx(
          "relative z-10 max-h-[92vh] w-full overflow-y-auto rounded-t-2xl border border-surface-200 bg-white shadow-lift animate-fade-up sm:rounded-xl",
          widths[size],
        )}
      >
        <header className="flex items-start justify-between gap-4 border-b border-surface-100 px-5 py-4">
          <div>
            <h2 id={titleId} className="text-base font-semibold text-surface-900">
              {title}
            </h2>
            {description ? (
              <p className="mt-0.5 text-sm text-surface-500">{description}</p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="rounded-lg p-1.5 text-surface-400 transition-colors hover:bg-surface-100 hover:text-surface-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
          >
            <CloseIcon className="h-5 w-5" />
          </button>
        </header>
        <div className="px-5 py-4">{children}</div>
        {footer ? (
          <footer className="flex flex-wrap items-center justify-end gap-2 border-t border-surface-100 bg-surface-50/60 px-5 py-4">
            {footer}
          </footer>
        ) : null}
      </div>
    </div>
  );
}

/* --------------------------------- Toast ---------------------------------- */

export type ToastTone = "info" | "success" | "error";

interface ToastItem {
  id: number;
  title: string;
  description?: string;
  tone: ToastTone;
}

interface ToastContextValue {
  toast: (title: string, options?: { description?: string; tone?: ToastTone }) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const TOAST_STYLES: Record<ToastTone, { wrap: string; icon: ReactNode }> = {
  info: {
    wrap: "border-surface-200 bg-white text-surface-900",
    icon: <InfoIcon className="h-4 w-4 text-sky-600" />,
  },
  success: {
    wrap: "border-surface-200 bg-white text-surface-900",
    icon: <CheckCircleIcon className="h-4 w-4 text-emerald-600" />,
  },
  error: {
    wrap: "border-surface-200 bg-white text-surface-900",
    icon: <AlertIcon className="h-4 w-4 text-rose-600" />,
  },
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const counter = useRef(0);

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((item) => item.id !== id));
  }, []);

  const toast = useCallback(
    (title: string, options?: { description?: string; tone?: ToastTone }) => {
      counter.current += 1;
      const id = counter.current;
      const item: ToastItem = {
        id,
        title,
        description: options?.description,
        tone: options?.tone ?? "info",
      };
      setItems((current) => [...current, item]);
      window.setTimeout(() => dismiss(id), 5000);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ toast }), [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <Toaster items={items} onDismiss={dismiss} />
    </ToastContext.Provider>
  );
}

export function Toaster({
  items,
  onDismiss,
}: {
  items: ToastItem[];
  onDismiss: (id: number) => void;
}) {
  return (
    <div
      className="pointer-events-none fixed inset-x-0 bottom-0 z-[100] flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-4 sm:items-end"
      role="region"
      aria-live="polite"
      aria-label="Notifications"
    >
      {items.map((item) => (
        <div
          key={item.id}
          className={clsx(
            "pointer-events-auto flex w-full max-w-sm animate-fade-up items-start gap-3 rounded-lg border px-4 py-3 shadow-lift",
            TOAST_STYLES[item.tone].wrap,
          )}
        >
          <span className="mt-0.5 shrink-0">{TOAST_STYLES[item.tone].icon}</span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold">{item.title}</p>
            {item.description ? (
              <p className="mt-0.5 text-xs opacity-80">{item.description}</p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={() => onDismiss(item.id)}
            aria-label="Dismiss notification"
            className="shrink-0 rounded-md p-1 opacity-60 transition-opacity hover:opacity-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current"
          >
            <CloseIcon className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used inside <ToastProvider>");
  }
  return ctx;
}

/* ------------------------------ ErrorBanner ------------------------------- */

export function ErrorBanner({
  message,
  onRetry,
  className,
}: {
  message: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={clsx(
        "flex items-start gap-3 rounded-md border border-rose-200 bg-rose-50/70 px-3.5 py-2.5 text-[13px] text-rose-800",
        className,
      )}
    >
      <AlertIcon className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
      <p className="min-w-0 flex-1">{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="shrink-0 rounded-md px-2 py-1 text-xs font-semibold text-rose-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-500"
        >
          Retry
        </button>
      ) : null}
    </div>
  );
}

/* ------------------------------- ToggleSwitch ----------------------------- */

export function ToggleSwitch({
  checked,
  onChange,
  label,
  description,
  id,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
  description?: string;
  id?: string;
}) {
  const generatedId = useId();
  const switchId = id ?? generatedId;
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <label
          htmlFor={switchId}
          className="block text-sm font-medium text-surface-800"
        >
          {label}
        </label>
        {description ? (
          <p className="mt-0.5 text-xs text-surface-500">{description}</p>
        ) : null}
      </div>
      <button
        id={switchId}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className={clsx(
          "relative mt-0.5 h-6 w-11 shrink-0 rounded-full transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500",
          checked ? "bg-brand-900" : "bg-surface-300",
        )}
      >
        <span
          className={clsx(
            "absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform",
            checked ? "translate-x-[1.375rem]" : "translate-x-0.5",
          )}
        />
      </button>
    </div>
  );
}
