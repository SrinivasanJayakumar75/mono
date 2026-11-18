import type { Metadata } from "next";
import Link from "next/link";

import { MonotronLogo } from "@/components/logo";

export const metadata: Metadata = {
  title: "Privacy, terms, data & refund policies",
  description:
    "Learn how Monotron handles personal information, customer data, omni-channel messaging, service terms, subscriptions and refund requests.",
};

const sections = [
  { id: "privacy", label: "Privacy" },
  { id: "channels", label: "Omni-channel" },
  { id: "terms", label: "Terms" },
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
          <h1 className="mn-display mt-3 text-4xl tracking-[-0.04em] sm:text-5xl">Privacy, terms, data & refunds</h1>
          <p className="mt-5 text-[14px] leading-6 text-surface-600">
            These policies explain how Monotron handles information, connected channels, service terms and subscriptions. Updated November 15, 2026.
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

            <PolicySection id="channels" number="02" title="Omni-channel messaging">
              <p>Monotron can connect a workspace to Gmail, WhatsApp Business and Instagram so customer conversations from those channels appear in the same inbox. A workspace administrator authorizes each connection through Google or Meta OAuth. Platform OAuth credentials are configured on our servers; each workspace’s access and refresh tokens are encrypted at rest and are never returned to the browser.</p>
              <h3>Gmail</h3>
              <p>When Gmail is connected, Monotron uses Google OAuth with read and send permissions. It reads unread messages in the connected mailbox to create inbox conversations, stores the message content, sender details, subject, thread identifiers and message identifiers needed to display and reply, and may mark imported messages as read. When AI auto-reply or a human agent reply is sent, Monotron sends the response through Gmail in the same thread. Monotron does not use Gmail access to read unrelated Google services or to send email on behalf of the workspace except as part of a conversation reply.</p>
              <p>Gmail access can be removed at any time from the workspace’s Channels settings or from the Google Account permissions page. Disconnecting Gmail stops new email import and removes the stored connection tokens.</p>
              <h3>WhatsApp</h3>
              <p>When a WhatsApp Business account is connected, Monotron receives messages and delivery events sent by Meta for the connected business phone number. It processes message content, sender identifiers, contact profile names, timestamps and provider message identifiers so the conversation can appear in the inbox. Replies are sent through the WhatsApp Cloud API to the customer who messaged the business. Meta processes these messages under its own terms and privacy policy.</p>
              <h3>Instagram</h3>
              <p>When an Instagram professional account is connected through Meta&apos;s Instagram API (Unified Login), Monotron receives direct-message events sent by Meta for that account. It processes message text, sender and recipient identifiers, attachments or placeholders, timestamps and provider message identifiers. Replies are sent through the Instagram Messaging API using the connected account. Monotron requests the Instagram basic profile and messaging permissions needed for this purpose and does not use them to access unrelated Instagram content.</p>
              <h3>AI and human replies</h3>
              <p>If a workspace enables AI auto-reply, message content may be processed together with the workspace’s knowledge content to generate a reply, which is sent through the same channel. Workspace members can also read and reply manually. Automated replies and AI suggestions should be reviewed by the workspace where appropriate.</p>
              <h3>Storage, sharing and control</h3>
              <p>Imported messages, contact details and conversation history are stored with the workspace’s other customer data and may be shared with the infrastructure, Google, Meta and AI service providers used to operate Monotron. We do not sell this information. OAuth state values are signed, incoming Meta webhooks are signature-verified, and workspace data is separated using tenant access controls.</p>
              <p>A workspace administrator can disable automation, disconnect a channel, or request deletion. Disconnecting a channel removes its stored connection tokens and stops new messages from being imported for that connection; existing conversation records remain subject to the customer data policy below. Google, Meta and WhatsApp process information under their own terms and privacy policies.</p>
            </PolicySection>

            <PolicySection id="terms" number="03" title="Terms of service">
              <p>These terms govern access to and use of Monotron’s website, workspace and related services. By creating an account, accessing the service, or using it on behalf of a workspace, you agree to these terms. If you use Monotron for an organization, you confirm that you are authorized to accept these terms for that organization.</p>
              <h3>Accounts and workspace responsibility</h3>
              <p>You must provide accurate account information, keep your credentials secure, and ensure that workspace members use the service only as permitted. Workspace administrators are responsible for workspace settings, connected channels, member access and content, and for ensuring that they have the rights, permissions and notices required to connect Gmail, WhatsApp or Instagram accounts and communicate with their customers.</p>
              <h3>Acceptable use</h3>
              <p>You must not use Monotron to violate any law or third-party right; send spam, deceptive, abusive or harmful messages; connect accounts without authorization; access data you do not have permission to access; distribute malware; probe or bypass security or tenant controls; reverse engineer the service; or place an unreasonable load on the service. You must also comply with the terms of Google, Meta, WhatsApp and any other provider used with a connected channel.</p>
              <h3>Customer content and AI output</h3>
              <p>You retain ownership of content you submit to a workspace. You grant us a limited license to host, process, transmit and display that content as needed to provide the service, including generating AI-assisted replies. AI output may be incomplete or inaccurate and is not professional advice. You are responsible for reviewing automated or AI-generated replies before relying on or sending them where review is appropriate.</p>
              <h3>Third-party services</h3>
              <p>Monotron relies on third-party services, including Google for Gmail, Meta for WhatsApp and Instagram, AI providers, and Polar for billing. Those services are subject to their own terms and privacy policies. We are not responsible for third-party services, and a provider may suspend or limit access in ways that affect a connected channel.</p>
              <h3>Fees and cancellation</h3>
              <p>Paid plans, trials, billing periods, cancellation and refunds are described on the billing page and in the refund and cancellation policy below. You are responsible for applicable fees and taxes. We may change prices with notice where required.</p>
              <h3>Availability, suspension and termination</h3>
              <p>We aim to keep the service available but do not guarantee uninterrupted or error-free operation. We may perform maintenance, change features, or suspend or terminate access if these terms are violated, payment is overdue, a security risk is identified, or the law requires it. You may stop using the service at any time and request account closure.</p>
              <h3>Intellectual property</h3>
              <p>Monotron and its software, design, branding and documentation are owned by us or our licensors. These terms do not grant you ownership of the service. You may not copy, modify, distribute or create derivative works from the service except as permitted by law or with our written permission.</p>
              <h3>Disclaimers and liability</h3>
              <p>To the extent permitted by law, the service is provided “as is” and “as available” without warranties of merchantability, fitness for a particular purpose, non-infringement or uninterrupted availability. To the extent permitted by law, we are not liable for indirect, incidental, special, consequential or punitive damages, or for lost profits, revenue, data or goodwill. Our aggregate liability for claims relating to the service will not exceed the amount you paid to Monotron for the service in the twelve months before the event giving rise to the claim. Nothing in these terms excludes rights or liability that cannot be excluded under applicable law.</p>
              <h3>Changes and governing law</h3>
              <p>We may update these terms as the service changes; the revised version will be posted here with a new date. These terms are governed by the laws of India, without regard to conflict-of-law rules, and subject to any mandatory rights available to you. Courts in Chennai, Tamil Nadu, India will have jurisdiction unless applicable law requires otherwise. If any provision is found unenforceable, the remaining provisions will continue in effect.</p>
              <p>Questions about these terms can be sent to <a className="font-medium text-accent-700 underline underline-offset-2" href="mailto:monotron@monotron.in">monotron@monotron.in</a>.</p>
            </PolicySection>

            <PolicySection id="data" number="04" title="Customer data policy">
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

            <PolicySection id="refunds" number="05" title="Refund & cancellation policy">
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
