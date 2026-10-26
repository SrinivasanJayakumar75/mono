"use client";

import clsx from "clsx";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import { ArticleEditor } from "@/components/article-editor";
import { DocumentUploader } from "@/components/document-uploader";
import {
  BookIcon,
  CheckIcon,
  ExternalLinkIcon,
  FileIcon,
  PencilIcon,
  PlusIcon,
  RefreshIcon,
  SearchIcon,
  TagIcon,
  TrashIcon,
} from "@/components/icons";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  Input,
  Modal,
  Skeleton,
  useToast,
} from "@/components/ui";
import { apiBlob, apiFetch } from "@/lib/api";
import { formatBytes, formatNumber, formatRelative, formatScore } from "@/lib/format";
import type {
  Article,
  Category,
  DocumentChunk,
  DocumentDetail,
  DocumentStatus,
  DocumentSummary,
  Page,
  SearchResult,
} from "@/lib/types";

type Tab = "documents" | "articles";

const STATUS_VARIANT: Record<
  DocumentStatus,
  "neutral" | "brand" | "success" | "warning" | "danger"
> = {
  PENDING: "neutral",
  PROCESSING: "warning",
  READY: "success",
  FAILED: "danger",
};

function StatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <Badge variant={STATUS_VARIANT[status] ?? "neutral"}>
      {status === "PROCESSING" ? (
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
      ) : null}
      {status}
    </Badge>
  );
}

export default function KnowledgePage() {
  const { toast } = useToast();
  const [tab, setTab] = useState<Tab>("documents");

  /* ------------------------------- documents ------------------------------ */
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [documentsLoading, setDocumentsLoading] = useState(true);
  const [documentsError, setDocumentsError] = useState<string | null>(null);
  const [docQuery, setDocQuery] = useState("");
  const [chunksTarget, setChunksTarget] = useState<DocumentDetail | null>(null);
  const [chunksLoading, setChunksLoading] = useState(false);
  const [deleteDocTarget, setDeleteDocTarget] = useState<DocumentSummary | null>(null);
  const [actionId, setActionId] = useState<string | null>(null);

  const loadDocuments = useCallback(async () => {
    setDocumentsLoading(true);
    setDocumentsError(null);
    try {
      const result = await apiFetch<Page<DocumentSummary>>("/documents", {
        params: { q: docQuery.trim() || undefined, size: 50 },
      });
      setDocuments(result.items ?? []);
    } catch (err) {
      setDocumentsError(
        err instanceof Error ? err.message : "Could not load documents.",
      );
    } finally {
      setDocumentsLoading(false);
    }
  }, [docQuery]);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  const pendingIds = useMemo(
    () =>
      documents
        .filter(
          (document) =>
            document.status === "PENDING" || document.status === "PROCESSING",
        )
        .map((document) => document.id),
    [documents],
  );

  useEffect(() => {
    if (pendingIds.length === 0) return;
    const timer = window.setInterval(() => {
      pendingIds.forEach(async (id) => {
        try {
          const updated = await apiFetch<DocumentDetail>(`/documents/${id}`);
          setDocuments((current) =>
            current.map((document) =>
              document.id === id
                ? {
                    id: updated.id,
                    filename: updated.filename,
                    title: updated.title,
                    status: updated.status,
                    error: updated.error,
                    pageCount: updated.pageCount,
                    chunkCount: updated.chunkCount,
                    sizeBytes: updated.sizeBytes,
                    mimeType: updated.mimeType,
                    createdAt: updated.createdAt,
                    updatedAt: updated.updatedAt,
                  }
                : document,
            ),
          );
        } catch {
          /* keep polling */
        }
      });
    }, 1500);
    return () => window.clearInterval(timer);
  }, [pendingIds]);

  async function viewChunks(document: DocumentSummary) {
    setChunksLoading(true);
    setChunksTarget({ ...document, chunks: [] });
    try {
      const detail = await apiFetch<DocumentDetail>(
        `/documents/${encodeURIComponent(document.id)}`,
      );
      setChunksTarget(detail);
    } catch (err) {
      toast("Could not load chunks", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
      setChunksTarget(null);
    } finally {
      setChunksLoading(false);
    }
  }

  async function openPdf(document: DocumentSummary) {
    try {
      const blob = await apiBlob(`/documents/${encodeURIComponent(document.id)}/file`);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      toast("Could not open the PDF", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    }
  }

  async function reindex(document: DocumentSummary) {
    setActionId(document.id);
    try {
      const updated = await apiFetch<DocumentSummary>(
        `/documents/${encodeURIComponent(document.id)}/reindex`,
        { method: "POST", json: {} },
      );
      setDocuments((current) =>
        current.map((item) => (item.id === updated.id ? { ...item, ...updated } : item)),
      );
      toast("Reindex started", { tone: "success" });
    } catch (err) {
      toast("Reindex failed", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setActionId(null);
    }
  }

  async function deleteDocument() {
    if (!deleteDocTarget) return;
    const id = deleteDocTarget.id;
    setActionId(id);
    try {
      await apiFetch<void>(`/documents/${encodeURIComponent(id)}`, { method: "DELETE" });
      setDocuments((current) => current.filter((item) => item.id !== id));
      toast("Document deleted", { tone: "success" });
      setDeleteDocTarget(null);
    } catch (err) {
      toast("Could not delete the document", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setActionId(null);
    }
  }

  /* -------------------------------- articles ------------------------------ */
  const [articles, setArticles] = useState<Article[]>([]);
  const [articlesTotal, setArticlesTotal] = useState(0);
  const [articlePage, setArticlePage] = useState(0);
  const [articlesLoading, setArticlesLoading] = useState(true);
  const [articlesError, setArticlesError] = useState<string | null>(null);
  const [articleQuery, setArticleQuery] = useState("");
  const [debouncedArticleQuery, setDebouncedArticleQuery] = useState("");
  const [categories, setCategories] = useState<Category[]>([]);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingArticle, setEditingArticle] = useState<Article | null>(null);
  const [newCategory, setNewCategory] = useState("");
  const [categoryError, setCategoryError] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedArticleQuery(articleQuery.trim());
      setArticlePage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [articleQuery]);

  const loadArticles = useCallback(async () => {
    setArticlesLoading(true);
    setArticlesError(null);
    try {
      const result = await apiFetch<Page<Article>>("/articles", {
        params: {
          q: debouncedArticleQuery || undefined,
          page: articlePage,
          size: 20,
        },
      });
      setArticles(result.items ?? []);
      setArticlesTotal(result.total ?? 0);
    } catch (err) {
      setArticlesError(err instanceof Error ? err.message : "Could not load articles.");
    } finally {
      setArticlesLoading(false);
    }
  }, [debouncedArticleQuery, articlePage]);

  const loadCategories = useCallback(async () => {
    try {
      const result = await apiFetch<{ items: Category[] }>("/categories");
      setCategories(result.items ?? []);
    } catch {
      /* categories are optional */
    }
  }, []);

  useEffect(() => {
    void loadArticles();
  }, [loadArticles]);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories]);

  async function createCategory(event: FormEvent) {
    event.preventDefault();
    const name = newCategory.trim();
    if (!name) return;
    setCategoryError(null);
    try {
      const created = await apiFetch<Category>("/categories", {
        method: "POST",
        json: { name },
      });
      setCategories((current) => [...current, created]);
      setNewCategory("");
      toast("Category created", { tone: "success" });
    } catch (err) {
      setCategoryError(err instanceof Error ? err.message : "Could not create category.");
    }
  }

  async function deleteCategory(category: Category) {
    try {
      await apiFetch<void>(`/categories/${encodeURIComponent(category.id)}`, {
        method: "DELETE",
      });
      setCategories((current) => current.filter((item) => item.id !== category.id));
      toast("Category deleted", { tone: "success" });
    } catch (err) {
      toast("Could not delete the category", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    }
  }

  /* ----------------------------- semantic search -------------------------- */
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<SearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  async function runSearch(event: React.FormEvent) {
    event.preventDefault();
    const query = searchQuery.trim();
    if (!query) return;
    setSearching(true);
    setSearchError(null);
    try {
      const result = await apiFetch<{ items: SearchResult[] }>("/search", {
        method: "POST",
        json: { query, limit: 8 },
      });
      setSearchResults(result.items ?? []);
    } catch (err) {
      setSearchError(err instanceof Error ? err.message : "Search failed.");
      setSearchResults(null);
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-surface-50">
      {/* tabs */}
      <div className="shrink-0 border-b border-surface-200 bg-white px-3 sm:px-5">
        <div
          className="flex h-11 items-center gap-1"
          role="tablist"
          aria-label="Knowledge sections"
        >
          {(
            [
              { id: "documents" as const, label: "Documents", icon: <FileIcon className="h-3.5 w-3.5" /> },
              { id: "articles" as const, label: "Articles", icon: <BookIcon className="h-3.5 w-3.5" /> },
            ]
          ).map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={tab === item.id}
              onClick={() => setTab(item.id)}
              className={clsx(
                "relative inline-flex h-11 items-center gap-2 px-2.5 text-[12.5px] font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500",
                tab === item.id
                  ? "text-surface-900"
                  : "text-surface-500 hover:text-surface-800",
              )}
            >
              {item.icon}
              {item.label}
              {tab === item.id ? (
                <span className="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-surface-900" />
              ) : null}
            </button>
          ))}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5 sm:px-6">
      {tab === "documents" ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="space-y-6">
            <Card
              title="Upload a document"
              description="PDFs are chunked, embedded and indexed automatically."
            >
              <DocumentUploader
                onUploaded={(document) => {
                  setDocuments((current) => [document, ...current]);
                  toast("Upload started", {
                    tone: "success",
                    description: "We are indexing the file in the background.",
                  });
                }}
              />
            </Card>

            <Card
              title="Documents"
              description={
                documents.length > 0
                  ? `${formatNumber(documents.length)} document${documents.length === 1 ? "" : "s"}`
                  : undefined
              }
              action={
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-surface-400" />
                    <input
                      type="search"
                      value={docQuery}
                      onChange={(event) => setDocQuery(event.target.value)}
                      placeholder="Search documents"
                      aria-label="Search documents"
                      className="w-44 rounded-lg border border-surface-300 bg-white py-1.5 pl-8 pr-2 text-sm text-surface-900 placeholder:text-surface-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
                    />
                  </div>
                  <Button
                    size="icon"
                    variant="ghost"
                    onClick={() => void loadDocuments()}
                    aria-label="Refresh documents"
                  >
                    <RefreshIcon className="h-4 w-4" />
                  </Button>
                </div>
              }
            >
              {documentsError ? (
                <ErrorBanner message={documentsError} onRetry={() => void loadDocuments()} />
              ) : documentsLoading && documents.length === 0 ? (
                <div className="space-y-2">
                  {[0, 1, 2].map((index) => (
                    <div key={index} className="rounded-lg border border-surface-200 p-4">
                      <Skeleton className="h-4 w-1/2" />
                      <Skeleton className="mt-2 h-3 w-1/3" />
                    </div>
                  ))}
                </div>
              ) : documents.length === 0 ? (
                <EmptyState
                  icon={<FileIcon className="h-5 w-5" />}
                  title="No documents yet"
                  description="Upload your first PDF to make it searchable and citable."
                />
              ) : (
                <ul className="divide-y divide-surface-100">
                  {documents.map((document) => (
                    <li key={document.id} className="py-4 first:pt-0 last:pb-0">
                      <div className="flex flex-wrap items-start gap-3">
                        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-surface-100 text-surface-500">
                          <FileIcon className="h-5 w-5" />
                        </span>

                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="truncate text-sm font-semibold text-surface-900">
                              {document.title || document.filename}
                            </p>
                            <StatusBadge status={document.status} />
                          </div>
                          <p className="mt-0.5 truncate text-xs text-surface-500">
                            {document.filename} · {formatBytes(document.sizeBytes)} ·{" "}
                            {formatNumber(document.pageCount)} pages ·{" "}
                            {formatNumber(document.chunkCount)} chunks · updated{" "}
                            {formatRelative(document.updatedAt)}
                          </p>

                          {document.status === "PROCESSING" ||
                          document.status === "PENDING" ? (
                            <div className="mt-2 flex items-center gap-2">
                              <div className="h-1.5 w-40 overflow-hidden rounded-full bg-surface-100">
                                <div className="h-full w-1/2 animate-pulse rounded-full bg-brand-500" />
                              </div>
                              <span className="text-[11px] text-surface-500">
                                Indexing…
                              </span>
                            </div>
                          ) : null}

                          {document.status === "FAILED" && document.error ? (
                            <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">
                              {document.error}
                            </p>
                          ) : null}
                        </div>

                        <div className="flex shrink-0 items-center gap-1.5">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => void viewChunks(document)}
                            loading={chunksLoading && chunksTarget?.id === document.id}
                          >
                            Chunks
                          </Button>
                          <Button
                            size="icon"
                            variant="ghost"
                            onClick={() => void openPdf(document)}
                            aria-label={`Open ${document.title || document.filename}`}
                          >
                            <ExternalLinkIcon className="h-4 w-4" />
                          </Button>
                          <Button
                            size="icon"
                            variant="ghost"
                            loading={actionId === document.id}
                            onClick={() => void reindex(document)}
                            aria-label={`Reindex ${document.title || document.filename}`}
                          >
                            <RefreshIcon className="h-4 w-4" />
                          </Button>
                          <Button
                            size="icon"
                            variant="ghost"
                            onClick={() => setDeleteDocTarget(document)}
                            aria-label={`Delete ${document.title || document.filename}`}
                          >
                            <TrashIcon className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>

          {/* search test */}
          <Card
            title="Test semantic search"
            description="Query the vector index exactly like the AI assistant does."
          >
            <form onSubmit={runSearch} className="space-y-3">
              <Input
                label="Query"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="How do I reset my password?"
                error={searchError}
              />
              <Button type="submit" loading={searching} className="w-full">
                Search
              </Button>
            </form>

            <div className="mt-4">
              {searchResults === null ? (
                <p className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-6 text-center text-xs text-surface-500">
                  Run a query to see the closest knowledge chunks.
                </p>
              ) : searchResults.length === 0 ? (
                <p className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-6 text-center text-xs text-surface-500">
                  No matching content found.
                </p>
              ) : (
                <ul className="space-y-2.5">
                  {searchResults.map((result, index) => (
                    <li
                      key={`${result.sourceType}-${result.sourceId}-${index}`}
                      className="rounded-lg border border-surface-200 p-3"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <p className="truncate text-xs font-semibold text-surface-800">
                          {result.title || "Untitled"}
                        </p>
                        <span className="shrink-0 rounded bg-accent-50 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-accent-700">
                          {formatScore(result.score)}
                        </span>
                      </div>
                      <p className="mt-1 line-clamp-4 text-[11px] leading-relaxed text-surface-500">
                        {result.content}
                      </p>
                      <p className="mt-1.5 text-[10px] uppercase tracking-wide text-surface-400">
                        {result.sourceType} · chunk {result.chunkIndex}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </Card>
        </div>
      ) : (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <Card
            title="Articles"
            description="Written knowledge your AI can cite."
            action={
              <div className="flex items-center gap-2">
                <div className="relative">
                  <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-surface-400" />
                  <input
                    type="search"
                    value={articleQuery}
                    onChange={(event) => setArticleQuery(event.target.value)}
                    placeholder="Search articles"
                    aria-label="Search articles"
                    className="w-44 rounded-lg border border-surface-300 bg-white py-1.5 pl-8 pr-2 text-sm text-surface-900 placeholder:text-surface-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
                  />
                </div>
                <Button
                  size="sm"
                  onClick={() => {
                    setEditingArticle(null);
                    setEditorOpen(true);
                  }}
                  icon={<PlusIcon className="h-4 w-4" />}
                >
                  New
                </Button>
              </div>
            }
          >
            {articlesError ? (
              <ErrorBanner message={articlesError} onRetry={() => void loadArticles()} />
            ) : articlesLoading && articles.length === 0 ? (
              <div className="space-y-2">
                {[0, 1, 2].map((index) => (
                  <div key={index} className="rounded-lg border border-surface-200 p-4">
                    <Skeleton className="h-4 w-1/2" />
                    <Skeleton className="mt-2 h-3 w-2/3" />
                  </div>
                ))}
              </div>
            ) : articles.length === 0 ? (
              <EmptyState
                icon={<BookIcon className="h-5 w-5" />}
                title="No articles yet"
                description="Write an article so the AI can quote your process and policy."
                action={
                  <Button
                    onClick={() => {
                      setEditingArticle(null);
                      setEditorOpen(true);
                    }}
                    icon={<PlusIcon className="h-4 w-4" />}
                  >
                    New article
                  </Button>
                }
              />
            ) : (
              <>
                <ul className="divide-y divide-surface-100">
                  {articles.map((article) => (
                    <li key={article.id} className="py-4 first:pt-0 last:pb-0">
                      <div className="flex items-start gap-3">
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="truncate text-sm font-semibold text-surface-900">
                              {article.title}
                            </p>
                            <Badge variant={article.published ? "success" : "neutral"}>
                              {article.published ? "Published" : "Draft"}
                            </Badge>
                            {article.categoryName ? (
                              <Badge variant="info">
                                <TagIcon className="h-3 w-3" />
                                {article.categoryName}
                              </Badge>
                            ) : null}
                          </div>
                          <p className="mt-1 line-clamp-2 text-xs text-surface-500">
                            {article.body}
                          </p>
                          <p className="mt-1 text-[11px] text-surface-400">
                            By {article.authorName ?? "Unknown"} · updated{" "}
                            {formatRelative(article.updatedAt)}
                          </p>
                        </div>
                        <Button
                          size="icon"
                          variant="ghost"
                          onClick={() => {
                            setEditingArticle(article);
                            setEditorOpen(true);
                          }}
                          aria-label={`Edit ${article.title}`}
                        >
                          <PencilIcon className="h-4 w-4" />
                        </Button>
                      </div>
                    </li>
                  ))}
                </ul>

                {articlesTotal > 20 ? (
                  <div className="mt-4 flex items-center justify-between border-t border-surface-100 pt-3">
                    <p className="text-xs text-surface-500">
                      Page {articlePage + 1} of {Math.ceil(articlesTotal / 20)}
                    </p>
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={articlePage <= 0}
                        onClick={() => setArticlePage((value) => Math.max(0, value - 1))}
                      >
                        Prev
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={(articlePage + 1) * 20 >= articlesTotal}
                        onClick={() => setArticlePage((value) => value + 1)}
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                ) : null}
              </>
            )}
          </Card>

          <Card title="Categories" description="Group articles by topic.">
            <form onSubmit={createCategory} className="flex items-end gap-2">
              <Input
                label="New category"
                value={newCategory}
                onChange={(event) => setNewCategory(event.target.value)}
                placeholder="Support"
                error={categoryError}
              />
              <Button
                type="submit"
                loading={false}
                icon={<PlusIcon className="h-4 w-4" />}
                aria-label="Create category"
              >
                Add
              </Button>
            </form>

            <ul className="mt-4 space-y-1.5">
              {categories.length === 0 ? (
                <li className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-6 text-center text-xs text-surface-500">
                  No categories yet.
                </li>
              ) : (
                categories.map((category) => (
                  <li
                    key={category.id}
                    className="flex items-center gap-2 rounded-lg border border-surface-200 px-3 py-2"
                  >
                    <TagIcon className="h-3.5 w-3.5 shrink-0 text-surface-400" />
                    <span className="min-w-0 flex-1 truncate text-sm text-surface-700">
                      {category.name}
                    </span>
                    <button
                      type="button"
                      onClick={() => void deleteCategory(category)}
                      aria-label={`Delete category ${category.name}`}
                      className="rounded-md p-1.5 text-surface-400 transition-colors hover:bg-rose-50 hover:text-rose-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-500"
                    >
                      <TrashIcon className="h-4 w-4" />
                    </button>
                  </li>
                ))
              )}
            </ul>
          </Card>
        </div>
      )}

      {/* chunks modal */}
      <Modal
        open={Boolean(chunksTarget)}
        onClose={() => setChunksTarget(null)}
        title={chunksTarget?.title || chunksTarget?.filename || "Document chunks"}
        description={
          chunksTarget
            ? `${formatNumber(chunksTarget.pageCount)} pages · ${formatNumber(
                chunksTarget.chunkCount,
              )} chunks indexed`
            : undefined
        }
        size="lg"
      >
        {chunksLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : (chunksTarget?.chunks?.length ?? 0) === 0 ? (
          <p className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-8 text-center text-sm text-surface-500">
            No chunks have been indexed for this document yet.
          </p>
        ) : (
          <ul className="space-y-3">
            {(chunksTarget?.chunks ?? []).map((chunk: DocumentChunk) => (
              <li
                key={chunk.id}
                className="rounded-lg border border-surface-200 bg-surface-50/60 p-3"
              >
                <div className="flex items-center gap-2">
                  <span className="rounded bg-brand-50 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-brand-700">
                    #{chunk.chunkIndex}
                  </span>
                  <p className="truncate text-xs font-semibold text-surface-700">
                    {chunk.title}
                  </p>
                </div>
                <p className="mt-1.5 whitespace-pre-wrap text-xs leading-relaxed text-surface-600">
                  {chunk.content}
                </p>
              </li>
            ))}
          </ul>
        )}
      </Modal>

      {/* delete document confirm */}
      <Modal
        open={Boolean(deleteDocTarget)}
        onClose={() => setDeleteDocTarget(null)}
        title="Delete document"
        description="This removes the file and all of its indexed chunks. This cannot be undone."
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleteDocTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={actionId === deleteDocTarget?.id}
              onClick={() => void deleteDocument()}
              icon={<TrashIcon className="h-4 w-4" />}
            >
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-surface-600">
          <span className="font-semibold text-surface-900">
            {deleteDocTarget?.title || deleteDocTarget?.filename}
          </span>{" "}
          will be permanently removed from this workspace.
        </p>
      </Modal>

      {/* article editor */}
      <ArticleEditor
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        article={editingArticle}
        categories={categories}
        onSaved={(saved) => {
          setArticles((current) => {
            const index = current.findIndex((item) => item.id === saved.id);
            if (index === -1) return [saved, ...current];
            const next = [...current];
            next[index] = saved;
            return next;
          });
        }}
        onDeleted={(id) => {
          setArticles((current) => current.filter((item) => item.id !== id));
        }}
      />

      {articles.length > 0 ? (
        <p className="flex items-center gap-1.5 text-xs text-surface-400">
          <CheckIcon className="h-3.5 w-3.5" />
          Articles are automatically chunked and embedded after every save.
        </p>
      ) : null}
      </div>
    </div>
  );
}
