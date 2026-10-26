"use client";

import { useRef, useState, type DragEvent, type ChangeEvent } from "react";

import { FileIcon, UploadIcon } from "./icons";
import { Button, ErrorBanner } from "./ui";
import { apiFetch } from "@/lib/api";
import { formatBytes } from "@/lib/format";
import type { DocumentSummary } from "@/lib/types";

const MAX_BYTES = 25 * 1024 * 1024;

export function DocumentUploader({
  onUploaded,
}: {
  onUploaded: (document: DocumentSummary) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [title, setTitle] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  async function upload(file: File) {
    setError(null);
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      setError("Only PDF files are supported.");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError(`That file is ${formatBytes(file.size)}. The limit is 25 MB.`);
      return;
    }
    if (file.size === 0) {
      setError("That file is empty.");
      return;
    }

    setUploading(true);
    try {
      const form = new FormData();
      form.append("file", file);
      if (title.trim()) form.append("title", title.trim());

      const created = await apiFetch<DocumentSummary>("/documents", {
        method: "POST",
        body: form,
      });
      onUploaded(created);
      setTitle("");
      if (inputRef.current) inputRef.current.value = "";
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed.");
    } finally {
      setUploading(false);
    }
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files?.[0];
    if (file) void upload(file);
  }

  function onChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) void upload(file);
  }

  return (
    <div className="space-y-3">
      <div
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`rounded-card border-2 border-dashed px-6 py-8 text-center transition-colors ${
          dragging
            ? "border-brand-400 bg-brand-50/60"
            : "border-surface-300 bg-surface-50/60 hover:border-brand-300"
        }`}
      >
        <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-white text-brand-600 shadow-card">
          <UploadIcon className="h-5 w-5" />
        </span>
        <p className="mt-3 text-sm font-semibold text-surface-800">
          Drop a PDF here, or browse
        </p>
        <p className="mt-1 text-xs text-surface-500">
          Maximum 25 MB · the file is chunked and embedded for semantic search.
        </p>

        <div className="mx-auto mt-4 flex max-w-sm items-end gap-2">
          <div className="flex-1 text-left">
            <label
              htmlFor="document-title"
              className="mb-1 block text-xs font-medium text-surface-600"
            >
              Title (optional)
            </label>
            <input
              id="document-title"
              type="text"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Employee Handbook"
              className="w-full rounded-lg border border-surface-300 bg-white px-3 py-2 text-sm text-surface-900 placeholder:text-surface-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
            />
          </div>
          <Button
            onClick={() => inputRef.current?.click()}
            loading={uploading}
            icon={<FileIcon className="h-4 w-4" />}
          >
            Choose PDF
          </Button>
        </div>

        <input
          ref={inputRef}
          type="file"
          accept="application/pdf,.pdf"
          onChange={onChange}
          className="sr-only"
          aria-label="Upload PDF document"
        />
      </div>

      {error ? <ErrorBanner message={error} /> : null}
    </div>
  );
}
