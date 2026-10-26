-- =====================================================================
-- RelayDesk — baseline schema (V1)
--
-- Multi-tenant AI customer support platform.
--
-- Tenancy model
--   * Every tenant-scoped row carries `workspace_id`.
--   * Row Level Security is ENABLED *and* FORCE'd on tenant tables, so the
--     policies apply even to the table owner.
--   * The session variable `app.workspace_id` selects the active tenant.
--     It is set transaction-locally (set_config(..., true)) by the
--     application on every request, inside a single transaction.
--   * force_row_workspace() stamps workspace_id on INSERT so a row can
--     never be written into another tenant's slice by mistake.
-- =====================================================================
--
-- PostgreSQL database dump
--



-- Dumped from database version 16.15 (Debian 16.15-1.pgdg12+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg12+2)










SET row_security = off;

--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


--
-- Name: app_workspace_id(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.app_workspace_id() RETURNS character varying
    LANGUAGE sql STABLE
    AS $$
    select nullif(current_setting('app.workspace_id', true), '')::varchar
$$;


--
-- Name: force_row_workspace(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.force_row_workspace() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    new.workspace_id := coalesce(app_workspace_id(), 'default-workspace');
    return new;
end $$;


--
-- Name: sync_app_workspace_lookup(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_app_workspace_lookup() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    insert into app_workspace_lookup (app_id, workspace_id) values (new.id, new.workspace_id)
    on conflict (app_id) do update set workspace_id = excluded.workspace_id;
    return new;
end $$;






--
-- Name: app_workspace_lookup; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_workspace_lookup (
    app_id character varying(32) NOT NULL,
    workspace_id character varying(36) NOT NULL
);


--
-- Name: apps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apps (
    id character varying(32) NOT NULL,
    name character varying(200) NOT NULL,
    hmac_secret character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.apps FORCE ROW LEVEL SECURITY;


--
-- Name: articles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.articles (
    id character varying(36) NOT NULL,
    category_id character varying(36),
    title character varying(500) NOT NULL,
    slug character varying(500) NOT NULL,
    body text NOT NULL,
    published boolean DEFAULT false NOT NULL,
    author_id character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36)
);

ALTER TABLE ONLY public.articles FORCE ROW LEVEL SECURITY;


--
-- Name: calls; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calls (
    id character varying(36) NOT NULL,
    direction character varying(10) DEFAULT 'OUTBOUND'::character varying NOT NULL,
    outcome character varying(16) DEFAULT 'NO_ANSWER'::character varying NOT NULL,
    subject character varying(300),
    notes text,
    duration_seconds integer,
    related_type character varying(16),
    related_id character varying(36),
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.calls FORCE ROW LEVEL SECURITY;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    slug character varying(200) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36)
);

ALTER TABLE ONLY public.categories FORCE ROW LEVEL SECURITY;


--
-- Name: companies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.companies (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    domain character varying(255),
    industry character varying(100),
    size character varying(50),
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.companies FORCE ROW LEVEL SECURITY;


--
-- Name: contacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contacts (
    id character varying(36) NOT NULL,
    app_id character varying(32) NOT NULL,
    visitor_id character varying(36),
    email character varying(255),
    name character varying(255),
    avatar_url character varying(1000),
    signed_up_at timestamp with time zone,
    last_seen_at timestamp with time zone,
    online boolean DEFAULT false NOT NULL,
    locale character varying(32),
    location character varying(255),
    browser character varying(64),
    os character varying(64),
    custom_attributes jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.contacts FORCE ROW LEVEL SECURITY;


--
-- Name: content_chunks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_chunks (
    id character varying(36) NOT NULL,
    source_type character varying(16) NOT NULL,
    source_id character varying(36) NOT NULL,
    title character varying(500),
    url character varying(1000),
    chunk_index integer DEFAULT 0 NOT NULL,
    content text NOT NULL,
    embedding public.vector(1536),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.content_chunks FORCE ROW LEVEL SECURITY;


--
-- Name: conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversations (
    id character varying(36) NOT NULL,
    app_id character varying(32) NOT NULL,
    contact_id character varying(36) NOT NULL,
    subject character varying(500),
    status character varying(16) DEFAULT 'OPEN'::character varying NOT NULL,
    assignee_id character varying(36),
    team_id character varying(36),
    agent_last_seen_at timestamp with time zone,
    last_activity_at timestamp with time zone DEFAULT now() NOT NULL,
    closed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.conversations FORCE ROW LEVEL SECURITY;


--
-- Name: deals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.deals (
    id character varying(36) NOT NULL,
    title character varying(200) NOT NULL,
    value_amount numeric(14,2) DEFAULT 0 NOT NULL,
    stage character varying(16) DEFAULT 'NEW'::character varying NOT NULL,
    company_id character varying(36),
    lead_id character varying(36),
    expected_close_date date,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.deals FORCE ROW LEVEL SECURITY;


--
-- Name: documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documents (
    id character varying(36) NOT NULL,
    filename character varying(500) NOT NULL,
    title character varying(500) NOT NULL,
    file_path character varying(1000) NOT NULL,
    status character varying(16) NOT NULL,
    error text,
    page_count integer,
    chunk_count integer,
    uploaded_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.documents FORCE ROW LEVEL SECURITY;



--



--
-- Name: leads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leads (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    email character varying(255),
    phone character varying(50),
    source character varying(16) DEFAULT 'OTHER'::character varying NOT NULL,
    status character varying(16) DEFAULT 'NEW'::character varying NOT NULL,
    company_id character varying(36),
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    first_name character varying(100),
    last_name character varying(100),
    title character varying(150),
    company_text character varying(200),
    website character varying(255),
    location character varying(200),
    country character varying(100),
    industry character varying(100),
    priority character varying(16) DEFAULT 'MEDIUM'::character varying NOT NULL,
    buying_intent character varying(16) DEFAULT 'MEDIUM'::character varying NOT NULL,
    expected_purchase_date date,
    lead_score integer,
    whatsapp character varying(50),
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.leads FORCE ROW LEVEL SECURITY;


--
-- Name: meetings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meetings (
    id character varying(36) NOT NULL,
    title character varying(300) NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    duration_minutes integer DEFAULT 30 NOT NULL,
    location character varying(300),
    status character varying(16) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    related_type character varying(16),
    related_id character varying(36),
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.meetings FORCE ROW LEVEL SECURITY;


--
-- Name: messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.messages (
    id character varying(36) NOT NULL,
    conversation_id character varying(36) NOT NULL,
    author_type character varying(16) NOT NULL,
    author_id character varying(36),
    author_name character varying(255),
    author_avatar_url character varying(1000),
    body text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.messages FORCE ROW LEVEL SECURITY;


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    product_id character varying(36),
    product_name character varying(300) NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    unit_price numeric(14,2) DEFAULT 0 NOT NULL,
    line_no integer DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY public.order_items FORCE ROW LEVEL SECURITY;


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id character varying(36) NOT NULL,
    order_number character varying(64) NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    order_date date DEFAULT CURRENT_DATE NOT NULL,
    company_id character varying(36),
    lead_id character varying(36),
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.orders FORCE ROW LEVEL SECURITY;


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    sku character varying(100),
    price numeric(14,2) DEFAULT 0 NOT NULL,
    description text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.products FORCE ROW LEVEL SECURITY;


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
    id character varying(36) NOT NULL,
    title character varying(300) NOT NULL,
    notes text,
    due_date date,
    priority character varying(16) DEFAULT 'NORMAL'::character varying NOT NULL,
    status character varying(16) DEFAULT 'OPEN'::character varying NOT NULL,
    related_type character varying(16),
    related_id character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying) NOT NULL
);

ALTER TABLE ONLY public.tasks FORCE ROW LEVEL SECURITY;


--
-- Name: team_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_members (
    team_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.team_members FORCE ROW LEVEL SECURITY;


--
-- Name: teams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teams (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.teams FORCE ROW LEVEL SECURITY;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id character varying(36) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    avatar_url character varying(1000),
    role character varying(16) NOT NULL,
    status character varying(16) DEFAULT 'OFFLINE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: widget_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.widget_settings (
    app_id character varying(32) NOT NULL,
    workspace_name character varying(200) NOT NULL,
    greeting character varying(500) NOT NULL,
    subtitle character varying(500) NOT NULL,
    color character varying(16) NOT NULL,
    intro_message character varying(1000) NOT NULL,
    input_placeholder character varying(200) NOT NULL,
    show_branding boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.widget_settings FORCE ROW LEVEL SECURITY;


--
-- Name: workspace_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workspace_memberships (
    workspace_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    role character varying(16) DEFAULT 'AGENT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: workspaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workspaces (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    slug character varying(200) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: app_workspace_lookup app_workspace_lookup_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_workspace_lookup
    ADD CONSTRAINT app_workspace_lookup_pkey PRIMARY KEY (app_id);


--
-- Name: apps apps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apps
    ADD CONSTRAINT apps_pkey PRIMARY KEY (id);


--
-- Name: articles articles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_pkey PRIMARY KEY (id);


--
-- Name: calls calls_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calls
    ADD CONSTRAINT calls_pkey PRIMARY KEY (id);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: companies companies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.companies
    ADD CONSTRAINT companies_pkey PRIMARY KEY (id);


--
-- Name: contacts contacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contacts
    ADD CONSTRAINT contacts_pkey PRIMARY KEY (id);


--
-- Name: content_chunks content_chunks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_chunks
    ADD CONSTRAINT content_chunks_pkey PRIMARY KEY (id);


--
-- Name: conversations conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);


--
-- Name: deals deals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deals
    ADD CONSTRAINT deals_pkey PRIMARY KEY (id);


--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);


--


--
-- Name: leads leads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_pkey PRIMARY KEY (id);


--
-- Name: meetings meetings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meetings
    ADD CONSTRAINT meetings_pkey PRIMARY KEY (id);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: team_members team_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT team_members_pkey PRIMARY KEY (team_id, user_id);


--
-- Name: teams teams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: widget_settings widget_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.widget_settings
    ADD CONSTRAINT widget_settings_pkey PRIMARY KEY (app_id);


--
-- Name: workspace_memberships workspace_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspace_memberships
    ADD CONSTRAINT workspace_memberships_pkey PRIMARY KEY (workspace_id, user_id);


--
-- Name: workspaces workspaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspaces
    ADD CONSTRAINT workspaces_pkey PRIMARY KEY (id);


--
-- Name: workspaces workspaces_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspaces
    ADD CONSTRAINT workspaces_slug_key UNIQUE (slug);


--


--
-- Name: idx_app_workspace_lookup_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_workspace_lookup_workspace ON public.app_workspace_lookup USING btree (workspace_id);


--
-- Name: idx_apps_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apps_workspace ON public.apps USING btree (workspace_id);


--
-- Name: idx_articles_published; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_articles_published ON public.articles USING btree (published, updated_at DESC);


--
-- Name: idx_articles_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_articles_workspace ON public.articles USING btree (workspace_id);


--
-- Name: idx_calls_direction; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calls_direction ON public.calls USING btree (direction);


--
-- Name: idx_calls_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calls_occurred ON public.calls USING btree (occurred_at DESC);


--
-- Name: idx_calls_related; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calls_related ON public.calls USING btree (related_type, related_id);


--
-- Name: idx_calls_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calls_workspace ON public.calls USING btree (workspace_id);


--
-- Name: idx_categories_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_workspace ON public.categories USING btree (workspace_id);


--
-- Name: idx_companies_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_companies_name ON public.companies USING btree (name);


--
-- Name: idx_companies_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_companies_workspace ON public.companies USING btree (workspace_id);


--
-- Name: idx_contacts_app_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contacts_app_workspace ON public.contacts USING btree (app_id);


--
-- Name: idx_contacts_last_seen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contacts_last_seen ON public.contacts USING btree (last_seen_at DESC NULLS LAST);


--
-- Name: idx_content_chunks_embedding; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_chunks_embedding ON public.content_chunks USING hnsw (embedding public.vector_cosine_ops);


--
-- Name: idx_content_chunks_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_chunks_source ON public.content_chunks USING btree (source_type, source_id);


--
-- Name: idx_conversations_app_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_app_workspace ON public.conversations USING btree (app_id);


--
-- Name: idx_conversations_assignee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_assignee ON public.conversations USING btree (assignee_id);


--
-- Name: idx_conversations_contact; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_contact ON public.conversations USING btree (contact_id);


--
-- Name: idx_conversations_status_activity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_status_activity ON public.conversations USING btree (status, last_activity_at DESC);


--
-- Name: idx_conversations_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_workspace ON public.conversations USING btree (workspace_id);


--
-- Name: idx_deals_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_deals_company ON public.deals USING btree (company_id);


--
-- Name: idx_deals_lead; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_deals_lead ON public.deals USING btree (lead_id);


--
-- Name: idx_deals_stage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_deals_stage ON public.deals USING btree (stage);


--
-- Name: idx_deals_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_deals_workspace ON public.deals USING btree (workspace_id);


--
-- Name: idx_documents_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documents_status ON public.documents USING btree (status);


--
-- Name: idx_documents_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documents_workspace ON public.documents USING btree (workspace_id);


--
-- Name: idx_leads_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leads_company ON public.leads USING btree (company_id);


--
-- Name: idx_leads_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leads_created ON public.leads USING btree (created_at DESC);


--
-- Name: idx_leads_expected_purchase; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leads_expected_purchase ON public.leads USING btree (expected_purchase_date);


--
-- Name: idx_leads_priority; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leads_priority ON public.leads USING btree (priority);


--
-- Name: idx_leads_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leads_status ON public.leads USING btree (status);


--
-- Name: idx_leads_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leads_workspace ON public.leads USING btree (workspace_id);


--
-- Name: idx_meetings_related; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meetings_related ON public.meetings USING btree (related_type, related_id);


--
-- Name: idx_meetings_starts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meetings_starts ON public.meetings USING btree (starts_at);


--
-- Name: idx_meetings_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meetings_status ON public.meetings USING btree (status);


--
-- Name: idx_meetings_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meetings_workspace ON public.meetings USING btree (workspace_id);


--
-- Name: idx_messages_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_messages_conversation ON public.messages USING btree (conversation_id, created_at);


--
-- Name: idx_order_items_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_items_order ON public.order_items USING btree (order_id);


--
-- Name: idx_orders_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_company ON public.orders USING btree (company_id);


--
-- Name: idx_orders_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_date ON public.orders USING btree (order_date DESC);


--
-- Name: idx_orders_lead; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_lead ON public.orders USING btree (lead_id);


--
-- Name: idx_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_status ON public.orders USING btree (status);


--
-- Name: idx_orders_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_workspace ON public.orders USING btree (workspace_id);


--
-- Name: idx_products_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_name ON public.products USING btree (name);


--
-- Name: idx_products_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_sku ON public.products USING btree (sku);


--
-- Name: idx_products_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_workspace ON public.products USING btree (workspace_id);


--
-- Name: idx_refresh_tokens_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_tasks_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tasks_status ON public.tasks USING btree (status, due_date);


--
-- Name: idx_tasks_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tasks_workspace ON public.tasks USING btree (workspace_id);


--
-- Name: idx_teams_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_teams_workspace ON public.teams USING btree (workspace_id);


--
-- Name: idx_workspace_memberships_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workspace_memberships_user ON public.workspace_memberships USING btree (user_id);


--
-- Name: ux_articles_workspace_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_articles_workspace_slug ON public.articles USING btree (workspace_id, slug);


--
-- Name: ux_categories_workspace_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_categories_workspace_slug ON public.categories USING btree (workspace_id, slug);


--
-- Name: ux_contacts_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_contacts_email ON public.contacts USING btree (app_id, lower((email)::text)) WHERE (email IS NOT NULL);


--
-- Name: ux_contacts_visitor; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_contacts_visitor ON public.contacts USING btree (app_id, visitor_id) WHERE (visitor_id IS NOT NULL);


--
-- Name: ux_orders_workspace_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_orders_workspace_number ON public.orders USING btree (workspace_id, order_number);


--
-- Name: apps trg_apps_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_apps_workspace BEFORE INSERT ON public.apps FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: apps trg_apps_workspace_lookup; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_apps_workspace_lookup AFTER INSERT OR UPDATE OF workspace_id ON public.apps FOR EACH ROW EXECUTE FUNCTION public.sync_app_workspace_lookup();


--
-- Name: articles trg_articles_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_articles_workspace BEFORE INSERT ON public.articles FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: calls trg_calls_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_calls_workspace BEFORE INSERT ON public.calls FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: categories trg_categories_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_categories_workspace BEFORE INSERT ON public.categories FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: companies trg_companies_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_companies_workspace BEFORE INSERT ON public.companies FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: conversations trg_conversations_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_conversations_workspace BEFORE INSERT ON public.conversations FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: deals trg_deals_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_deals_workspace BEFORE INSERT ON public.deals FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: documents trg_documents_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_documents_workspace BEFORE INSERT ON public.documents FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: leads trg_leads_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_leads_workspace BEFORE INSERT ON public.leads FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: meetings trg_meetings_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_meetings_workspace BEFORE INSERT ON public.meetings FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: orders trg_orders_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_orders_workspace BEFORE INSERT ON public.orders FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: products trg_products_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_products_workspace BEFORE INSERT ON public.products FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: tasks trg_tasks_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tasks_workspace BEFORE INSERT ON public.tasks FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: teams trg_teams_workspace; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_teams_workspace BEFORE INSERT ON public.teams FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();


--
-- Name: app_workspace_lookup app_workspace_lookup_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_workspace_lookup
    ADD CONSTRAINT app_workspace_lookup_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE CASCADE;


--
-- Name: articles articles_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: articles articles_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE SET NULL;


--
-- Name: articles articles_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE RESTRICT;


--
-- Name: categories categories_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE RESTRICT;


--
-- Name: contacts contacts_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contacts
    ADD CONSTRAINT contacts_app_id_fkey FOREIGN KEY (app_id) REFERENCES public.apps(id) ON DELETE CASCADE;


--
-- Name: conversations conversations_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_app_id_fkey FOREIGN KEY (app_id) REFERENCES public.apps(id) ON DELETE CASCADE;


--
-- Name: conversations conversations_assignee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: conversations conversations_contact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_contact_id_fkey FOREIGN KEY (contact_id) REFERENCES public.contacts(id) ON DELETE CASCADE;


--
-- Name: conversations conversations_team_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_team_id_fkey FOREIGN KEY (team_id) REFERENCES public.teams(id) ON DELETE SET NULL;


--
-- Name: deals deals_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deals
    ADD CONSTRAINT deals_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id) ON DELETE SET NULL;


--
-- Name: deals deals_lead_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deals
    ADD CONSTRAINT deals_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE SET NULL;


--
-- Name: documents documents_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: apps fk_apps_workspace; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apps
    ADD CONSTRAINT fk_apps_workspace FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE RESTRICT;


--
-- Name: conversations fk_conversations_workspace; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT fk_conversations_workspace FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE RESTRICT;


--
-- Name: documents fk_documents_workspace; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT fk_documents_workspace FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE RESTRICT;


--
-- Name: teams fk_teams_workspace; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT fk_teams_workspace FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE RESTRICT;


--
-- Name: leads leads_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id) ON DELETE SET NULL;


--
-- Name: messages messages_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.conversations(id) ON DELETE CASCADE;


--
-- Name: order_items order_items_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: order_items order_items_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE SET NULL;


--
-- Name: orders orders_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id) ON DELETE SET NULL;


--
-- Name: orders orders_lead_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE SET NULL;


--
-- Name: refresh_tokens refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: team_members team_members_team_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT team_members_team_id_fkey FOREIGN KEY (team_id) REFERENCES public.teams(id) ON DELETE CASCADE;


--
-- Name: team_members team_members_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT team_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: widget_settings widget_settings_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.widget_settings
    ADD CONSTRAINT widget_settings_app_id_fkey FOREIGN KEY (app_id) REFERENCES public.apps(id) ON DELETE CASCADE;


--
-- Name: workspace_memberships workspace_memberships_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspace_memberships
    ADD CONSTRAINT workspace_memberships_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: workspace_memberships workspace_memberships_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspace_memberships
    ADD CONSTRAINT workspace_memberships_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE CASCADE;


--
-- Name: apps; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.apps ENABLE ROW LEVEL SECURITY;

--
-- Name: articles; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.articles ENABLE ROW LEVEL SECURITY;

--
-- Name: calls; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;

--
-- Name: categories; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;

--
-- Name: companies; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.companies ENABLE ROW LEVEL SECURITY;

--
-- Name: contacts; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;

--
-- Name: content_chunks; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.content_chunks ENABLE ROW LEVEL SECURITY;

--
-- Name: conversations; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;

--
-- Name: deals; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.deals ENABLE ROW LEVEL SECURITY;

--
-- Name: documents; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.documents ENABLE ROW LEVEL SECURITY;

--
-- Name: leads; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.leads ENABLE ROW LEVEL SECURITY;

--
-- Name: meetings; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.meetings ENABLE ROW LEVEL SECURITY;

--
-- Name: messages; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

--
-- Name: order_items; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.order_items ENABLE ROW LEVEL SECURITY;

--
-- Name: orders; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;

--
-- Name: products; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

--
-- Name: tasks; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tasks ENABLE ROW LEVEL SECURITY;

--
-- Name: team_members; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.team_members ENABLE ROW LEVEL SECURITY;

--
-- Name: teams; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.teams ENABLE ROW LEVEL SECURITY;

--
-- Name: apps tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.apps USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: articles tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.articles USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: calls tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.calls USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: categories tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.categories USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: companies tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.companies USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: contacts tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.contacts USING ((EXISTS ( SELECT 1
   FROM public.apps a
  WHERE (((a.id)::text = (contacts.app_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM public.apps a
  WHERE (((a.id)::text = (contacts.app_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text)))));


--
-- Name: content_chunks tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.content_chunks USING (((((source_type)::text = 'DOCUMENT'::text) AND (EXISTS ( SELECT 1
   FROM public.documents d
  WHERE (((d.id)::text = (content_chunks.source_id)::text) AND ((d.workspace_id)::text = (public.app_workspace_id())::text))))) OR (((source_type)::text = 'ARTICLE'::text) AND (EXISTS ( SELECT 1
   FROM public.articles a
  WHERE (((a.id)::text = (content_chunks.source_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text))))))) WITH CHECK (((((source_type)::text = 'DOCUMENT'::text) AND (EXISTS ( SELECT 1
   FROM public.documents d
  WHERE (((d.id)::text = (content_chunks.source_id)::text) AND ((d.workspace_id)::text = (public.app_workspace_id())::text))))) OR (((source_type)::text = 'ARTICLE'::text) AND (EXISTS ( SELECT 1
   FROM public.articles a
  WHERE (((a.id)::text = (content_chunks.source_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text)))))));


--
-- Name: conversations tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.conversations USING ((EXISTS ( SELECT 1
   FROM public.apps a
  WHERE (((a.id)::text = (conversations.app_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM public.apps a
  WHERE (((a.id)::text = (conversations.app_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text)))));


--
-- Name: deals tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.deals USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: documents tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.documents USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: leads tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.leads USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: meetings tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.meetings USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: messages tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.messages USING ((EXISTS ( SELECT 1
   FROM public.conversations c
  WHERE (((c.id)::text = (messages.conversation_id)::text) AND ((c.workspace_id)::text = (public.app_workspace_id())::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM public.conversations c
  WHERE (((c.id)::text = (messages.conversation_id)::text) AND ((c.workspace_id)::text = (public.app_workspace_id())::text)))));


--
-- Name: order_items tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.order_items USING ((EXISTS ( SELECT 1
   FROM public.orders o
  WHERE (((o.id)::text = (order_items.order_id)::text) AND ((o.workspace_id)::text = (public.app_workspace_id())::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM public.orders o
  WHERE (((o.id)::text = (order_items.order_id)::text) AND ((o.workspace_id)::text = (public.app_workspace_id())::text)))));


--
-- Name: orders tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.orders USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: products tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.products USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: tasks tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.tasks USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: team_members tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.team_members USING ((EXISTS ( SELECT 1
   FROM public.teams t
  WHERE (((t.id)::text = (team_members.team_id)::text) AND ((t.workspace_id)::text = (public.app_workspace_id())::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM public.teams t
  WHERE (((t.id)::text = (team_members.team_id)::text) AND ((t.workspace_id)::text = (public.app_workspace_id())::text)))));


--
-- Name: teams tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.teams USING (((workspace_id)::text = (public.app_workspace_id())::text)) WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));


--
-- Name: widget_settings tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON public.widget_settings USING ((EXISTS ( SELECT 1
   FROM public.apps a
  WHERE (((a.id)::text = (widget_settings.app_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM public.apps a
  WHERE (((a.id)::text = (widget_settings.app_id)::text) AND ((a.workspace_id)::text = (public.app_workspace_id())::text)))));


--
-- Name: widget_settings; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.widget_settings ENABLE ROW LEVEL SECURITY;


--
