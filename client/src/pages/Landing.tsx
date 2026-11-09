import { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Check,
  Copy,
  Cpu,
  GitBranch,
  Layers,
  ShieldCheck,
  Terminal,
  Zap,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ThemeToggle } from '@/components/ui/ThemeToggle';

/* ─── Scroll-reveal hook ─────────────────────────────────────────── */
function useScrollReveal<T extends HTMLElement>(): [React.RefObject<T>, boolean] {
  const ref = useRef<T>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { threshold: 0.12 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return [ref, visible];
}

/* ─── Feature card ───────────────────────────────────────────────── */
function FeatureCard({
  icon,
  title,
  description,
  metric,
  delay,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
  metric: string;
  delay: number;
}) {
  const [cardRef, visible] = useScrollReveal<HTMLDivElement>();

  return (
    <div
      ref={cardRef}
      className={[
        'rounded-xl border border-border bg-card p-6 sm:p-8 flex flex-col justify-between',
        'transition-[opacity,transform] duration-700 ease-out',
        'hover:border-primary/40 hover:shadow-sm group',
        visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-6',
      ].join(' ')}
      style={{ transitionDelay: visible ? `${delay}ms` : '0ms' }}
    >
      <div>
        <div className="h-10 w-10 rounded-lg border border-border bg-paper-2 flex items-center justify-center text-primary mb-4 shadow-sm transition-colors group-hover:bg-accent-soft group-hover:border-primary/30">
          {icon}
        </div>
        <h3 className="text-base font-medium text-foreground">{title}</h3>
        <p className="mt-2 text-sm text-muted-foreground leading-relaxed">{description}</p>
      </div>
      <div className="mt-6 pt-4 border-t border-border text-xs text-muted-foreground font-mono flex items-center justify-between">
        <span>{metric}</span>
        <ArrowRight className="h-3.5 w-3.5 opacity-0 group-hover:opacity-100 transition-opacity text-primary" />
      </div>
    </div>
  );
}

/* ─── Main page ──────────────────────────────────────────────────── */
export default function Landing() {
  const [flags, setFlags] = useState<Record<string, boolean>>({
    'dark-mode': true,
    'ai-copilot-beta': true,
    'fast-checkout-v2': false,
    'rate-limiting': true,
  });

  const [copied, setCopied] = useState(false);
  const [liveTime, setLiveTime] = useState(() => new Date().toISOString().slice(0, 19) + 'Z');
  const [latency, setLatency] = useState('2.4');
  const [evalCount, setEvalCount] = useState(0);

  /* Live workbench ticker */
  useEffect(() => {
    const id = setInterval(() => {
      setLiveTime(new Date().toISOString().slice(0, 19) + 'Z');
      setLatency((1.8 + Math.random() * 1.4).toFixed(1));
      setEvalCount((n) => n + 1);
    }, 2400);
    return () => clearInterval(id);
  }, []);

  /* Hero badge typewriter */
  const fullBadgeText = 'Distributed evaluation engine · v1.0';
  const [badgeText, setBadgeText] = useState('');
  useEffect(() => {
    let i = 0;
    const id = setInterval(() => {
      i += 1;
      setBadgeText(fullBadgeText.slice(0, i));
      if (i >= fullBadgeText.length) clearInterval(id);
    }, 38);
    return () => clearInterval(id);
  }, []);

  const toggleFlag = useCallback((key: string) => {
    setFlags((prev) => ({ ...prev, [key]: !prev[key] }));
  }, []);

  const copyCode = () => {
    navigator.clipboard.writeText(`import { AuraClient } from '@aura/sdk';

const aura = new AuraClient({
  endpoint: 'https://api.aura.dev/api/v1/evaluate',
  apiKey: 'aura_pk_live_demo',
  environment: 'production'
});

await aura.evaluate();
if (aura.isEnabled('ai-copilot-beta')) {
  mountCopilot();
}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  /* Scroll-reveal refs for sections */
  const [codeRef, codeVisible] = useScrollReveal<HTMLDivElement>();
  const [workbenchRef, workbenchVisible] = useScrollReveal<HTMLElement>();

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Top Navigation */}
      <header className="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6 sm:px-8 lg:px-12">
          <div className="flex items-center gap-3">
            <span className="flex h-8 w-8 items-center justify-center rounded-md border border-border bg-paper-2 text-primary shadow-sm">
              <Layers className="h-4 w-4" />
            </span>
            <span className="text-base font-semibold tracking-tight text-foreground">Aura</span>
            <span className="rounded border border-border bg-paper-2 px-2 py-0.5 text-[11px] font-mono text-muted-foreground">
              v1.0
            </span>
          </div>

          <div className="flex items-center gap-3 sm:gap-4">
            {/* Live status pill */}
            <span className="hidden sm:inline-flex items-center gap-1.5 text-[11px] font-mono text-muted-foreground">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 motion-safe:animate-pulse" />
              {latency}ms
            </span>
            <ThemeToggle />
            <Link to="/login">
              <Button variant="ghost" size="sm">
                Sign in
              </Button>
            </Link>
            <Link to="/register">
              <Button size="sm">Create workspace</Button>
            </Link>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <main className="mx-auto max-w-7xl px-6 sm:px-8 lg:px-12 pt-16 pb-28 lg:pt-24 lg:pb-36">
        <div className="mx-auto max-w-4xl text-center flex flex-col items-center">
          {/* Badge with typewriter */}
          <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-border bg-paper-2 px-3.5 py-1 text-xs text-muted-foreground shadow-sm min-w-[16rem] justify-center">
            <span className="h-2 w-2 rounded-full bg-emerald-500 shrink-0 motion-safe:animate-pulse" />
            <span className="font-mono">
              {badgeText}
              <span className="motion-safe:animate-pulse opacity-60">|</span>
            </span>
          </div>

          <h1 className="text-4xl sm:text-5xl md:text-6xl font-medium tracking-tight text-foreground text-balance">
            Feature flags evaluated in under five milliseconds.
          </h1>

          <p className="mt-6 max-w-2xl text-base sm:text-lg text-muted-foreground leading-relaxed text-balance">
            Safely toggle, roll out, and audit features across distributed systems with lightweight
            client-side evaluation and GitHub branch coordination.
          </p>

          <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
            <Link to="/register">
              <Button size="lg" className="gap-2 px-6">
                Get started
                <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
            <Link to="/login">
              <Button variant="secondary" size="lg" className="px-6">
                View dashboard
              </Button>
            </Link>
          </div>
        </div>

        {/* Signature: Interactive Evaluation Workbench */}
        <section
          ref={workbenchRef}
          className={[
            'mt-16 lg:mt-20 overflow-hidden rounded-xl border border-border bg-card shadow-sm',
            'transition-[opacity,transform] duration-700 ease-out',
            workbenchVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8',
          ].join(' ')}
        >
          {/* Workbench Header */}
          <div className="border-b border-border px-6 py-3.5 flex flex-wrap items-center justify-between gap-3 bg-paper-3">
            <div className="flex items-center gap-2.5 text-xs font-mono text-muted-foreground">
              <span className="h-2 w-2 rounded-full bg-emerald-500 inline-block motion-safe:animate-pulse" />
              <span className="font-medium text-foreground">evaluation-workbench.aura</span>
              <span className="text-border">/</span>
              <span>env:production</span>
            </div>
            <div className="flex items-center gap-3">
              <span className="inline-flex items-center gap-1.5 rounded bg-emerald-500/10 px-2 py-0.5 text-[11px] font-mono font-medium text-emerald-600 dark:text-emerald-400">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Live {latency}ms
              </span>
              <code className="text-xs text-muted-foreground font-mono hidden sm:inline-block">
                GET /api/v1/evaluate
              </code>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-border">
            {/* Interactive Flag Controls */}
            <div className="p-6 sm:p-8 lg:col-span-6 flex flex-col justify-between space-y-6">
              <div>
                <div className="flex items-center justify-between mb-5">
                  <h2 className="text-xs font-semibold text-foreground tracking-wide">
                    Remote flag controls
                  </h2>
                  <span className="text-[11px] text-muted-foreground font-mono">
                    eval #{evalCount.toString().padStart(4, '0')}
                  </span>
                </div>

                <div className="space-y-3">
                  {Object.entries(flags).map(([key, enabled]) => (
                    <div
                      key={key}
                      onClick={() => toggleFlag(key)}
                      className="flex items-center justify-between p-4 rounded-lg border border-border bg-paper hover:bg-paper-2 transition-colors cursor-pointer select-none group"
                    >
                      <div className="pr-4">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-mono text-foreground font-medium">{key}</span>
                          <span
                            className={`rounded px-1.5 py-0.5 text-[10px] font-mono transition-colors ${
                              enabled
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                                : 'bg-muted text-muted-foreground'
                            }`}
                          >
                            {enabled ? 'active' : 'inactive'}
                          </span>
                        </div>
                        <div className="text-xs text-muted-foreground mt-1">
                          {enabled
                            ? 'Targeting 100% of incoming client sessions'
                            : 'Disabled across all clusters'}
                        </div>
                      </div>

                      {/* Toggle Switch */}
                      <div
                        className={`h-6 w-11 rounded-full transition-colors relative flex items-center px-0.5 shrink-0 ${
                          enabled
                            ? 'bg-primary justify-end'
                            : 'bg-muted border border-border justify-start'
                        }`}
                      >
                        <div className="h-5 w-5 rounded-full bg-background shadow-md border border-border/50 transition-[box-shadow] group-hover:shadow-lg" />
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="rounded-lg border border-border/60 bg-paper-2 p-3 text-xs text-muted-foreground flex items-center gap-2">
                <Cpu className="h-4 w-4 shrink-0 text-primary" />
                <span>Synchronized via in-memory distributed cache with automated TTL invalidation.</span>
              </div>
            </div>

            {/* Live Evaluated JSON Payload */}
            <div className="p-6 sm:p-8 lg:col-span-6 bg-paper-3 flex flex-col justify-between space-y-6">
              <div>
                <div className="flex items-center justify-between mb-5">
                  <h2 className="text-xs font-semibold text-foreground tracking-wide">
                    Evaluated response payload
                  </h2>
                  <span className="text-xs font-mono text-muted-foreground">
                    200 OK · {latency}ms
                  </span>
                </div>

                <div className="rounded-lg border border-border bg-paper p-5 shadow-inner">
                  <pre className="font-mono text-xs text-foreground leading-relaxed overflow-x-auto">
                    {JSON.stringify(
                      {
                        status: 'success',
                        environment: 'production',
                        evaluatedAt: liveTime,
                        flags,
                      },
                      null,
                      2
                    )}
                  </pre>
                </div>
              </div>

              {/* Edge Telemetry Footer */}
              <div className="grid grid-cols-3 gap-2 pt-2 border-t border-border text-[11px] font-mono text-muted-foreground">
                <div>
                  <span className="block text-muted-foreground/70">Edge region</span>
                  <span className="text-foreground font-medium">iad1-global</span>
                </div>
                <div>
                  <span className="block text-muted-foreground/70">Cache status</span>
                  <span className="text-emerald-600 dark:text-emerald-400 font-medium">
                    HIT (0.3ms)
                  </span>
                </div>
                <div>
                  <span className="block text-muted-foreground/70">Signatures</span>
                  <span className="text-foreground font-medium">Ed25519 verified</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Core Principles */}
        <section className="mt-20 grid grid-cols-1 md:grid-cols-3 gap-6 lg:gap-8">
          <FeatureCard
            icon={<Zap className="h-5 w-5" />}
            title="Sub-millisecond resolution"
            description="Designed for high-throughput microservices and modern web frontends. Evaluates boolean and multivariate flags with zero cold-start delay."
            metric="p99 &lt; 4.8ms worldwide"
            delay={0}
          />
          <FeatureCard
            icon={<GitBranch className="h-5 w-5" />}
            title="GitHub workflow synchronization"
            description="Link pull requests and branch lifecycles directly to flag states. Automatically coordinate rollout toggles when branches are merged."
            metric="native webhook triggers"
            delay={120}
          />
          <FeatureCard
            icon={<ShieldCheck className="h-5 w-5" />}
            title="Immutable audit trail"
            description="Every environment toggle, gradual release percentage, and configuration change is logged with exact actor metadata for complete compliance."
            metric="cryptographic audit chain"
            delay={240}
          />
        </section>

        {/* Integration Code Specimen */}
        <section
          ref={codeRef}
          className={[
            'mt-20 rounded-xl border border-border bg-card overflow-hidden shadow-sm',
            'transition-[opacity,transform] duration-700 ease-out',
            codeVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-6',
          ].join(' ')}
        >
          <div className="border-b border-border px-6 py-3.5 flex items-center justify-between bg-paper-2">
            <div className="flex items-center gap-2 text-xs font-medium text-foreground">
              <Terminal className="h-4 w-4 text-primary" />
              <span>Integration SDK snippet</span>
            </div>
            <button
              type="button"
              onClick={copyCode}
              className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              {copied ? (
                <Check className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
              ) : (
                <Copy className="h-3.5 w-3.5" />
              )}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
          </div>
          <pre className="p-6 bg-paper-3 font-mono text-xs text-foreground overflow-x-auto leading-relaxed">
            <code>{`import { AuraClient } from '@aura/sdk';

const aura = new AuraClient({
  endpoint: 'https://api.aura.dev/api/v1/evaluate',
  apiKey: 'aura_pk_live_demo',
  environment: 'production'
});

await aura.evaluate();
if (aura.isEnabled('ai-copilot-beta')) {
  mountCopilot();
}`}</code>
          </pre>
        </section>
      </main>

      {/* Quiet Footer */}
      <footer className="border-t border-border py-10 bg-paper-3">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 px-6 sm:px-8 lg:px-12 text-xs text-muted-foreground sm:flex-row">
          <p>© {new Date().getFullYear()} Aura Platform. Distributed feature flag infrastructure.</p>
          <div className="flex items-center gap-6">
            <Link to="/login" className="hover:text-foreground transition-colors">
              Sign in
            </Link>
            <Link to="/register" className="hover:text-foreground transition-colors">
              Register
            </Link>
            <span className="inline-flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 motion-safe:animate-pulse" />
              All systems operational
            </span>
          </div>
        </div>
      </footer>
    </div>
  );
}
