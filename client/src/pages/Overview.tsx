import { useEffect, useMemo, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import {
  Activity,
  ArrowRight,
  Boxes,
  CheckCircle2,
  CircleDot,
  Clock,
  Flag,
  GitBranch,
  GitPullRequest,
  Key,
  ScrollText,
  XCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuditLogs, useFeatures, useGithubPullRequests, useGithubStatus } from '@/hooks/queries';
import { resolveApiUrl } from '@/lib/api';
import type { AuditLogEntry, Environment, Project } from '@/types/domain';

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

function actionLabel(action: string): string {
  const map: Record<string, string> = {
    FLAG_ENABLED: 'Enabled flag',
    FLAG_DISABLED: 'Disabled flag',
    FLAG_CREATED: 'Created flag',
    FLAG_DELETED: 'Deleted flag',
    ENV_CREATED: 'Created environment',
    PROJECT_UPDATED: 'Updated project',
    GITHUB_CONNECTED: 'Connected GitHub',
    GITHUB_DISCONNECTED: 'Disconnected GitHub',
  };
  return map[action] ?? action.replace(/_/g, ' ').toLowerCase();
}

function EnvPill({ env }: { env: Environment }) {
  const colorMap: Record<string, string> = {
    production: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20',
    staging: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20',
    development: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20',
  };
  const cls = colorMap[env.slug] ?? 'bg-muted text-muted-foreground border-border';
  return (
    <div className={`flex items-center gap-2 rounded-lg border px-3 py-2 ${cls}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-80" />
      <span className="text-xs font-medium font-mono">{env.name}</span>
    </div>
  );
}

function AuditRow({ entry }: { entry: AuditLogEntry }) {
  return (
    <div className="flex items-start gap-3 py-3 border-b border-border last:border-0">
      <div className="mt-0.5 h-6 w-6 rounded-full bg-paper-2 border border-border flex items-center justify-center shrink-0">
        <Activity className="h-3 w-3 text-muted-foreground" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs text-foreground font-medium">
          {actionLabel(entry.action)}
          {entry.entity && entry.entity !== 'PROJECT' && entry.entityId ? (
            <span className="text-muted-foreground font-normal">
              {' '}· <code className="font-mono">{entry.entityId.slice(0, 8)}</code>
            </span>
          ) : null}
        </p>
        <p className="text-[11px] text-muted-foreground mt-0.5">
          {entry.userName ?? entry.userEmail ?? 'System'} · {timeAgo(entry.createdAt)}
        </p>
      </div>
    </div>
  );
}

export default function Overview() {
  const { project } = useOutletContext<{ project: Project }>();
  const { data: features = [] } = useFeatures(project.id);
  const { data: auditData } = useAuditLogs(project.id, 1);
  const { data: githubStatus } = useGithubStatus(project.id);
  const { data: pullRequests = [] } = useGithubPullRequests(
    project.id,
    'open',
    Boolean(githubStatus?.connected),
  );

  const environments: Environment[] = project.environments ?? [];
  const streamEnvSlug =
    environments.find((e) => e.slug === 'development')?.slug ??
    environments.find((e) => e.slug === 'staging')?.slug ??
    environments[0]?.slug ??
    null;
  const totalFlags = features.length;

  const enabledCount = features.reduce((n, f) => {
    const vals = Object.values(f.states);
    return n + (vals.some(Boolean) ? 1 : 0);
  }, 0);
  const disabledCount = totalFlags - enabledCount;

  const [liveEvents, setLiveEvents] = useState<AuditLogEntry[]>([]);

  useEffect(() => {
    if (!project?.apiKey || !streamEnvSlug) return;

    const streamUrl = resolveApiUrl(
      `/v1/stream?apiKey=${encodeURIComponent(project.apiKey)}&env=${encodeURIComponent(streamEnvSlug)}`,
    );
    let es: EventSource | null = null;
    try {
      es = new EventSource(streamUrl);
      es.addEventListener('audit_log_created', (e) => {
        try {
          const newEntry: AuditLogEntry = JSON.parse(e.data);
          if (!newEntry?.id) return;
          setLiveEvents((prev) => [newEntry, ...prev.filter((x) => x.id !== newEntry.id)].slice(0, 5));
        } catch {
          // ignore parsing error
        }
      });
    } catch {
      // ignore connection error
    }

    return () => {
      es?.close();
    };
  }, [project?.apiKey, streamEnvSlug]);

  const baseEvents = auditData?.data?.slice(0, 5) ?? [];
  const recentEvents = useMemo(() => {
    const combined = [...liveEvents, ...baseEvents];
    const seen = new Set<string>();
    return combined.filter((item) => {
      if (seen.has(item.id)) return false;
      seen.add(item.id);
      return true;
    }).slice(0, 5);
  }, [liveEvents, baseEvents]);

  const openPRs = pullRequests.filter((pr) => pr.state === 'open');

  return (
    <div className="space-y-6" data-testid="overview-page">
      {/* API key strip */}
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-paper-2 px-5 py-3.5">
        <Key className="h-4 w-4 text-primary shrink-0" />
        <span className="text-xs text-muted-foreground">API key</span>
        <code className="font-mono text-xs text-foreground bg-paper-3 border border-border rounded px-2 py-0.5 select-all">
          {project.apiKey.slice(0, 16)}••••••••••••••••
        </code>
        <span className="text-[11px] text-muted-foreground">
          Created {new Date(project.createdAt).toLocaleDateString()}
        </span>
        <div className="ml-auto">
          <Link to="../api-keys">
            <Button variant="ghost" size="sm" className="gap-1.5 text-xs">
              Manage keys
              <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </Link>
        </div>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="rounded-xl border border-border bg-card p-5 flex items-center gap-4">
          <div className="h-10 w-10 rounded-lg bg-paper-2 border border-border flex items-center justify-center text-primary shrink-0">
            <Flag className="h-5 w-5" />
          </div>
          <div>
            <p className="text-2xl font-semibold font-mono tabular-nums text-foreground">{totalFlags}</p>
            <p className="text-xs text-muted-foreground mt-0.5">Total flags</p>
          </div>
        </div>
        <div className="rounded-xl border border-border bg-card p-5 flex items-center gap-4">
          <div className="h-10 w-10 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center shrink-0">
            <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
          </div>
          <div>
            <p className="text-2xl font-semibold font-mono tabular-nums text-foreground">{enabledCount}</p>
            <p className="text-xs text-muted-foreground mt-0.5">Flags with active state</p>
          </div>
        </div>
        <div className="rounded-xl border border-border bg-card p-5 flex items-center gap-4">
          <div className="h-10 w-10 rounded-lg bg-muted border border-border flex items-center justify-center shrink-0">
            <XCircle className="h-5 w-5 text-muted-foreground" />
          </div>
          <div>
            <p className="text-2xl font-semibold font-mono tabular-nums text-foreground">{disabledCount}</p>
            <p className="text-xs text-muted-foreground mt-0.5">Fully disabled</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Environments */}
        <div className="rounded-xl border border-border bg-card p-5 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Boxes className="h-4 w-4 text-primary" />
              <h2 className="text-sm font-semibold text-foreground">Environments</h2>
            </div>
            <Link to="../environments">
              <Button variant="ghost" size="sm" className="gap-1 text-xs">
                Manage <ArrowRight className="h-3 w-3" />
              </Button>
            </Link>
          </div>
          {environments.length === 0 ? (
            <p className="text-xs text-muted-foreground py-4 text-center">
              No environments yet.{' '}
              <Link to="../environments" className="text-primary hover:underline">
                Create one
              </Link>
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {environments.map((env) => (
                <EnvPill key={env.id} env={env} />
              ))}
            </div>
          )}
        </div>

        {/* GitHub status */}
        <div className="rounded-xl border border-border bg-card p-5 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <GitBranch className="h-4 w-4 text-primary" />
              <h2 className="text-sm font-semibold text-foreground">GitHub</h2>
            </div>
            <Link to="../github">
              <Button variant="ghost" size="sm" className="gap-1 text-xs">
                Configure <ArrowRight className="h-3 w-3" />
              </Button>
            </Link>
          </div>
          {githubStatus?.connected ? (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <span className="h-2 w-2 rounded-full bg-emerald-500 motion-safe:animate-pulse" />
                <span className="text-xs font-mono text-foreground">{githubStatus.repoFullName}</span>
              </div>
              <div className="text-[11px] text-muted-foreground font-mono">
                Default branch: {githubStatus.defaultBranch}
              </div>
              {openPRs.length > 0 && (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <GitPullRequest className="h-3.5 w-3.5 text-primary" />
                  {openPRs.length} open pull request{openPRs.length !== 1 ? 's' : ''}
                </div>
              )}
            </div>
          ) : (
            <div className="flex items-center gap-2 text-xs text-muted-foreground py-2">
              <CircleDot className="h-4 w-4" />
              Not connected.{' '}
              <Link to="../github" className="text-primary hover:underline">
                Connect a repository
              </Link>
            </div>
          )}
        </div>
      </div>

      {/* Recent activity */}
      <div className="rounded-xl border border-border bg-card p-5 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Clock className="h-4 w-4 text-primary" />
            <h2 className="text-sm font-semibold text-foreground">Recent activity</h2>
            <span className="flex items-center gap-1 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 px-2 py-0.5 text-[10px] font-mono">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
              Live SSE Stream
            </span>
          </div>
          <Link to="../audit">
            <Button variant="ghost" size="sm" className="gap-1 text-xs">
              Full log <ArrowRight className="h-3 w-3" />
            </Button>
          </Link>
        </div>
        {recentEvents.length === 0 ? (
          <p className="text-xs text-muted-foreground py-4 text-center">No activity recorded yet.</p>
        ) : (
          <div>
            {recentEvents.map((entry) => (
              <AuditRow key={entry.id} entry={entry} />
            ))}
          </div>
        )}
      </div>

      {/* Quick actions */}
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-paper-2 px-5 py-4">
        <span className="text-xs font-medium text-muted-foreground mr-2">Quick actions</span>
        <Link to="..">
          <Button size="sm" className="gap-1.5">
            <Flag className="h-3.5 w-3.5" />
            Feature flags
          </Button>
        </Link>
        <Link to="../environments">
          <Button variant="secondary" size="sm" className="gap-1.5">
            <Boxes className="h-3.5 w-3.5" />
            Environments
          </Button>
        </Link>
        <Link to="../audit">
          <Button variant="secondary" size="sm" className="gap-1.5">
            <ScrollText className="h-3.5 w-3.5" />
            Audit log
          </Button>
        </Link>
      </div>
    </div>
  );
}

