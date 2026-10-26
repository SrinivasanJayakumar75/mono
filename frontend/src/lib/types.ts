/**
 * TypeScript types mirroring the Monotron API contract (docs/API.md v1).
 * Field names and shapes are identical to the wire format.
 */

export type Role = "ADMIN" | "AGENT" | "VIEWER";
export type PresenceStatus = "ONLINE" | "OFFLINE" | "AWAY" | "BUSY";
export type AuthorType = "CONTACT" | "AGENT" | "AI";
export type ConversationStatus = "OPEN" | "CLOSED";
export type DocumentStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED";
export type SourceType = "DOCUMENT" | "ARTICLE";
export type Channel = "WIDGET" | "EMAIL" | "CHAT";

export interface User {
  id: string;
  email: string;
  name: string;
  role: Role;
  status: PresenceStatus;
}

export interface Workspace {
  id: string;
  name: string;
  slug: string;
  role: Role;
  appId: string;
}

export interface MessengerApp {
  id: string;
  name: string;
  hmacSecret: string;
  aiEnabled: boolean;
  autoReply: boolean;
  aiModel: string | null;
  aiTemperature: number;
  aiSystemPrompt: string | null;
}

export interface WorkspaceDetail extends Workspace {
  createdAt: string;
  app: MessengerApp;
}

export interface AuthSession {
  user: User;
  activeWorkspace: Workspace;
  workspaces: Workspace[];
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface AuthResponse extends AuthTokens {
  user: User;
  workspaces: Workspace[];
  activeWorkspace: Workspace;
}

export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
  workspaceName?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type GoogleAuthIntent = "LOGIN" | "REGISTER";

export interface GoogleAuthConfig {
  enabled: boolean;
  clientId: string | null;
}

export interface GoogleAuthRequest {
  credential: string;
  intent: GoogleAuthIntent;
  workspaceName?: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

export interface WorkspaceMember {
  userId: string;
  email: string;
  name: string;
  role: Role;
  status: PresenceStatus;
  joinedAt: string;
}

export interface Team {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  memberIds: string[];
}

export interface WidgetSettings {
  appId: string;
  workspaceName: string;
  greeting: string;
  subtitle: string;
  color: string;
  introMessage: string;
  inputPlaceholder: string;
  showBranding: boolean;
  aiEnabled: boolean;
  autoReply: boolean;
}

export type WidgetSettingsUpdate = Partial<
  Pick<
    WidgetSettings,
    | "workspaceName"
    | "greeting"
    | "subtitle"
    | "color"
    | "introMessage"
    | "inputPlaceholder"
    | "showBranding"
    | "aiEnabled"
    | "autoReply"
  >
>;

export interface DocumentSummary {
  id: string;
  filename: string;
  title: string;
  status: DocumentStatus;
  error: string | null;
  pageCount: number;
  chunkCount: number;
  sizeBytes: number;
  mimeType: string;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentChunk {
  id: string;
  chunkIndex: number;
  title: string;
  content: string;
  url: string | null;
}

export interface DocumentDetail extends DocumentSummary {
  chunks: DocumentChunk[];
}

export interface Article {
  id: string;
  title: string;
  slug: string;
  body: string;
  published: boolean;
  categoryId: string | null;
  categoryName: string | null;
  authorName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  sortOrder: number;
}

export interface Citation {
  title: string;
  sourceType: SourceType;
  sourceId: string;
  url: string | null;
  score: number;
  snippet?: string | null;
}

export interface SearchResult {
  sourceType: SourceType;
  sourceId: string;
  title: string;
  url: string | null;
  content: string;
  score: number;
  chunkIndex: number;
}

export interface Contact {
  id: string;
  name: string;
  email: string | null;
  avatarUrl: string | null;
  visitorId: string | null;
  online: boolean;
  browser: string | null;
  os: string | null;
  location: string | null;
  locale?: string | null;
  lastSeenAt?: string | null;
  createdAt?: string;
  customAttributes?: Record<string, unknown>;
  conversationCount?: number;
  crmLeadId?: string | null;
}

export interface LastMessage {
  id: string;
  authorType: AuthorType;
  authorName: string | null;
  body: string;
  createdAt: string;
}

export interface Conversation {
  id: string;
  subject: string;
  status: ConversationStatus;
  channel: Channel | string;
  createdAt: string;
  lastActivityAt: string;
  closedAt: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  teamId: string | null;
  teamName: string | null;
  aiHandled: boolean;
  escalated: boolean;
  unreadCount: number;
  contact?: Contact;
  lastMessage?: LastMessage | null;
}

export interface Message {
  id: string;
  authorType: AuthorType;
  authorId: string | null;
  authorName: string | null;
  authorAvatarUrl: string | null;
  body: string;
  createdAt: string;
  citations: Citation[];
  feedback?: number | null;
}

export interface ConversationDetail {
  conversation: Conversation;
  contact: Contact;
  messages: Message[];
}

export interface ConversationStats {
  open: number;
  closed: number;
  unread: number;
  aiHandled: number;
  total: number;
}

export interface ContactDetail {
  contact: Contact;
  conversations: Conversation[];
}

export interface AiAnswerResponse {
  answer: string;
  model: string;
  provider: string;
  citations: Citation[];
}

export interface AiReplyResponse {
  message: Message;
}

/** Standard paginated envelope. */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

/* ---------------------------------- Widget --------------------------------- */

export interface WidgetConfig {
  appId: string;
  workspaceName: string;
  greeting: string;
  subtitle: string;
  color: string;
  introMessage: string;
  inputPlaceholder: string;
  showBranding: boolean;
}

export type WidgetContentKind = "BLOG" | "FAQ" | "ANNOUNCEMENT";

export interface WidgetContent {
  id: string;
  kind: WidgetContentKind;
  title: string;
  summary: string;
  body: string;
  published: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WidgetContentInput {
  kind: WidgetContentKind;
  title: string;
  summary?: string;
  body: string;
  published?: boolean;
}

export interface WidgetSessionRequest {
  visitorId?: string;
  name?: string;
  email?: string;
}

export interface WidgetMessage {
  id: string;
  authorType: AuthorType;
  authorName: string | null;
  body: string;
  createdAt: string;
  citations?: Citation[];
}

export interface WidgetConversation {
  id: string;
  subject: string;
  status: ConversationStatus;
  createdAt: string;
  lastActivityAt: string;
  messages: WidgetMessage[];
}

export interface WidgetSessionResponse {
  contactId: string;
  visitorId: string;
  name: string | null;
  email: string | null;
  conversations: WidgetConversation[];
}

export interface WidgetSendRequest {
  visitorId: string;
  conversationId?: string;
  body: string;
  name?: string;
  email?: string;
}

export interface WidgetSendResponse {
  conversationId: string;
  contactId: string;
  message: WidgetMessage;
  aiReply: WidgetMessage | null;
}
