"use client";

import { useEffect, useState, type FormEvent } from "react";

import { PencilIcon, PlusIcon, TrashIcon } from "./icons";
import { Button, ErrorBanner, Input, Modal, Select, Textarea, ToggleSwitch, useToast } from "./ui";
import { ApiError, apiFetch } from "@/lib/api";
import type { Article, Category } from "@/lib/types";

export function ArticleEditor({
  open,
  onClose,
  article,
  categories,
  onSaved,
  onDeleted,
}: {
  open: boolean;
  onClose: () => void;
  article: Article | null;
  categories: Category[];
  onSaved: (article: Article) => void;
  onDeleted?: (id: string) => void;
}) {
  const { toast } = useToast();

  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [published, setPublished] = useState(true);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTitle(article?.title ?? "");
    setBody(article?.body ?? "");
    setCategoryId(article?.categoryId ?? "");
    setPublished(article?.published ?? true);
    setFieldErrors({});
    setFormError(null);
  }, [open, article]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    setFieldErrors({});
    setSaving(true);
    try {
      const payload = {
        title: title.trim(),
        body,
        categoryId: categoryId || undefined,
        published,
      };
      const saved = article
        ? await apiFetch<Article>(`/articles/${encodeURIComponent(article.id)}`, {
            method: "PUT",
            json: payload,
          })
        : await apiFetch<Article>("/articles", { method: "POST", json: payload });
      onSaved(saved);
      toast(article ? "Article updated" : "Article created", { tone: "success" });
      onClose();
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors ?? {});
        setFormError(err.message || "Could not save the article.");
      } else {
        setFormError("Could not save the article.");
      }
    } finally {
      setSaving(false);
    }
  }

  async function onDelete() {
    if (!article) return;
    setDeleting(true);
    try {
      await apiFetch<void>(`/articles/${encodeURIComponent(article.id)}`, {
        method: "DELETE",
      });
      toast("Article deleted", { tone: "success" });
      onDeleted?.(article.id);
      onClose();
    } catch (err) {
      toast("Could not delete the article", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={article ? "Edit article" : "New article"}
      description={
        article
          ? "Update the content. It is re-indexed automatically."
          : "Articles are chunked and embedded into the vector index automatically."
      }
      size="lg"
      footer={
        <>
          {article ? (
            <Button
              variant="danger"
              className="mr-auto"
              loading={deleting}
              onClick={() => void onDelete()}
              icon={<TrashIcon className="h-4 w-4" />}
            >
              Delete
            </Button>
          ) : null}
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={(event) => {
              event.preventDefault();
              void onSubmit(event);
            }}
            loading={saving}
            icon={article ? <PencilIcon className="h-4 w-4" /> : <PlusIcon className="h-4 w-4" />}
          >
            {article ? "Save changes" : "Create article"}
          </Button>
        </>
      }
    >
      {formError ? <ErrorBanner message={formError} className="mb-4" /> : null}

      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Input
          label="Title"
          required
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="How to reset your password"
          error={fieldErrors.title ?? null}
        />

        <Textarea
          label="Body"
          required
          rows={10}
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder="Describe the steps here. Plain text and simple markdown are supported."
          error={fieldErrors.body ?? null}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Category"
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value)}
            placeholder="No category"
            options={categories.map((category) => ({
              value: category.id,
              label: category.name,
            }))}
            error={fieldErrors.categoryId ?? null}
          />
          <div className="rounded-lg border border-surface-200 px-3.5 py-3">
            <ToggleSwitch
              checked={published}
              onChange={setPublished}
              label="Published"
              description="Only published articles are surfaced to the AI."
            />
          </div>
        </div>
      </form>
    </Modal>
  );
}
