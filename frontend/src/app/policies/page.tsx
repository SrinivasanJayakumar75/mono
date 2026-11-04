import type { Metadata } from "next";
import Link from "next/link";

import { MonotronLogo } from "@/components/logo";

export const metadata: Metadata = {
  title: "Privacy, data & refund policies",
  description:
    "Learn how Monotron handles personal information, customer data, subscriptions and refund requests.",
};

const sections = [
  { id: "privacy", label: "Privacy" },
  { id: "data", label: "Customer data" },
  { id: "refunds", label: "Refunds & billing" },
];

export default function PoliciesPage() {
  return (
    <div className="min-h-screen bg-surface-50">
      <header className="sticky top-0 z-50 border-b border-surface-200/80 bg-surface-50/85 backdrop-blur-xl">
        <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-6 px-5 sm:px-8" aria-label="Main">
          <MonotronLogo href="/" markClassName="h-8 w-8" wordmarkClassName="text-[15px]" />
          <Link href="/" className="text-[13px] font-medium text-surface-600 transition-colors hover:text-surface-900">Back to product</Link>
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-5 py-14 sm:px-8 sm:py-20">
        <div className="max-w-3xl">
          <p className="mn-eyebrow">Policies</p>
          <h1 className="mn-display mt-3 text-4xl tracking-[-0.04em] sm:text-5xl">Privacy, data & refunds</h1>
          <p className="mt-5 text-[14px] leading-6 text-surface-600">
            These policies explain how Monotron handles information and subscriptions. Updated November 4, 2026.
          </p>
          <nav aria-label="Policy sections" className="mt-8 flex flex-wrap gap-2">
            {sections.map((section) => (
              <a key={section.id} href={`#${section.id}`} className="rounded-full border border-surface-200 bg-white px-4 py-2 text-[12px] font-medium text-surface-700 transition-colors hover:border-surface-300 hover:text-surface-900">
                {section.label}
              </a>
            ))}
          </nav>
        </div>

        <div className="mt-10 grid gap-8 lg:grid-cols-[220px_minmax(0,1fr)]">
          <aside className="hidden lg:block">
            <div className="sticky top-24 rounded-xl border border-surface-200 bg-white p-4">
              <p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-surface-400">On this page</p>
              <div className="mt-3 space-y-1">
                {sections.map((section) => (
                  <a key={section.id} href={`#${section.id}`} className="block rounded-md px-2 py-2 text-[12px] text-surface-600 hover:bg-surface-50 hover:text-surface-900">{section.label}</a>
                ))}
              </div>
            </div>
          </aside>

          <div className="space-y-5">
            <PolicySection id="privacy" number="01" title="Privacy policy">
              <p>Monotron (“we”, “us”) provides a customer support workspace for businesses. This policy describes personal information we handle when you visit our site, create an account, or use our service.</p>
              <h3>Information we receive</h3>
              <p>Depending on how you use Monotron, this can include account and workspace details such as your name, email address, authentication details and team membership; billing status and transaction references; and service information such as messages, contacts, uploaded knowledge content, CRM records, settings and support requests.</p>
              <p>Our systems also process technical and usage information needed to provide and protect the service, such as device and browser details, log events and interactions with the product. Our website or service may use essential storage and similar technologies to keep sessions working and remember settings.</p>
              <h3>How we use information</h3>
              <p>We use information to provide and maintain Monotron, authenticate users, process subscriptions, respond to support requests, secure and improve the service, and meet applicable legal obligations. We do not sell personal information.</p>
              <h3>Service providers and international processing</h3>
              <p>We use third-party infrastructure and service providers to operate Monotron, including Polar for subscription payments. These providers process information under their own terms and, where applicable, contractual safeguards. Information may be processed in countries where we or our providers operate, with protections required by applicable law.</p>
              <h3>Your choices and requests</h3>
              <p>You can contact us to request access to, correction of, or deletion of personal information associated with your account, subject to applicable law and legitimate service or record-keeping needs. You can also close your account by contacting us. We may need to verify your identity before acting on a request.</p>
              <h3>Security and changes</h3>
              <p>We use technical and organizational safeguards intended to protect information, but no online service can promise absolute security. We may update this policy as Monotron changes; the revised version will be posted here with its updated date.</p>
            </PolicySection>

            <PolicySection id="data" number="02" title="Customer data policy">
              <p>When a business uses Monotron to manage its customers’ conversations and records, that business decides what information to submit and how to use it. The business is responsible for having an appropriate basis to collect and share that information and for answering its customers’ privacy requests. Monotron processes workspace content to provide the service and follow the workspace administrator’s instructions.</p>
              <h3>How customer data is used</h3>
              <p>Workspace content is used to operate the inbox, knowledge base, chat widget and CRM features selected by the workspace. Knowledge content may be processed to make it searchable and to provide relevant context for AI-assisted drafts. AI suggestions can include or refer to information supplied in the workspace; users should review suggestions before sending them.</p>
              <h3>Access, sharing and safeguards</h3>
              <p>Workspace members can access information according to their workspace roles and settings. We do not make workspace content public, except where a workspace chooses to publish or embed content through a feature. We apply access controls and tenant separation safeguards to help protect workspace information.</p>
              <h3>Retention and deletion</h3>
              <p>Workspace information is kept while the account is active and as needed to provide the service. If an account is closed or an administrator requests deletion, we will take steps to delete or de-identify associated workspace information, subject to technical backup cycles, security needs and legal obligations. Some billing and transaction records may need to be retained for accounting or compliance.</p>
              <h3>Requests from your customers</h3>
              <p>If you are a customer of a business using Monotron, please contact that business first to exercise privacy rights relating to your conversation or records. Workspace administrators can contact us at <a className="font-medium text-accent-700 underline underline-offset-2" href="mailto:monotron@monotron.in">monotron@monotron.in</a> for help with a request.</p>
            </PolicySection>

            <PolicySection id="refunds" number="03" title="Refund & cancellation policy">
              <p>Monotron subscriptions are offered on monthly or yearly billing terms. New workspaces may receive a three-day trial as described on the billing page. A paid subscription is charged in advance for its selected billing period, and payments are processed by Polar.</p>
              <h3>Cancellation</h3>
              <p>A workspace administrator can request cancellation from the billing page. When cancellation is scheduled for the end of a billing period, the subscription remains active until that period ends. Cancellation prevents a future renewal; it does not automatically refund a payment already made.</p>
              <h3>Refund requests</h3>
              <p>Except where applicable law requires otherwise, subscription payments are non-refundable once a billing period has started. If you believe you were charged in error, were charged after a cancellation, or experienced a billing issue, email us at <a className="font-medium text-accent-700 underline underline-offset-2" href="mailto:monotron@monotron.in">monotron@monotron.in</a> with the account email and payment details. We will review the request and, where appropriate, coordinate with Polar to correct or refund an erroneous charge.</p>
              <p>This policy does not limit any rights you may have under applicable consumer protection laws. Any refund approved will generally be returned to the original payment method, subject to payment provider processing times.</p>
            </PolicySection>

            <div className="rounded-xl border border-surface-200 bg-white p-5 text-[13px] leading-6 text-surface-600">
              <p className="font-medium text-surface-900">Questions about these policies?</p>
              <p className="mt-1">Contact <a className="font-medium text-accent-700 underline underline-offset-2" href="mailto:monotron@monotron.in">monotron@monotron.in</a>.</p>
            </div>
          </div>
        </div>
      </main>

      <footer className="border-t border-surface-200">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-5 py-6 text-[11.5px] text-surface-500 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <span>© {new Date().getFullYear()} Monotron</span>
          <Link href="/" className="transition-colors hover:text-surface-900">Back to product</Link>
        </div>
      </footer>
    </div>
  );
}

function PolicySection({ id, number, title, children }: { id: string; number: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="scroll-mt-24 rounded-2xl border border-surface-200 bg-white p-6 sm:p-8">
      <div className="flex items-center gap-3">
        <span className="font-mono text-[11px] text-surface-400">{number}</span>
        <h2 className="mn-display text-2xl sm:text-3xl">{title}</h2>
      </div>
      <div className="policy-copy mt-6 space-y-4 text-[13px] leading-6 text-surface-600">{children}</div>
    </section>
  );
}
