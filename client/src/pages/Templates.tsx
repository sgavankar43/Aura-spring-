import { useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import {
  ArrowRight,
  BookOpen,
  Copy,
  GitMerge,
  Shield,
  ToggleRight,
  Zap,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useCreateFeature } from '@/hooks/queries';
import type { Project } from '@/types/domain';

interface Template {
  id: string;
  icon: React.ReactNode;
  name: string;
  description: string;
  badge: string;
  badgeColor: string;
  defaultKey: string;
  defaultName: string;
  defaultDescription: string;
  defaultEnabled: boolean;
  guidance: string[];
}

const TEMPLATES: Template[] = [
  {
    id: 'kill-switch',
    icon: <Shield className="h-5 w-5" />,
    name: 'Kill switch',
    description:
      'Instantly disable a feature across all environments. Use for emergency rollbacks or disabling broken functionality.',
    badge: 'Safety',
    badgeColor: 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20',
    defaultKey: 'kill-switch-',
    defaultName: 'Kill switch',
    defaultDescription: 'Emergency disable — set to false to immediately turn off the feature.',
    defaultEnabled: true,
    guidance: [
      'Start enabled (feature is live).',
      'Toggle to false to kill the feature instantly.',
      'No deployment needed — takes effect within seconds.',
    ],
  },
  {
    id: 'ab-test',
    icon: <Copy className="h-5 w-5" />,
    name: 'A/B test',
    description:
      'Run a controlled experiment between two variants. Typically enabled for 50% of users via targeting rules.',
    badge: 'Experiment',
    badgeColor: 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20',
    defaultKey: 'ab-test-',
    defaultName: 'A/B test variant',
    defaultDescription: 'Experiment flag — enable for a percentage of users to test variant B.',
    defaultEnabled: false,
    guidance: [
      'Start disabled.',
      'Enable for a user segment using targeting rules.',
      'Compare conversion metrics between enabled/disabled groups.',
      'Graduate to 100% when results are conclusive.',
    ],
  },
  {
    id: 'gradual-rollout',
    icon: <GitMerge className="h-5 w-5" />,
    name: 'Gradual rollout',
    description:
      'Roll out a feature incrementally: 1% → 10% → 50% → 100%. Allows safe progressive exposure.',
    badge: 'Rollout',
    badgeColor: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20',
    defaultKey: 'rollout-',
    defaultName: 'Gradual rollout',
    defaultDescription: 'Progressive rollout — increase percentage exposure over time.',
    defaultEnabled: false,
    guidance: [
      'Start at 0% (disabled).',
      'Ramp to 1%, monitor errors and latency.',
      'Graduate: 1% → 10% → 50% → 100%.',
      'Archive the flag once at 100% and stable.',
    ],
  },
  {
    id: 'beta-gate',
    icon: <BookOpen className="h-5 w-5" />,
    name: 'Beta feature gate',
    description:
      'Restrict access to a new feature to a specific beta group. Enabled only for allowlisted users or organisations.',
    badge: 'Access control',
    badgeColor: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20',
    defaultKey: 'beta-',
    defaultName: 'Beta gate',
    defaultDescription: 'Beta access — enabled only for approved beta participants.',
    defaultEnabled: false,
    guidance: [
      'Create the flag (disabled by default).',
      'Enable per-user or per-org via targeting.',
      'Gather feedback from beta group.',
      'Transition to gradual rollout when ready.',
    ],
  },
  {
    id: 'maintenance-mode',
    icon: <ToggleRight className="h-5 w-5" />,
    name: 'Maintenance mode',
    description:
      'Show a maintenance banner or disable write operations during scheduled downtime. Toggle instantly without a deploy.',
    badge: 'Operations',
    badgeColor: 'bg-orange-500/10 text-orange-600 dark:text-orange-400 border-orange-500/20',
    defaultKey: 'maintenance-mode',
    defaultName: 'Maintenance mode',
    defaultDescription: 'Operational flag — enable to show maintenance UI and block write paths.',
    defaultEnabled: false,
    guidance: [
      'Keep disabled during normal operation.',
      'Enable before scheduled maintenance windows.',
      'Disable immediately after maintenance completes.',
    ],
  },
  {
    id: 'perf-experiment',
    icon: <Zap className="h-5 w-5" />,
    name: 'Performance experiment',
    description:
      'Gate a performance optimisation behind a flag to measure its real-world impact before full rollout.',
    badge: 'Performance',
    badgeColor: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20',
    defaultKey: 'perf-',
    defaultName: 'Performance experiment',
    defaultDescription: 'Performance flag — test optimised code path on a user subset.',
    defaultEnabled: false,
    guidance: [
      'Enable for a small traffic slice (e.g. 5%).',
      'Measure Core Web Vitals / p99 latency delta.',
      'Roll out or revert based on measured data.',
    ],
  },
];

export default function Templates() {
  const { project } = useOutletContext<{ project: Project }>();
  const createFeature = useCreateFeature(project.id);
  const navigate = useNavigate();
  const [creating, setCreating] = useState<string | null>(null);
  const [error, setError] = useState('');

  const handleUse = async (tpl: Template) => {
    setCreating(tpl.id);
    setError('');
    try {
      const timestamp = Date.now().toString(36);
      await createFeature.mutateAsync({
        key: `${tpl.defaultKey}${timestamp}`,
        name: tpl.defaultName,
        description: tpl.defaultDescription,
        defaultEnabled: tpl.defaultEnabled,
      });
      navigate('..');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create flag');
    } finally {
      setCreating(null);
    }
  };

  return (
    <div className="space-y-6" data-testid="templates-page">
      <div>
        <h2 className="text-xl font-semibold text-foreground">Flag templates</h2>
        <p className="text-sm text-muted-foreground mt-1">
          One-click flag patterns with pre-configured defaults and built-in guidance.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3 text-xs text-destructive">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {TEMPLATES.map((tpl) => (
          <div
            key={tpl.id}
            className="rounded-xl border border-border bg-card p-5 flex flex-col justify-between gap-4 hover:border-primary/40 hover:shadow-sm transition-[border-color,box-shadow] group"
          >
            <div className="space-y-3">
              <div className="flex items-start justify-between gap-3">
                <div className="h-10 w-10 rounded-lg border border-border bg-paper-2 flex items-center justify-center text-primary shrink-0 transition-colors group-hover:bg-accent-soft group-hover:border-primary/30">
                  {tpl.icon}
                </div>
                <span
                  className={`inline-flex items-center rounded border px-2 py-0.5 text-[10px] font-mono font-medium ${tpl.badgeColor}`}
                >
                  {tpl.badge}
                </span>
              </div>

              <div>
                <h3 className="text-sm font-semibold text-foreground">{tpl.name}</h3>
                <p className="mt-1 text-xs text-muted-foreground leading-relaxed">{tpl.description}</p>
              </div>

              <div className="rounded-lg bg-paper-2 border border-border/60 px-3 py-2.5 space-y-1">
                <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide mb-1.5">
                  Guidance
                </p>
                {tpl.guidance.map((step, i) => (
                  <div key={i} className="flex items-start gap-2 text-[11px] text-muted-foreground">
                    <span className="font-mono text-primary shrink-0">{i + 1}.</span>
                    <span>{step}</span>
                  </div>
                ))}
              </div>
            </div>

            <Button
              size="sm"
              className="w-full gap-1.5"
              onClick={() => handleUse(tpl)}
              disabled={creating === tpl.id}
            >
              {creating === tpl.id ? 'Creating…' : 'Use this template'}
              <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </div>
        ))}
      </div>

      <p className="text-[11px] text-muted-foreground">
        Templates create a flag with pre-configured defaults. You can rename, re-key, or adjust settings after creation from the Feature flags page.
      </p>
    </div>
  );
}

