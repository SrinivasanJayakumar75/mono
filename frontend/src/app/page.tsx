import Link from "next/link";

import {
  ArrowRightIcon,
  BookIcon,
  BotIcon,
  CheckCircleIcon,
  CheckIcon,
  ClockIcon,
  FileIcon,
  GlobeIcon,
  InboxIcon,
  LayersIcon,
  LeadsIcon,
  MessageIcon,
  PlusIcon,
  SearchIcon,
  ShieldIcon,
  SparklesIcon,
  UsersIcon,
  WorkflowIcon,
  ZapIcon,
} from "@/components/icons";
import { MonotronLogo } from "@/components/logo";

const NAV_LINKS = [
  { href: "#product", label: "Product" },
  { href: "#inbox", label: "Inbox" },
  { href: "#knowledge", label: "Knowledge" },
  { href: "#pricing", label: "Pricing" },
  { href: "#faq", label: "FAQ" },
];

const CAPABILITIES = [
  { icon: <InboxIcon className="h-4 w-4" />, label: "One shared inbox" },
  { icon: <SparklesIcon className="h-4 w-4" />, label: "Grounded AI answers" },
  { icon: <MessageIcon className="h-4 w-4" />, label: "Live chat widget" },
  { icon: <LeadsIcon className="h-4 w-4" />, label: "One-click CRM" },
];

const FEATURES = [
  {
    icon: <InboxIcon className="h-4 w-4" />,
    title: "A calmer inbox",
    description:
      "Every widget conversation lands in one queue with owners, teams and unread counts that actually mean something.",
  },
  {
    icon: <SparklesIcon className="h-4 w-4" />,
    title: "Answers with receipts",
    description:
      "Monotron chunks your PDFs, retrieves the right passages and drafts a reply with source citations your team can trust.",
  },
  {
    icon: <MessageIcon className="h-4 w-4" />,
    title: "A widget that feels yours",
    description:
      "Embed a branded messenger on any site. Visitors get instant help; your team takes over the moment a human is needed.",
  },
  {
    icon: <LeadsIcon className="h-4 w-4" />,
    title: "CRM without copy-paste",
    description:
      "Turn a support conversation into a qualified lead in one click, with the customer's context already attached.",
  },
];

const PLATFORM_FEATURES = [
  {
    icon: <LayersIcon className="h-4 w-4" />,
    title: "Multi-tenant from request one",
    description: "Workspaces, roles and teams are isolated by design, not by convention.",
  },
  {
    icon: <ShieldIcon className="h-4 w-4" />,
    title: "Row-level security",
    description: "Every tenant-bound query is enforced in PostgreSQL, down to the row.",
  },
  {
    icon: <ZapIcon className="h-4 w-4" />,
    title: "Realtime by default",
    description: "New messages, assignments and handoffs appear without a refresh.",
  },
  {
    icon: <GlobeIcon className="h-4 w-4" />,
    title: "A REST API for the workspace",
    description: "Automate the inbox, the knowledge base and the CRM from your own stack.",
  },
  {
    icon: <WorkflowIcon className="h-4 w-4" />,
    title: "Human handoff that works",
    description: "Escalate a thread, claim it and resume AI without ever talking over each other.",
  },
  {
    icon: <SearchIcon className="h-4 w-4" />,
    title: "Semantic retrieval",
    description: "pgvector search finds the right passage even when the wording is different.",
  },
];

const CONVERSATIONS = [
  {
    initials: "AR",
    name: "Amara Reyes",
    snippet: "How do I reset my password?",
    time: "2m",
    active: true,
  },
  { initials: "JT", name: "Jonas Tan", snippet: "Can I invite a teammate?", time: "18m" },
  { initials: "MK", name: "Mira Kapoor", snippet: "Where can I download invoices?", time: "1h" },
  { initials: "DL", name: "Diego Lopez", snippet: "The widget is not loading.", time: "3h" },
];

const FAQS = [
  {
    question: "How does the 3-day trial work?",
    answer:
      "Every new workspace starts with full access for three days. No credit card is required. When the trial ends, choose the monthly or yearly plan to keep your inbox, knowledge base and CRM available.",
  },
  {
    question: "What happens after the trial?",
    answer:
      "Your workspace moves to a paywall. Billing and authentication stay reachable so you can subscribe, and your data stays intact. The public widget remains available to visitors while you decide.",
  },
  {
    question: "Can I use my own documents?",
    answer:
      "Yes. Upload PDFs and write knowledge base articles. Monotron extracts, chunks and embeds the content, then cites the exact source passages in AI drafts.",
  },
  {
    question: "Can a human take over from the AI?",
    answer:
      "Any time. Escalate a conversation from the inbox and the widget stops sending automatic AI replies. Claim the thread, reply yourself and resume AI when you are ready.",
  },
  {
    question: "Is my workspace isolated from other customers?",
    answer:
      "Yes. Tenant isolation is enforced with PostgreSQL row-level security. Each tenant request is bound to a workspace and runs as a restricted database role.",
  },
];

export default function LandingPage() {
  return (
    <div className="min-h-screen overflow-x-hidden bg-surface-50">
      <header className="sticky top-0 z-50 border-b border-surface-200/80 bg-surface-50/85 backdrop-blur-xl">
        <nav
          className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-6 px-5 sm:px-8"
          aria-label="Main"
        >
          <MonotronLogo href="/" markClassName="h-8 w-8" wordmarkClassName="text-[15px]" />

          <div className="hidden items-center gap-8 text-[13px] font-medium text-surface-500 lg:flex">
            {NAV_LINKS.map((link) => (
              <a key={link.href} href={link.href} className="transition-colors hover:text-surface-900">
                {link.label}
              </a>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <Link
              href="/login"
              className="inline-flex rounded-lg px-3 py-2 text-[13px] font-medium text-surface-600 transition-colors hover:bg-surface-100 hover:text-surface-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
            >
              Sign in
            </Link>
            <Link
              href="/register"
              className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-brand-900 px-3.5 text-[13px] font-medium text-white shadow-sm transition-colors hover:bg-brand-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-900"
            >
              Start free
              <ArrowRightIcon className="h-3.5 w-3.5" />
            </Link>
          </div>
        </nav>
      </header>

      <main>
        {/* ------------------------------- hero ------------------------------- */}
        <section className="relative overflow-hidden border-b border-surface-200/70">
          <div className="pointer-events-none absolute inset-0 mn-grid" aria-hidden="true" />
          <div className="pointer-events-none absolute inset-x-0 top-0 h-[42rem] mn-hero-glow" aria-hidden="true" />
          <div
            className="pointer-events-none absolute -right-32 top-28 h-80 w-80 rounded-full bg-accent-200/30 blur-3xl"
            aria-hidden="true"
          />
          <div
            className="pointer-events-none absolute -left-32 top-64 h-72 w-72 rounded-full bg-brand-200/40 blur-3xl"
            aria-hidden="true"
          />

          <div className="relative mx-auto max-w-6xl px-5 pb-20 pt-20 sm:px-8 sm:pb-24 sm:pt-28">
            <div className="mx-auto max-w-4xl text-center">
              <span className="inline-flex items-center gap-2 rounded-full border border-surface-200 bg-white/80 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.14em] text-surface-500 shadow-sm backdrop-blur">
                <span className="h-1.5 w-1.5 rounded-full bg-accent-500" />
                AI-native customer support
              </span>

              <h1 className="mn-display mt-7 text-[3.1rem] leading-[0.96] tracking-[-0.06em] sm:text-7xl lg:text-[5.7rem]">
                Support that feels
                <br />
                <span className="font-serif font-normal italic text-accent-700">effortless.</span>
              </h1>

              <p className="mx-auto mt-7 max-w-2xl text-pretty text-[16px] leading-relaxed text-surface-600 sm:text-[17px]">
                Monotron brings your shared inbox, knowledge base, live chat and CRM into one
                quiet workspace. AI drafts grounded answers; your team stays in control.
              </p>

              <div className="mt-9 flex flex-col items-center justify-center gap-2.5 sm:flex-row">
                <Link
                  href="/register"
                  className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-brand-900 px-6 text-[13px] font-medium text-white shadow-[0_10px_30px_-16px_rgba(26,24,21,0.6)] transition-colors hover:bg-brand-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-900 sm:w-auto"
                >
                  Start your 3-day trial
                  <ArrowRightIcon className="h-4 w-4" />
                </Link>
                <a
                  href="#product"
                  className="inline-flex h-11 w-full items-center justify-center rounded-lg border border-surface-300 bg-white/80 px-6 text-[13px] font-medium text-surface-800 shadow-sm backdrop-blur transition-colors hover:border-surface-400 hover:bg-white sm:w-auto"
                >
                  See how it works
                </a>
              </div>

              <p className="mt-5 text-[11.5px] text-surface-500">
                No credit card required · Setup in minutes · Cancel anytime
              </p>
            </div>

            <div className="relative mx-auto mt-16 max-w-5xl sm:mt-20">
              <ProductWindow />

              <div className="absolute -left-6 top-56 hidden w-44 rotate-[-3deg] rounded-xl border border-surface-200 bg-white/95 p-3.5 shadow-lift backdrop-blur xl:block">
                <div className="flex items-center gap-2 text-[10px] font-medium uppercase tracking-[0.12em] text-surface-400">
                  <SparklesIcon className="h-3 w-3 text-accent-600" />
                  AI draft ready
                </div>
                <p className="mt-2 text-[11.5px] leading-relaxed text-surface-700">
                  Reset your password from Settings → Security.
                </p>
                <div className="mt-2 flex items-center gap-1 text-[10px] text-surface-400">
                  <FileIcon className="h-3 w-3" />
                  account-guide.pdf · p.2
                </div>
              </div>

              <div className="absolute -right-4 bottom-16 hidden w-48 rotate-[2deg] rounded-xl border border-surface-200 bg-white/95 p-3.5 shadow-lift backdrop-blur xl:block">
                <div className="flex items-center gap-2 text-[10px] font-medium uppercase tracking-[0.12em] text-surface-400">
                  <CheckCircleIcon className="h-3 w-3 text-emerald-600" />
                  Added to CRM
                </div>
                <p className="mt-2 text-[11.5px] font-medium text-surface-800">Amara Reyes</p>
                <p className="text-[10.5px] text-surface-500">New lead · Website</p>
              </div>
            </div>

            <div className="mt-14 grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-surface-200 bg-surface-200 sm:grid-cols-4">
              {CAPABILITIES.map((item) => (
                <div
                  key={item.label}
                  className="flex items-center justify-center gap-2 bg-surface-50/90 px-3 py-3.5 text-[11.5px] font-medium text-surface-600 backdrop-blur"
                >
                  <span className="text-surface-400">{item.icon}</span>
                  {item.label}
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ----------------------------- product ----------------------------- */}
        <section id="product" className="scroll-mt-20 border-b border-surface-200 py-20 sm:py-24">
          <div className="mx-auto max-w-6xl px-5 sm:px-8">
            <div className="grid gap-10 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:items-end">
              <div>
                <p className="mn-eyebrow">The workspace</p>
                <h2 className="mn-display mt-3 max-w-lg text-4xl sm:text-5xl">
                  One calm place for every conversation.
                </h2>
              </div>
              <p className="max-w-xl text-[15px] leading-relaxed text-surface-600 lg:justify-self-end">
                Most support tools make you choose between speed and context. Monotron keeps the
                whole story in one place: the visitor, the history, the answer, the source and the
                next step.
              </p>
            </div>

            <div className="mt-12 grid gap-px overflow-hidden rounded-2xl border border-surface-200 bg-surface-200 sm:grid-cols-2">
              {FEATURES.map((feature) => (
                <div key={feature.title} className="group bg-white p-7 transition-colors hover:bg-surface-50">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-surface-200 bg-surface-50 text-surface-700 transition-colors group-hover:border-surface-300">
                    {feature.icon}
                  </div>
                  <h3 className="mt-5 text-[15px] font-medium text-surface-900">{feature.title}</h3>
                  <p className="mt-2 text-[13px] leading-relaxed text-surface-600">
                    {feature.description}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ------------------------------ inbox ------------------------------ */}
        <section id="inbox" className="scroll-mt-20 border-b border-surface-200 bg-white py-20 sm:py-24">
          <div className="mx-auto grid max-w-6xl gap-12 px-5 sm:px-8 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)] lg:items-center">
            <div>
              <div className="flex items-center gap-3">
                <span className="font-mono text-[11px] text-surface-400">01</span>
                <p className="mn-eyebrow">Inbox</p>
              </div>
              <h2 className="mn-display mt-4 max-w-md text-4xl sm:text-5xl">
                A queue that stays quiet.
              </h2>
              <p className="mt-5 max-w-md text-[15px] leading-relaxed text-surface-600">
                Assign, prioritize and reply without losing context. Every conversation lands in
                one place, with AI drafts and source citations beside it.
              </p>
              <ul className="mt-7 space-y-3">
                {[
                  "Every widget conversation in one queue",
                  "AI drafts with inline citations",
                  "Escalate to a human without talking over them",
                ].map((item) => (
                  <li key={item} className="flex items-start gap-2.5 text-[13px] text-surface-700">
                    <CheckIcon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-600" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>

            <InboxMockup />
          </div>
        </section>

        {/* ---------------------------- knowledge ---------------------------- */}
        <section id="knowledge" className="scroll-mt-20 border-b border-surface-200 py-20 sm:py-24">
          <div className="mx-auto grid max-w-6xl gap-12 px-5 sm:px-8 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)] lg:items-center">
            <KnowledgeMockup />

            <div className="lg:pl-4">
              <div className="flex items-center gap-3">
                <span className="font-mono text-[11px] text-surface-400">02</span>
                <p className="mn-eyebrow">Knowledge</p>
              </div>
              <h2 className="mn-display mt-4 max-w-md text-4xl sm:text-5xl">
                Answers that show their work.
              </h2>
              <p className="mt-5 max-w-md text-[15px] leading-relaxed text-surface-600">
                Upload handbooks, runbooks and policy docs. Monotron extracts the text, embeds it
                and retrieves the exact passages that support every answer.
              </p>
              <ul className="mt-7 space-y-3">
                {[
                  "Semantic search over pgvector",
                  "Source chips on every AI draft",
                  "Your content stays inside your workspace",
                ].map((item) => (
                  <li key={item} className="flex items-start gap-2.5 text-[13px] text-surface-700">
                    <CheckIcon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-600" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        {/* -------------------------------- crm ------------------------------- */}
        <section id="crm" className="scroll-mt-20 border-b border-surface-200 bg-white py-20 sm:py-24">
          <div className="mx-auto grid max-w-6xl gap-12 px-5 sm:px-8 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)] lg:items-center">
            <div>
              <div className="flex items-center gap-3">
                <span className="font-mono text-[11px] text-surface-400">03</span>
                <p className="mn-eyebrow">CRM</p>
              </div>
              <h2 className="mn-display mt-4 max-w-md text-4xl sm:text-5xl">
                From question to pipeline.
              </h2>
              <p className="mt-5 max-w-md text-[15px] leading-relaxed text-surface-600">
                When a conversation is ready, add the customer to CRM in one click. Name, email,
                location and source arrive with the lead — no copy-paste, no lost context.
              </p>
              <ul className="mt-7 space-y-3">
                {[
                  "Idempotent: click once, get one lead",
                  "Source and status set automatically",
                  "The thread shows “In CRM” on the next visit",
                ].map((item) => (
                  <li key={item} className="flex items-start gap-2.5 text-[13px] text-surface-700">
                    <CheckIcon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-600" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>

            <CrmMockup />
          </div>
        </section>

        {/* ----------------------------- platform ----------------------------- */}
        <section
          id="platform"
          className="relative scroll-mt-20 overflow-hidden bg-surface-950 py-20 text-surface-200 sm:py-24"
        >
          <div className="pointer-events-none absolute inset-0 mn-dark-grid" aria-hidden="true" />
          <div className="pointer-events-none absolute left-1/2 top-0 h-80 w-[42rem] -translate-x-1/2 rounded-full bg-accent-500/10 blur-3xl" aria-hidden="true" />

          <div className="relative mx-auto max-w-6xl px-5 sm:px-8">
            <div className="grid gap-10 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)] lg:items-end">
              <div>
                <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-surface-500">
                  Platform
                </p>
                <h2 className="mn-display mt-3 max-w-lg text-4xl text-surface-50 sm:text-5xl">
                  Quietly powerful underneath.
                </h2>
              </div>
              <p className="max-w-xl text-[15px] leading-relaxed text-surface-400 lg:justify-self-end">
                A support product is only as calm as its foundation. Monotron is built for teams
                that need real isolation, real-time updates and an API for the workspace.
              </p>
            </div>

            <div className="mt-12 grid gap-px overflow-hidden rounded-2xl border border-surface-800 bg-surface-800 sm:grid-cols-2 lg:grid-cols-3">
              {PLATFORM_FEATURES.map((feature) => (
                <div key={feature.title} className="bg-surface-950 p-6 transition-colors hover:bg-surface-900">
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-surface-800 bg-surface-900 text-accent-300">
                    {feature.icon}
                  </div>
                  <h3 className="mt-4 text-[13.5px] font-medium text-surface-100">{feature.title}</h3>
                  <p className="mt-1.5 text-[12.5px] leading-relaxed text-surface-400">
                    {feature.description}
                  </p>
                </div>
              ))}
            </div>

            <div className="mt-12 grid gap-6 border-t border-surface-800 pt-8 sm:grid-cols-3">
              {[
                { value: "3 days", label: "Full access, no card" },
                { value: "1 click", label: "Conversation to CRM lead" },
                { value: "100%", label: "Tenant query isolation" },
              ].map((stat) => (
                <div key={stat.label}>
                  <p className="font-mono text-2xl tracking-[-0.04em] text-surface-50">{stat.value}</p>
                  <p className="mt-1 text-[12px] text-surface-500">{stat.label}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ----------------------------- pricing ----------------------------- */}
        <section id="pricing" className="scroll-mt-20 border-b border-surface-200 py-20 sm:py-24">
          <div className="mx-auto max-w-6xl px-5 sm:px-8">
            <div className="mx-auto max-w-2xl text-center">
              <p className="mn-eyebrow">Pricing</p>
              <h2 className="mn-display mt-3 text-4xl sm:text-5xl">
                Simple pricing that stays calm.
              </h2>
              <p className="mt-4 text-[15px] leading-relaxed text-surface-600">
                Every plan includes the whole product. Start with a three-day trial, then pick the
                rhythm that fits your team.
              </p>
            </div>

            <div className="mx-auto mt-12 grid max-w-4xl gap-4 md:grid-cols-2">
              <PricingCard
                interval="Monthly"
                price="₹399"
                cadence="/ month"
                description="A flexible plan for teams finding their rhythm."
                features={[
                  "Unlimited conversations",
                  "AI answers with citations",
                  "Shared inbox and live widget",
                  "CRM and contacts",
                  "Razorpay checkout",
                ]}
              />
              <PricingCard
                interval="Yearly"
                price="₹3,990"
                cadence="/ year"
                description="Two months free, billed once a year."
                badge="Save ₹798"
                highlighted
                features={[
                  "Everything in Monthly",
                  "Two months free",
                  "One simple yearly invoice",
                  "Same 3-day trial",
                  "Cancel anytime",
                ]}
              />
            </div>

            <p className="mt-6 text-center text-[11.5px] text-surface-500">
              Prices in INR. No credit card required for the trial. Cancel anytime.
            </p>
          </div>
        </section>

        {/* -------------------------------- faq ------------------------------- */}
        <section id="faq" className="scroll-mt-20 border-b border-surface-200 bg-white py-20 sm:py-24">
          <div className="mx-auto grid max-w-6xl gap-12 px-5 sm:px-8 lg:grid-cols-[minmax(0,0.7fr)_minmax(0,1.3fr)]">
            <div>
              <p className="mn-eyebrow">FAQ</p>
              <h2 className="mn-display mt-3 max-w-sm text-4xl sm:text-5xl">
                A few things worth knowing.
              </h2>
              <p className="mt-5 max-w-xs text-[14px] leading-relaxed text-surface-600">
                Still curious? Create a workspace and see the whole thing in action.
              </p>
            </div>

            <div className="divide-y divide-surface-200 border-y border-surface-200">
              {FAQS.map((faq) => (
                <details key={faq.question} className="group py-5">
                  <summary className="flex cursor-pointer list-none items-start justify-between gap-6 text-[14px] font-medium text-surface-900 [&::-webkit-details-marker]:hidden">
                    {faq.question}
                    <PlusIcon className="mt-0.5 h-4 w-4 shrink-0 text-surface-400 transition-transform group-open:rotate-45" />
                  </summary>
                  <p className="mt-3 max-w-2xl pr-8 text-[13px] leading-relaxed text-surface-600">
                    {faq.answer}
                  </p>
                </details>
              ))}
            </div>
          </div>
        </section>

        {/* -------------------------------- cta ------------------------------- */}
        <section className="py-20 sm:py-24">
          <div className="mx-auto max-w-6xl px-5 sm:px-8">
            <div className="relative overflow-hidden rounded-[1.75rem] bg-brand-900 px-6 py-14 text-center shadow-[0_40px_100px_-50px_rgba(26,24,21,0.75)] sm:px-12 sm:py-20">
              <div className="pointer-events-none absolute inset-0 mn-dark-grid" aria-hidden="true" />
              <div className="pointer-events-none absolute left-1/2 top-0 h-64 w-[36rem] -translate-x-1/2 rounded-full bg-accent-400/15 blur-3xl" aria-hidden="true" />
              <div className="relative">
                <MonotronLogo
                  tone="light"
                  markClassName="h-10 w-10"
                  wordmarkClassName="text-[17px]"
                  className="justify-center"
                />
                <h2 className="mn-display mx-auto mt-7 max-w-2xl text-4xl text-surface-50 sm:text-5xl">
                  Ready to make support feel calm?
                </h2>
                <p className="mx-auto mt-4 max-w-lg text-[15px] leading-relaxed text-surface-400">
                  Create a workspace, upload a document and watch Monotron draft its first cited
                  reply.
                </p>
                <div className="mt-8 flex flex-col items-center justify-center gap-2.5 sm:flex-row">
                  <Link
                    href="/register"
                    className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-surface-50 px-6 text-[13px] font-medium text-surface-950 transition-colors hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-surface-50 sm:w-auto"
                  >
                    Start your free trial
                    <ArrowRightIcon className="h-4 w-4" />
                  </Link>
                  <Link
                    href="/login"
                    className="inline-flex h-11 w-full items-center justify-center rounded-lg border border-surface-700 px-6 text-[13px] font-medium text-surface-300 transition-colors hover:border-surface-500 hover:text-surface-50 sm:w-auto"
                  >
                    Sign in
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* ------------------------------- footer ------------------------------ */}
      <footer className="border-t border-surface-200">
        <div className="mx-auto max-w-6xl px-5 py-12 sm:px-8">
          <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
            <div>
              <MonotronLogo href="/" markClassName="h-8 w-8" wordmarkClassName="text-[15px]" />
              <p className="mt-4 max-w-xs text-[12.5px] leading-relaxed text-surface-500">
                AI customer support, minus the busywork. Built for teams that care about the answer.
              </p>
            </div>

            <FooterColumn
              title="Product"
              links={[
                { href: "#product", label: "Overview" },
                { href: "#inbox", label: "Inbox" },
                { href: "#knowledge", label: "Knowledge" },
                { href: "#pricing", label: "Pricing" },
              ]}
            />
            <FooterColumn
              title="Workspace"
              links={[
                { href: "/login", label: "Sign in" },
                { href: "/register", label: "Create account" },
                { href: "/billing", label: "Billing" },
              ]}
            />
            <FooterColumn
              title="Resources"
              links={[
                { href: "#faq", label: "FAQ" },
                { href: "#crm", label: "CRM" },
                { href: "#platform", label: "Platform" },
              ]}
            />
          </div>

          <div className="mt-12 flex flex-col items-center justify-between gap-3 border-t border-surface-200 pt-6 text-[11.5px] text-surface-500 sm:flex-row">
            <p>© {new Date().getFullYear()} Monotron. AI customer support, minus the busywork.</p>
            <p className="flex items-center gap-1.5">
              <ClockIcon className="h-3.5 w-3.5" />
              3-day free trial · No credit card
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}

function ProductWindow() {
  return (
    <div className="overflow-hidden rounded-[1.4rem] border border-surface-200 bg-white shadow-[0_40px_100px_-42px_rgba(26,24,21,0.5)]">
      <div className="flex h-10 items-center gap-2 border-b border-surface-200 bg-surface-50 px-4">
        <span className="h-2 w-2 rounded-full bg-surface-300" />
        <span className="h-2 w-2 rounded-full bg-surface-300" />
        <span className="h-2 w-2 rounded-full bg-surface-300" />
        <span className="ml-2 flex-1 truncate rounded-md border border-surface-200 bg-white px-2.5 py-1 text-center font-mono text-[10px] text-surface-400 sm:mx-auto sm:max-w-[18rem]">
          app.monotron.com/inbox
        </span>
        <span className="hidden text-[10px] text-surface-400 sm:block">⌘ K</span>
      </div>

      <div className="grid min-h-[28rem] sm:grid-cols-[14rem_minmax(0,1fr)]">
        <aside className="hidden border-r border-surface-200 bg-surface-50/70 p-3 sm:block">
          <div className="flex items-center gap-2 px-1">
            <span className="flex h-6 w-6 items-center justify-center rounded-md bg-brand-900 text-surface-50">
              <MonotronLogoMarkMini />
            </span>
            <span className="text-[11.5px] font-semibold tracking-[-0.03em] text-surface-900">
              Monotron
            </span>
          </div>

          <div className="mt-5 space-y-0.5">
            {[
              { icon: <InboxIcon className="h-3.5 w-3.5" />, label: "Inbox", active: true },
              { icon: <LeadsIcon className="h-3.5 w-3.5" />, label: "CRM" },
              { icon: <BookIcon className="h-3.5 w-3.5" />, label: "Knowledge" },
              { icon: <UsersIcon className="h-3.5 w-3.5" />, label: "Contacts" },
            ].map((item) => (
              <div
                key={item.label}
                className={
                  item.active
                    ? "flex items-center gap-2 rounded-md bg-white px-2.5 py-1.5 text-[10.5px] font-medium text-surface-900 shadow-sm ring-1 ring-surface-200"
                    : "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-[10.5px] text-surface-500"
                }
              >
                <span className={item.active ? "text-surface-800" : "text-surface-400"}>{item.icon}</span>
                {item.label}
              </div>
            ))}
          </div>

          <div className="mt-5 rounded-md border border-surface-200 bg-white p-2.5">
            <p className="text-[8.5px] font-medium uppercase tracking-[0.12em] text-surface-400">
              Workspace
            </p>
            <p className="mt-1 text-[10.5px] font-medium text-surface-800">Acme Support</p>
            <div className="mt-2 flex items-center justify-between rounded bg-amber-50 px-1.5 py-1 text-[8.5px] font-medium text-amber-700 ring-1 ring-inset ring-amber-200">
              <span>Trial</span>
              <span>2 days left</span>
            </div>
          </div>
        </aside>

        <div className="flex min-w-0 flex-col">
          <div className="flex h-11 items-center justify-between border-b border-surface-200 px-3.5">
            <div>
              <p className="text-[10.5px] font-medium text-surface-800">Inbox</p>
              <p className="text-[8.5px] text-surface-400">12 open conversations</p>
            </div>
            <div className="flex -space-x-1.5">
              {["AR", "JT", "MK"].map((initials) => (
                <span
                  key={initials}
                  className="flex h-5 w-5 items-center justify-center rounded-full border-2 border-white bg-surface-200 text-[7px] font-semibold text-surface-600"
                >
                  {initials}
                </span>
              ))}
            </div>
          </div>

          <div className="grid min-h-0 flex-1 sm:grid-cols-[13rem_minmax(0,1fr)]">
            <ul className="hidden border-r border-surface-200 sm:block">
              {CONVERSATIONS.map((item) => (
                <li
                  key={item.name}
                  className={
                    item.active
                      ? "border-l-2 border-brand-900 bg-surface-50 px-3 py-2.5"
                      : "border-l-2 border-transparent px-3 py-2.5"
                  }
                >
                  <div className="flex items-center gap-2">
                    <span className="flex h-5 w-5 items-center justify-center rounded-full bg-surface-200 text-[7px] font-semibold text-surface-600">
                      {item.initials}
                    </span>
                    <span className="flex-1 truncate text-[10px] font-medium text-surface-800">
                      {item.name}
                    </span>
                    <span className="text-[8px] text-surface-400">{item.time}</span>
                  </div>
                  <p className="mt-1 truncate pl-7 text-[9px] text-surface-500">{item.snippet}</p>
                </li>
              ))}
            </ul>

            <div className="flex min-w-0 flex-col p-3.5 sm:p-4">
              <div className="flex items-center justify-between border-b border-surface-100 pb-3">
                <div className="flex items-center gap-2">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-surface-200 text-[8px] font-semibold text-surface-600">
                    AR
                  </span>
                  <div>
                    <p className="text-[10px] font-medium text-surface-800">Amara Reyes</p>
                    <p className="text-[8.5px] text-surface-400">Widget · via monotron.ai</p>
                  </div>
                </div>
                <span className="rounded border border-surface-200 px-1.5 py-0.5 text-[8px] font-medium text-surface-500">
                  Open
                </span>
              </div>

              <div className="mt-3 space-y-2">
                <div className="w-fit max-w-[88%] rounded-lg rounded-tl-sm bg-surface-100 px-2.5 py-1.5 text-[9.5px] leading-relaxed text-surface-700">
                  How do I reset my password?
                </div>
                <div className="ml-auto w-fit max-w-[88%] rounded-lg rounded-tr-sm bg-brand-900 px-2.5 py-1.5 text-[9.5px] leading-relaxed text-surface-50">
                  Open Settings, choose Security, then select Reset Password.
                </div>
                <div className="rounded-lg border border-surface-200 bg-surface-50 p-2.5">
                  <div className="flex items-center gap-1.5 text-[8.5px] font-medium uppercase tracking-[0.1em] text-surface-400">
                    <BotIcon className="h-3 w-3" />
                    AI draft · 2 sources
                  </div>
                  <p className="mt-1 text-[9.5px] leading-relaxed text-surface-600">
                    You can reset your password from the Security section in Settings.
                  </p>
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    <span className="rounded border border-surface-200 bg-white px-1 py-0.5 font-mono text-[7.5px] text-surface-500">
                      account-guide.pdf · p.2
                    </span>
                    <span className="rounded border border-surface-200 bg-white px-1 py-0.5 font-mono text-[7.5px] text-surface-500">
                      security-guide.md · §3
                    </span>
                  </div>
                </div>
              </div>

              <div className="mt-auto flex items-center gap-2 pt-3">
                <div className="flex h-7 flex-1 items-center rounded-md border border-surface-200 px-2 text-[8.5px] text-surface-400">
                  Write a reply…
                </div>
                <span className="flex h-7 w-7 items-center justify-center rounded-md bg-brand-900 text-surface-50">
                  <ArrowRightIcon className="h-3 w-3" />
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function InboxMockup() {
  return (
    <div className="relative">
      <div className="overflow-hidden rounded-2xl border border-surface-200 bg-white shadow-lift">
        <div className="flex items-center justify-between border-b border-surface-200 px-4 py-3">
          <div>
            <p className="text-[12px] font-medium text-surface-900">Inbox</p>
            <p className="text-[10px] text-surface-400">Assigned to you · 4 open</p>
          </div>
          <span className="rounded-full bg-accent-50 px-2 py-0.5 text-[9.5px] font-medium text-accent-700 ring-1 ring-inset ring-accent-200">
            AI ready
          </span>
        </div>

        <div className="grid sm:grid-cols-[11rem_minmax(0,1fr)]">
          <ul className="border-b border-surface-200 sm:border-b-0 sm:border-r">
            {CONVERSATIONS.slice(0, 3).map((item) => (
              <li
                key={item.name}
                className={
                  item.active
                    ? "border-l-2 border-brand-900 bg-surface-50 px-3 py-3"
                    : "border-l-2 border-transparent px-3 py-3"
                }
              >
                <div className="flex items-center gap-2">
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-surface-200 text-[7px] font-semibold text-surface-600">
                    {item.initials}
                  </span>
                  <span className="flex-1 truncate text-[10.5px] font-medium text-surface-800">
                    {item.name}
                  </span>
                  <span className="text-[8.5px] text-surface-400">{item.time}</span>
                </div>
                <p className="mt-1 truncate pl-7 text-[9.5px] text-surface-500">{item.snippet}</p>
              </li>
            ))}
          </ul>

          <div className="p-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-surface-200 text-[9px] font-semibold text-surface-600">
                  AR
                </span>
                <div>
                  <p className="text-[11px] font-medium text-surface-900">Amara Reyes</p>
                  <p className="text-[9px] text-surface-400">Widget · 2m ago</p>
                </div>
              </div>
              <span className="rounded border border-surface-200 px-1.5 py-0.5 text-[8.5px] text-surface-500">
                Open
              </span>
            </div>

            <div className="mt-4 space-y-2.5">
              <div className="w-fit max-w-[85%] rounded-xl rounded-tl-sm bg-surface-100 px-3 py-2 text-[10.5px] leading-relaxed text-surface-700">
                How do I reset my password?
              </div>
              <div className="rounded-xl border border-accent-200 bg-accent-50/60 p-3">
                <div className="flex items-center gap-1.5 text-[9px] font-medium uppercase tracking-[0.1em] text-accent-700">
                  <SparklesIcon className="h-3 w-3" />
                  Suggested reply
                </div>
                <p className="mt-1.5 text-[10.5px] leading-relaxed text-surface-700">
                  You can reset your password from Settings → Security, then choose Reset Password.
                </p>
                <div className="mt-2 flex flex-wrap gap-1">
                  <span className="rounded border border-accent-200 bg-white px-1.5 py-0.5 font-mono text-[8px] text-surface-500">
                    account-guide.pdf · p.2
                  </span>
                  <span className="rounded border border-accent-200 bg-white px-1.5 py-0.5 font-mono text-[8px] text-surface-500">
                    security-guide.md · §3
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-4 flex items-center gap-2">
              <div className="flex h-8 flex-1 items-center rounded-lg border border-surface-200 px-2.5 text-[9.5px] text-surface-400">
                Write a reply…
              </div>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-900 text-surface-50">
                <ArrowRightIcon className="h-3.5 w-3.5" />
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="absolute -bottom-5 -right-3 hidden rounded-xl border border-surface-200 bg-white px-3 py-2 shadow-lift sm:block">
        <p className="flex items-center gap-1.5 text-[9px] font-medium uppercase tracking-[0.1em] text-surface-400">
          <CheckCircleIcon className="h-3 w-3 text-emerald-600" />
          Handoff ready
        </p>
        <p className="mt-1 text-[10px] text-surface-600">Claimed by Priya · 4s</p>
      </div>
    </div>
  );
}

function KnowledgeMockup() {
  return (
    <div className="relative">
      <div className="rounded-2xl border border-surface-200 bg-white p-5 shadow-lift sm:p-6">
        <div className="flex items-center gap-2 rounded-xl border border-surface-200 bg-surface-50 px-3.5 py-2.5">
          <SearchIcon className="h-4 w-4 text-surface-400" />
          <span className="text-[12px] text-surface-500">How do I reset my password?</span>
          <span className="ml-auto rounded border border-surface-200 bg-white px-1.5 py-0.5 font-mono text-[9px] text-surface-400">
            ⌘ K
          </span>
        </div>

        <div className="mt-5 rounded-xl border border-surface-200 p-4">
          <div className="flex items-center justify-between">
            <p className="text-[12px] font-medium text-surface-900">Reset a password</p>
            <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[9px] font-medium text-emerald-700 ring-1 ring-inset ring-emerald-200">
              98% match
            </span>
          </div>
          <p className="mt-2 text-[11.5px] leading-relaxed text-surface-600">
            Open Settings, choose Security, then select Reset Password. A reset link is sent to
            the email address on your account.
          </p>
          <div className="mt-3 flex flex-wrap gap-1.5">
            <span className="inline-flex items-center gap-1 rounded border border-surface-200 bg-surface-50 px-2 py-1 font-mono text-[9px] text-surface-500">
              <FileIcon className="h-3 w-3" />
              account-guide.pdf · p.2
            </span>
            <span className="inline-flex items-center gap-1 rounded border border-surface-200 bg-surface-50 px-2 py-1 font-mono text-[9px] text-surface-500">
              <FileIcon className="h-3 w-3" />
              security-guide.md · §3
            </span>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-3 gap-2">
          {[
            { label: "Documents", value: "12" },
            { label: "Chunks", value: "284" },
            { label: "Sources", value: "2" },
          ].map((item) => (
            <div key={item.label} className="rounded-lg border border-surface-200 bg-surface-50 p-2.5">
              <p className="font-mono text-[14px] text-surface-800">{item.value}</p>
              <p className="text-[8.5px] uppercase tracking-[0.1em] text-surface-400">{item.label}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="absolute -right-4 -top-5 hidden rounded-xl border border-surface-200 bg-white px-3 py-2 shadow-lift sm:block">
        <p className="flex items-center gap-1.5 text-[9px] font-medium uppercase tracking-[0.1em] text-surface-400">
          <SparklesIcon className="h-3 w-3 text-accent-600" />
          Grounded answer
        </p>
        <p className="mt-1 text-[10px] text-surface-600">2 sources · 0.91 confidence</p>
      </div>
    </div>
  );
}

function CrmMockup() {
  return (
    <div className="relative">
      <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,0.9fr)]">
        <div className="rounded-2xl border border-surface-200 bg-white p-5 shadow-lift">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-200 text-[12px] font-semibold text-surface-600">
              AR
            </span>
            <div>
              <p className="text-[13px] font-medium text-surface-900">Amara Reyes</p>
              <p className="text-[10.5px] text-surface-500">amara@northstar.co</p>
            </div>
          </div>

          <div className="mt-4 space-y-2 border-t border-surface-100 pt-3 text-[10.5px]">
            <div className="flex items-center justify-between">
              <span className="text-surface-400">Location</span>
              <span className="text-surface-700">Bengaluru, IN</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-surface-400">Channel</span>
              <span className="text-surface-700">Widget</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-surface-400">Conversations</span>
              <span className="text-surface-700">3</span>
            </div>
          </div>

          <div className="mt-4 flex h-9 items-center justify-center gap-1.5 rounded-lg bg-emerald-50 text-[11px] font-medium text-emerald-700 ring-1 ring-inset ring-emerald-200">
            <CheckIcon className="h-3.5 w-3.5" />
            In CRM
          </div>
        </div>

        <div className="rounded-2xl border border-surface-200 bg-surface-50 p-5 shadow-card">
          <div className="flex items-center justify-between">
            <p className="text-[9px] font-medium uppercase tracking-[0.12em] text-surface-400">
              CRM lead
            </p>
            <span className="rounded-full bg-brand-900 px-2 py-0.5 text-[8.5px] font-medium text-surface-50">
              NEW
            </span>
          </div>
          <p className="mt-3 text-[13px] font-medium text-surface-900">Amara Reyes</p>
          <p className="text-[10.5px] text-surface-500">Northstar Co</p>

          <div className="mt-4 space-y-2 border-t border-surface-200 pt-3 text-[10px]">
            <div className="flex items-center justify-between">
              <span className="text-surface-400">Source</span>
              <span className="rounded bg-white px-1.5 py-0.5 font-mono text-[9px] text-surface-600 ring-1 ring-inset ring-surface-200">
                WEBSITE
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-surface-400">Created from</span>
              <span className="text-surface-700">Inbox thread</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-surface-400">Owner</span>
              <span className="text-surface-700">Unassigned</span>
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-dashed border-surface-300 bg-white/60 p-2.5 text-[9.5px] leading-relaxed text-surface-500">
            Name, email, location and source arrived with the lead.
          </div>
        </div>
      </div>

      <div className="absolute -bottom-5 left-4 hidden rounded-xl border border-surface-200 bg-white px-3 py-2 shadow-lift sm:block">
        <p className="flex items-center gap-1.5 text-[9px] font-medium uppercase tracking-[0.1em] text-surface-400">
          <CheckCircleIcon className="h-3 w-3 text-emerald-600" />
          Idempotent
        </p>
        <p className="mt-1 text-[10px] text-surface-600">One contact → one lead</p>
      </div>
    </div>
  );
}

function PricingCard({
  interval,
  price,
  cadence,
  description,
  features,
  badge,
  highlighted = false,
}: {
  interval: string;
  price: string;
  cadence: string;
  description: string;
  features: string[];
  badge?: string;
  highlighted?: boolean;
}) {
  return (
    <div
      className={
        highlighted
          ? "relative rounded-2xl border border-brand-900 bg-brand-900 p-6 text-surface-200 shadow-[0_30px_80px_-45px_rgba(26,24,21,0.8)]"
          : "relative rounded-2xl border border-surface-200 bg-white p-6 shadow-card"
      }
    >
      {badge ? (
        <span className="absolute right-5 top-5 rounded-full bg-accent-400 px-2.5 py-1 text-[9.5px] font-semibold uppercase tracking-[0.08em] text-brand-950">
          {badge}
        </span>
      ) : null}

      <p className={highlighted ? "text-[11px] font-medium uppercase tracking-[0.14em] text-surface-400" : "mn-eyebrow"}>
        {interval}
      </p>
      <div className="mt-4 flex items-end gap-1.5">
        <span className={highlighted ? "font-mono text-4xl tracking-[-0.06em] text-surface-50" : "font-mono text-4xl tracking-[-0.06em] text-surface-900"}>
          {price}
        </span>
        <span className={highlighted ? "pb-1 text-[12px] text-surface-400" : "pb-1 text-[12px] text-surface-500"}>
          {cadence}
        </span>
      </div>
      <p className={highlighted ? "mt-2 text-[12.5px] text-surface-400" : "mt-2 text-[12.5px] text-surface-600"}>
        {description}
      </p>

      <ul
        className={
          highlighted
            ? "mt-6 space-y-2.5 border-t border-white/10 pt-5"
            : "mt-6 space-y-2.5 border-t border-surface-200 pt-5"
        }
      >
        {features.map((feature) => (
          <li
            key={feature}
            className={highlighted ? "flex items-start gap-2 text-[12.5px] text-surface-300" : "flex items-start gap-2 text-[12.5px] text-surface-700"}
          >
            <CheckIcon className={highlighted ? "mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-400" : "mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-600"} />
            {feature}
          </li>
        ))}
      </ul>

      <Link
        href="/register"
        className={
          highlighted
            ? "mt-6 inline-flex h-10 w-full items-center justify-center rounded-lg bg-surface-50 text-[12.5px] font-medium text-surface-950 transition-colors hover:bg-white"
            : "mt-6 inline-flex h-10 w-full items-center justify-center rounded-lg border border-surface-300 bg-white text-[12.5px] font-medium text-surface-800 transition-colors hover:border-surface-400 hover:bg-surface-50"
        }
      >
        Start 3-day trial
      </Link>
    </div>
  );
}

function FooterColumn({
  title,
  links,
}: {
  title: string;
  links: { href: string; label: string }[];
}) {
  return (
    <div>
      <p className="text-[10px] font-medium uppercase tracking-[0.14em] text-surface-400">{title}</p>
      <ul className="mt-4 space-y-2.5">
        {links.map((link) => (
          <li key={link.href}>
            <Link href={link.href} className="text-[12.5px] text-surface-600 transition-colors hover:text-surface-900">
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

function MonotronLogoMarkMini() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" aria-hidden="true">
      <path
        d="M5 18V7.2L12 14l7-6.8V18"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="19" cy="5.5" r="1.8" fill="#adbea6" />
    </svg>
  );
}
