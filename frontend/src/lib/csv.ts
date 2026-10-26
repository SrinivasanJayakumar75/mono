/**
 * Small, dependency-free CSV reader/writer used by CRM import and export.
 *
 * The reader understands quoted cells, escaped quotes, embedded newlines,
 * UTF-8 BOMs, CRLF/LF files and comma/semicolon/tab delimiters. The writer
 * protects spreadsheet users from formula injection while keeping exported
 * files round-trippable through `cleanImportedCell`.
 */

export type CsvValue = string | number | boolean | null | undefined;

function stripBom(text: string): string {
  return text.replace(/^\uFEFF/, "");
}

function detectDelimiter(text: string): string {
  const counts: Record<string, number> = { ",": 0, ";": 0, "\t": 0 };
  let quoted = false;

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (character === '"') {
      if (quoted && text[index + 1] === '"') {
        index += 1;
      } else {
        quoted = !quoted;
      }
      continue;
    }
    if (!quoted) {
      if (character === "\n" || character === "\r") break;
      if (character in counts) counts[character] += 1;
    }
  }

  const [delimiter] = Object.entries(counts).sort((left, right) => right[1] - left[1])[0];
  return counts[delimiter] > 0 ? delimiter : ",";
}

/**
 * Parse CSV text into a rectangular-ish array of cells. Rows are not padded;
 * callers should treat missing cells as empty strings. Blank rows are kept so
 * the import UI can report the original spreadsheet row number.
 */
export function parseCsv(input: string): string[][] {
  let text = stripBom(input);
  let delimiter = detectDelimiter(text);

  // Excel users sometimes add a `sep=;` hint as the first line.
  const separatorHint = text.match(/^sep=(.)\r?\n/i);
  if (separatorHint) {
    delimiter = separatorHint[1];
    text = text.slice(separatorHint[0].length);
  }

  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];

    if (quoted) {
      if (character === '"') {
        if (text[index + 1] === '"') {
          cell += '"';
          index += 1;
        } else {
          quoted = false;
        }
      } else {
        cell += character;
      }
      continue;
    }

    if (character === '"' && cell === "") {
      quoted = true;
    } else if (character === delimiter) {
      row.push(cell);
      cell = "";
    } else if (character === "\n") {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else if (character === "\r") {
      if (text[index + 1] === "\n") index += 1;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += character;
    }
  }

  if (quoted) {
    throw new Error("The CSV has an unterminated quoted field.");
  }

  if (cell.length > 0 || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }

  // A trailing newline creates one empty row; drop only that final row.
  const last = rows[rows.length - 1];
  if (rows.length > 0 && last.length === 1 && last[0] === "" && /[\r\n]$/.test(text)) {
    rows.pop();
  }

  return rows;
}

function serialize(value: CsvValue): string {
  if (value === null || value === undefined) return "";
  if (typeof value === "number") return Number.isFinite(value) ? String(value) : "";
  if (typeof value === "boolean") return value ? "true" : "false";

  let text = value;
  // Prevent spreadsheet formula execution for user-supplied text.
  if (/^[=+\-@\t\r]/.test(text)) {
    text = `'${text}`;
  }
  if (/[",\r\n]/.test(text)) {
    text = `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

export function toCsv(rows: CsvValue[][]): string {
  return rows.map((row) => row.map(serialize).join(",")).join("\r\n");
}

/** Reverse the formula-injection guard applied by `toCsv`. */
export function cleanImportedCell(value: string): string {
  return /^'[=+\-@\t\r]/.test(value) ? value.slice(1) : value;
}

export function downloadCsv(filename: string, csv: string): void {
  // The BOM keeps Excel from guessing the wrong encoding for non-ASCII names.
  const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** Normalise a header/key for forgiving label matching. */
export function normalizeHeader(value: string): string {
  return stripBom(value)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "");
}
