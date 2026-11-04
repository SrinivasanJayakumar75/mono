import type { Metadata } from "next";
import Link from "next/link";

import { ArrowRightIcon, CheckIcon, ClockIcon, InboxIcon, LeadsIcon, MessageIcon, SparklesIcon } from "@/components/icons";
import { MonotronLogo } from "@/components/logo";

export const metadata: Metadata = {
  title: "About us",
  description:
    "Meet Monotron: AI customer support with a shared inbox, knowledge-grounded answers, live chat and an inbuilt CRM. Founded in Chennai, India.",
};

const PRODUCT_PILLARS = [
  {
    icon: <SparklesIcon className="h-4 w-4" />,
    title: "AI that knows your business",
    body: "Add your guides, policies and help articles to a knowledge base. Monotron finds relevant information and drafts helpful answers grounded in your content, with sources your team can check.",
  },
  {
    icon: <InboxIcon className="h-4 w-4" />,
    title: "One inbox, with people in control",
    body: "Bring website conversations into a shared team inbox. Assign and reply together, and hand a conversation to a person whenever a customer needs more thoughtful help.",
  },
  {
    icon: <MessageIcon className="h-4 w-4" />,
    title: "A simple website chat",
    body: "Give visitors a direct way to ask questions on your site. The chat widget connects to your knowledge base and your support team, so the whole conversation stays together.",
  },
  {
    icon: <LeadsIcon className="h-4 w-4" />,
    title: "CRM built into support",
    body: "Turn a support conversation into a customer or lead record in one click. Contact details, source and conversation context travel with it, helping your team follow up without copy-paste.",
  },
];

export default function AboutPage() {
  return (
    <div className="min-h-screen overflow-x-hidden bg-surface-50">
      <header className="sticky top-0 z-50 border-b border-surface-200/80 bg-surface-50/85 backdrop-blur-xl">
        <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-6 px-5 sm:px-8" aria-label="Main">
          <MonotronLogo href="/" markClassName="h-8 w-8" wordmarkClassName="text-[15px]" />
          <div className="flex items-center gap-2">
            <Link href="/" className="hidden rounded-lg px-3 py-2 text-[13px] font-medium text-surface-600 transition-colors hover:bg-surface-100 hover:text-surface-900 sm:inline-flex">Product</Link>
            <Link href="/login" className="inline-flex rounded-lg px-3 py-2 text-[13px] font-medium text-surface-600 transition-colors hover:bg-surface-100 hover:text-surface-900">Sign in</Link>
            <Link href="/register" className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-brand-900 px-3.5 text-[13px] font-medium text-white shadow-sm transition-colors hover:bg-brand-800">
              Start free <ArrowRightIcon className="h-3.5 w-3.5" />
            </Link>
          </div>
        </nav>
      </header>

      <main>
        <section className="relative overflow-hidden border-b border-surface-200/70">
          <div className="pointer-events-none absolute inset-0 mn-grid" aria-hidden="true" />
          <div className="pointer-events-none absolute inset-x-0 top-0 h-[42rem] mn-hero-glow" aria-hidden="true" />
          <div className="relative mx-auto max-w-6xl px-5 pb-20 pt-20 sm:px-8 sm:pb-24 sm:pt-28">
            <div className="max-w-3xl">
              <span className="inline-flex items-center gap-2 rounded-full border border-surface-200 bg-white/80 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.14em] text-surface-500 shadow-sm backdrop-blur">
                <span className="h-1.5 w-1.5 rounded-full bg-accent-500" /> About Monotron
              </span>
              <h1 className="mn-display mt-7 text-[3.1rem] leading-[0.98] tracking-[-0.06em] sm:text-7xl">
                Better support starts with <span className="font-serif font-normal italic text-accent-700">being there.</span>
              </h1>
              <p className="mt-7 max-w-2xl text-pretty text-[16px] leading-relaxed text-surface-600 sm:text-[17px]">
                We are building a more connected way for businesses to care for their customers. Monotron brings AI assistance, a human-friendly inbox and customer context together in one workspace.
              </p>
              <div className="mt-8 flex flex-wrap gap-3 text-[12px] text-surface-600">
                <span className="inline-flex items-center gap-2 rounded-lg border border-surface-200 bg-white/80 px-3 py-2"><ClockIcon className="h-3.5 w-3.5 text-accent-700" /> Founded November 4, 2026</span>
                <span className="rounded-lg border border-surface-200 bg-white/80 px-3 py-2">Chennai, Tamil Nadu, India</span>
              </div>
            </div>
          </div>
        </section>

        <section className="border-b border-surface-200 bg-white py-20 sm:py-24">
          <div className="mx-auto grid max-w-6xl gap-12 px-5 sm:px-8 lg:grid-cols-[0.8fr_1.2fr]">
            <div>
              <p className="mn-eyebrow">Why we started</p>
              <h2 className="mn-display mt-3 max-w-md text-4xl sm:text-5xl">Support should feel personal, even as you grow.</h2>
            </div>
            <div className="space-y-5 text-[15px] leading-relaxed text-surface-600">
              <p>Customers do not think in departments. They want a clear answer, a conversation that remembers them, and a real person when they need one. Yet support teams often have to juggle separate tools for chat, help content and customer records.</p>
              <p>Monotron brings those pieces together. AI helps with the repeatable questions using your own business knowledge, while your team can review, respond and take over with the full conversation in view.</p>
              <p>We started Monotron in Chennai, Tamil Nadu, India, on November 4, 2026, with a straightforward idea: useful technology should give teams more room to care, not add another layer of work.</p>
            </div>
          </div>
        </section>

        <section className="border-b border-surface-200 py-20 sm:py-24">
          <div className="mx-auto max-w-6xl px-5 sm:px-8">
            <div className="max-w-2xl">
              <p className="mn-eyebrow">What we are building</p>
              <h2 className="mn-display mt-3 text-4xl sm:text-5xl">AI customer support, with an inbuilt CRM.</h2>
              <p className="mt-5 text-[15px] leading-relaxed text-surface-600">Monotron is a customer support workspace for the complete conversation: from a visitor&apos;s first question to a human response and the follow-up that comes next.</p>
            </div>
            <div className="mt-10 grid gap-px overflow-hidden rounded-2xl border border-surface-200 bg-surface-200 sm:grid-cols-2">
              {PRODUCT_PILLARS.map((item) => (
                <article key={item.title} className="bg-white p-7 sm:p-8">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-surface-200 bg-surface-50 text-accent-700">{item.icon}</div>
                  <h3 className="mt-5 text-[15px] font-medium text-surface-900">{item.title}</h3>
                  <p className="mt-2 text-[13px] leading-relaxed text-surface-600">{item.body}</p>
                </article>
              ))}
            </div>
            <div className="mt-8 flex items-start gap-3 rounded-xl border border-surface-200 bg-white p-5 text-[13px] leading-relaxed text-surface-600">
              <CheckIcon className="mt-0.5 h-4 w-4 shrink-0 text-accent-700" />
              <p>AI suggests answers; your team stays in control. Review drafts, check their sources and step into any conversation when a personal touch matters.</p>
              <p><b>contact us at monotron@monotron.in</b></p>
            </div>
          </div>
        </section>

        <section className="border-b border-surface-200 bg-white py-20 sm:py-24">
          <div className="mx-auto grid max-w-6xl gap-10 px-5 sm:px-8 md:grid-cols-[0.65fr_1.35fr] md:items-center">
            <div className="flex h-32 w-32 items-center justify-center rounded-3xl bg-brand-900 font-serif text-4xl italic text-accent-300 shadow-lift">SJ</div>
            <div>
              <p className="mn-eyebrow">Company leadership</p>
              <h2 className="mn-display mt-3 text-3xl sm:text-4xl">Srinivasan Jayakumar</h2>
              <p className="mt-1 text-[13px] font-medium text-accent-700">Chief Executive Officer</p>
              <p className="mt-4 max-w-2xl text-[14px] leading-relaxed text-surface-600">Srinivasan Jayakumar leads Monotron with a focus on making customer support simpler and more connected. From Chennai, he is building a product that gives growing teams practical AI assistance while keeping human judgment and customer relationships at the center.</p>
              <p className="mt-4 text-[12px] text-surface-500">Monotron was founded on November 4, 2026, in Chennai, Tamil Nadu, India.</p>
            </div>
          </div>
        </section>

        <section className="bg-surface-950 py-16 text-surface-200 sm:py-20">
          <div className="mx-auto flex max-w-6xl flex-col gap-7 px-5 sm:px-8 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-surface-400">Let&apos;s make support feel simpler</p>
              <h2 className="mn-display mt-3 max-w-xl text-3xl text-surface-50 sm:text-4xl">Bring your customer conversations together.</h2>
            </div>
            <Link href="/register" className="inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-lg bg-surface-50 px-5 text-[13px] font-medium text-surface-950 transition-colors hover:bg-white">Try Monotron <ArrowRightIcon className="h-4 w-4" /></Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-surface-200">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 px-5 py-7 text-[11.5px] text-surface-500 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <MonotronLogo href="/" markClassName="h-7 w-7" wordmarkClassName="text-[14px]" />
          <p className="text-[20.5px] text-black"><b>contact us at monotron@monotron.in</b></p>
          <Link href="/" className="transition-colors hover:text-surface-900">Back to product</Link>
        </div>
      </footer>
    </div>
  );
}
