"use client";

import { useParams } from "next/navigation";

import { InboxView } from "@/components/inbox-view";

export default function ConversationPage() {
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params?.id) ? params.id[0] : params?.id;

  return <InboxView conversationId={id} />;
}
