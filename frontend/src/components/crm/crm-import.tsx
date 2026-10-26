"use client";

import clsx from "clsx";
import { useRef, useState } from "react";

import {
  CheckCircleIcon,
  DownloadIcon,
  FileIcon,
  InfoIcon,
  UploadIcon,
} from "@/components/icons";
import { Button, ErrorBanner, Modal, useToast } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api";
import type { CrmFieldConfig, CrmKey, CrmResourceConfig } from "@/lib/crm";
import {
  cleanImportedCell,
  downloadCsv,
  normalizeHeader,
  parseCsv,
  toCsv,
} from "@/lib/csv";

import type { RelationOptions } from "./crm-resource";

const MAX_FILE_BYTES = 2 * 1024 * 1024;
const MAX_ROWS = 2_000;
const PREVIEW_ROWS = 5;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Friendly aliases for spreadsheets that use a shorter column name than the
 * form label. Everything else is matched by field key or label.
 */
const HEADER_ALIASES: Partial<Record<CrmKey, Record<string, string>>> = {
  leads: { company: "companyText", lead: "name" },
  deals: { deal: "title", amount: "valueAmount" },
  companies: { company: "name", website: "domain" },
  contacts: { company: "companyId" },
  tasks: { task: "title" },
  calls: { call: "subject" },
  meetings: { meeting: "title" },
  products: { product: "name" },
  orders: { order: "orderNumber" },
  documents: { document: "name", link: "url" },
};

interface MappedColumn {
  header: string;
  index: number;
  field: CrmFieldConfig;
}

interface ParsedImport {
  fileName: string;
  rowCount: number;
  columns: MappedColumn[];
  ignored: string[];
  rows: Record<string, string>[];
  rowNumbers: number[];
  missingRequired: string[];
  preview: Record<string, string>[];
}

interface ImportError {
  row: number;
  message: string;
}

interface ImportResult {
  imported: number;
  total: number;
  errors: ImportError[];
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return error instanceof Error ? error.message : "Something went wrong";
}

function fieldForHeader(
  config: CrmResourceConfig,
  header: string,
): CrmFieldConfig | undefined {
  const normalized = normalizeHeader(header);
  if (!normalized) return undefined;

  const alias = HEADER_ALIASES[config.key]?.[normalized];
  if (alias) {
    const aliased = config.fields.find((field) => field.key === alias);
    if (aliased) return aliased;
  }

  return config.fields.find(
    (field) =>
      normalizeHeader(field.key) === normalized ||
      normalizeHeader(field.label) === normalized,
  );
}

function mapValue(
  field: CrmFieldConfig,
  raw: string,
  relations: RelationOptions,
): string {
  let value = cleanImportedCell(raw.trim());
  if (!value) return "";

  if (field.type === "select" && field.options) {
    const normalized = normalizeHeader(value);
    const option = field.options.find(
      (candidate) =>
        normalizeHeader(candidate.value) === normalized ||
        normalizeHeader(candidate.label) === normalized,
    );
    if (option) return option.value;
  }

  if (field.type === "checkbox") {
    const normalized = normalizeHeader(value);
    if (["true", "yes", "y", "1", "active"].includes(normalized)) return "true";
    if (["false", "no", "n", "0", "inactive"].includes(normalized)) return "false";
    return value;
  }

  if (field.type === "relation" && field.relation) {
    if (UUID_PATTERN.test(value)) return value;
    const normalized = normalizeHeader(value);
    const matches = (relations[field.relation] ?? []).filter(
      (option) => normalizeHeader(option.label) === normalized,
    );
    if (matches.length === 1) return matches[0].value;
    return value;
  }

  return value;
}

async function readFile(
  file: File,
  config: CrmResourceConfig,
  relations: RelationOptions,
): Promise<ParsedImport> {
  if (!file.name.toLowerCase().endsWith(".csv")) {
    throw new Error("Choose a .csv file.");
  }
  if (file.size > MAX_FILE_BYTES) {
    throw new Error("The file is larger than 2 MB.");
  }

  const table = parseCsv(await file.text());
  if (table.length === 0) {
    throw new Error("The file is empty.");
  }

  const headerRow = table[0].map((header) => header.trim());
  if (headerRow.every((header) => header === "")) {
    throw new Error("The first row must contain column names.");
  }

  const columns: MappedColumn[] = [];
  const ignored: string[] = [];
  const seen = new Map<string, string>();

  headerRow.forEach((header, index) => {
    const field = fieldForHeader(config, header);
    if (!field) {
      if (header) ignored.push(header);
      return;
    }
    const previous = seen.get(field.key);
    if (previous) {
      throw new Error(
        `Columns “${previous}” and “${header}” both map to ${field.label}.`,
      );
    }
    seen.set(field.key, header);
    columns.push({ header, index, field });
  });

  if (columns.length === 0) {
    throw new Error(
      "None of the columns match this module. Download the template to see the expected names.",
    );
  }

  const mappedKeys = new Set(columns.map((column) => column.field.key));
  const missingRequired = config.fields
    .filter((field) => field.required && !mappedKeys.has(field.key))
    .map((field) => field.label);

  const rows: Record<string, string>[] = [];
  const rowNumbers: number[] = [];

  for (let index = 1; index < table.length; index += 1) {
    const cells = table[index];
    if (cells.every((cell) => (cell ?? "").trim() === "")) continue;

    const record: Record<string, string> = {};
    for (const column of columns) {
      record[column.field.key] = mapValue(
        column.field,
        cells[column.index] ?? "",
        relations,
      );
    }
    rows.push(record);
    rowNumbers.push(index + 1);
  }

  if (rows.length === 0) {
    throw new Error("The file has no data rows.");
  }
  if (rows.length > MAX_ROWS) {
    throw new Error(
      `Imports are limited to ${MAX_ROWS.toLocaleString()} rows at a time.`,
    );
  }

  return {
    fileName: file.name,
    rowCount: rows.length,
    columns,
    ignored,
    rows,
    rowNumbers,
    missingRequired,
    preview: rows.slice(0, PREVIEW_ROWS),
  };
}

export function CrmImportModal({
  config,
  relations,
  onClose,
  onImported,
}: {
  config: CrmResourceConfig;
  relations: RelationOptions;
  onClose: () => void;
  onImported: () => void;
}) {
  const { toast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [parsed, setParsed] = useState<ParsedImport | null>(null);
  const [parseError, setParseError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [importing, setImporting] = useState(false);
  const [dragging, setDragging] = useState(false);

  const canImport =
    Boolean(parsed) &&
    parsed !== null &&
    parsed.rows.length > 0 &&
    parsed.missingRequired.length === 0 &&
    !importing;

  function reset() {
    setFile(null);
    setParsed(null);
    setParseError(null);
    setApiError(null);
    setResult(null);
    setDragging(false);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  async function handleFile(next: File | null | undefined) {
    if (!next) return;
    setFile(next);
    setParsed(null);
    setParseError(null);
    setApiError(null);
    setResult(null);
    try {
      setParsed(await readFile(next, config, relations));
    } catch (problem) {
      setParseError(errorMessage(problem));
    }
  }

  async function submit() {
    if (!parsed || parsed.missingRequired.length > 0 || parsed.rows.length === 0) {
      return;
    }
    setImporting(true);
    setApiError(null);
    try {
      const data = await apiFetch<ImportResult>(`/crm/${config.key}/import`, {
        method: "POST",
        json: { rows: parsed.rows },
      });
      setResult(data);
      if (data.imported > 0) {
        toast(
          `Imported ${data.imported} ${
            data.imported === 1
              ? config.singular.toLowerCase()
              : config.label.toLowerCase()
          }`,
          { tone: "success" },
        );
        onImported();
      }
    } catch (problem) {
      setApiError(errorMessage(problem));
    } finally {
      setImporting(false);
    }
  }

  function downloadTemplate() {
    downloadCsv(
      `${config.key}-template.csv`,
      toCsv([config.fields.map((field) => field.label)]),
    );
  }

  function displayRow(error: ImportError): number {
    const index = error.row - 2;
    return parsed?.rowNumbers[index] ?? error.row;
  }

  const footer = result ? (
    result.errors.length === 0 ? (
      <Button onClick={onClose}>Done</Button>
    ) : (
      <>
        <Button variant="ghost" onClick={reset} disabled={importing}>
          Try another file
        </Button>
        <Button onClick={onClose}>Close</Button>
      </>
    )
  ) : (
    <>
      <Button variant="ghost" onClick={onClose} disabled={importing}>
        Cancel
      </Button>
      <Button onClick={() => void submit()} loading={importing} disabled={!canImport}>
        {parsed
          ? `Import ${parsed.rowCount} ${
              parsed.rowCount === 1
                ? config.singular.toLowerCase()
                : config.label.toLowerCase()
            }`
          : `Import ${config.label.toLowerCase()}`}
      </Button>
    </>
  );

  return (
    <Modal
      open
      onClose={onClose}
      title={`Import ${config.label.toLowerCase()}`}
      description={`Bring ${config.label.toLowerCase()} in from a spreadsheet.`}
      size="xl"
      footer={footer}
    >
      {result ? (
        result.errors.length === 0 ? (
          <div className="rounded-card border border-emerald-200 bg-emerald-50/60 px-6 py-8 text-center">
            <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full border border-emerald-200 bg-white text-emerald-600">
              <CheckCircleIcon className="h-5 w-5" />
            </div>
            <h3 className="mt-3 text-sm font-semibold text-emerald-900">
              Import complete
            </h3>
            <p className="mt-1 text-[13px] text-emerald-800">
              {result.imported}{" "}
              {result.imported === 1
                ? config.singular.toLowerCase()
                : config.label.toLowerCase()}{" "}
              added successfully.
            </p>
            <p className="mt-0.5 text-xs text-emerald-700/80">
              All {result.total} {result.total === 1 ? "row" : "rows"} passed validation.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            <ErrorBanner
              message={`Nothing was imported. ${result.errors.length} of ${
                result.total
              } ${result.total === 1 ? "row needs" : "rows need"} attention.`}
            />
            <div className="overflow-hidden rounded-md border border-surface-200">
              <div className="max-h-64 overflow-y-auto">
                <table className="w-full text-left text-[12px]">
                  <thead className="sticky top-0 bg-surface-50">
                    <tr className="border-b border-surface-200">
                      <th className="w-20 px-3 py-2 font-medium text-surface-500">
                        Row
                      </th>
                      <th className="px-3 py-2 font-medium text-surface-500">
                        Issue
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.errors.slice(0, 100).map((problem) => (
                      <tr
                        key={`${problem.row}-${problem.message}`}
                        className="border-b border-surface-100 last:border-b-0"
                      >
                        <td className="px-3 py-2 tabular-nums text-surface-500">
                          {displayRow(problem)}
                        </td>
                        <td className="px-3 py-2 text-surface-700">
                          {problem.message}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            {result.errors.length > 100 ? (
              <p className="text-xs text-surface-500">
                And {result.errors.length - 100} more{" "}
                {result.errors.length - 100 === 1 ? "issue" : "issues"}.
              </p>
            ) : null}
            <p className="text-xs text-surface-500">
              Fix the rows in your file and import it again. Your existing data was
              left untouched.
            </p>
          </div>
        )
      ) : (
        <div className="space-y-4">
          <div className="rounded-md border border-surface-200 bg-surface-50/60 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-surface-200 bg-white text-surface-500">
                  <UploadIcon className="h-4 w-4" />
                </div>
                <div>
                  <p className="text-[13px] font-semibold text-surface-900">
                    Upload a CSV
                  </p>
                  <p className="mt-0.5 text-xs text-surface-500">
                    One row per {config.singular.toLowerCase()}. Up to 2 MB and
                    2,000 rows.
                  </p>
                </div>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={downloadTemplate}
                icon={<DownloadIcon className="h-3.5 w-3.5" />}
              >
                Template
              </Button>
            </div>

            <label
              onDragOver={(event) => {
                event.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={(event) => {
                event.preventDefault();
                setDragging(false);
                void handleFile(event.dataTransfer.files?.[0]);
              }}
              className={clsx(
                "mt-3 flex cursor-pointer flex-col items-center justify-center rounded-md border border-dashed px-4 py-5 text-center transition-colors",
                dragging
                  ? "border-brand-400 bg-brand-50/50"
                  : "border-surface-300 bg-white hover:border-surface-400 hover:bg-surface-50/60",
              )}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".csv,text/csv"
                className="sr-only"
                onChange={(event) => {
                  const next = event.target.files?.[0];
                  event.target.value = "";
                  void handleFile(next);
                }}
              />
              <FileIcon className="h-5 w-5 text-surface-400" />
              <span className="mt-1.5 text-[13px] font-medium text-surface-700">
                {file ? file.name : "Choose a CSV file"}
              </span>
              <span className="mt-0.5 text-xs text-surface-400">
                {file
                  ? "Click or drop another file to replace it."
                  : "or drag and drop it here"}
              </span>
            </label>
          </div>

          <div className="flex items-start gap-2 text-xs text-surface-500">
            <InfoIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <p>
              Use the exported column names or download the template. Linked
              records can be a name or ID. If any row fails validation, nothing is
              imported.
            </p>
          </div>

          {parseError ? <ErrorBanner message={parseError} /> : null}
          {apiError ? <ErrorBanner message={apiError} /> : null}

          {parsed ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-surface-200 px-3 py-2 text-xs text-surface-600">
                <span className="inline-flex min-w-0 items-center gap-1.5 font-medium text-surface-800">
                  <FileIcon className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">{parsed.fileName}</span>
                </span>
                <span>{parsed.rowCount.toLocaleString()} rows</span>
                <span>{parsed.columns.length} columns</span>
                <button
                  type="button"
                  onClick={reset}
                  className="ml-auto rounded px-1.5 py-0.5 text-xs font-medium text-brand-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                >
                  Choose another
                </button>
              </div>

              {parsed.missingRequired.length > 0 ? (
                <ErrorBanner
                  message={`Missing required ${
                    parsed.missingRequired.length === 1 ? "column" : "columns"
                  }: ${parsed.missingRequired.join(", ")}.`}
                />
              ) : null}

              {parsed.ignored.length > 0 ? (
                <p className="text-xs text-surface-500">
                  Ignored columns: {parsed.ignored.join(", ")}.
                </p>
              ) : null}

              <div className="overflow-hidden rounded-md border border-surface-200">
                <div className="max-h-60 overflow-auto">
                  <table className="w-full min-w-[720px] text-left text-[12px]">
                    <thead className="sticky top-0 bg-surface-50">
                      <tr className="border-b border-surface-200">
                        {parsed.columns.map((column) => (
                          <th
                            key={column.field.key}
                            className="whitespace-nowrap px-3 py-2 font-medium text-surface-500"
                          >
                            {column.field.label}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {parsed.preview.map((row, index) => (
                        <tr
                          key={index}
                          className="border-b border-surface-100 last:border-b-0"
                        >
                          {parsed.columns.map((column) => (
                            <td
                              key={column.field.key}
                              className="max-w-[220px] truncate px-3 py-2 text-surface-700"
                            >
                              {row[column.field.key] || (
                                <span className="text-surface-300">—</span>
                              )}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <p className="text-[11px] text-surface-400">
                Previewing the first{" "}
                {Math.min(parsed.rowCount, PREVIEW_ROWS)} of{" "}
                {parsed.rowCount.toLocaleString()} rows.
              </p>
            </div>
          ) : null}
        </div>
      )}
    </Modal>
  );
}
