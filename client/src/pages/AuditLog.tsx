import {
  Activity,
  Archive,
  ChevronLeft,
  ChevronRight,
  Download,
  FileEdit,
  FileText,
  Flag,
  FolderGit2,
  GitPullRequest,
  Layers,
  PlusSquare,
  Zap,
} from 'lucide-react';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { PageLoader } from '@/components/feedback/PageLoader';
import { Button } from '@/components/ui/Button';
import { useAuditLogs } from '@/hooks/queries';
import { resolveApiUrl } from '@/lib/api';
import type { AuditLogEntry, Project } from '@/types/domain';

function getLogIcon(action: string) {
  const norm = action.toUpperCase().replace('.', '_');
  switch (norm) {
    case 'FLAG_TOGGLED':
      return <Zap className="h-5 w-5 text-accent" />;
    case 'FEATURE_CREATED':
      return <Flag className="h-5 w-5 text-success" />;
    case 'ENVIRONMENT_CREATED':
      return <Layers className="h-5 w-5 text-purple-500" />;
    case 'PROJECT_CREATED':
      return <PlusSquare className="h-5 w-5 text-primary" />;
    case 'PROJECT_UPDATED':
      return <FileEdit className="h-5 w-5 text-blue-500" />;
    case 'PROJECT_ARCHIVED':
      return <Archive className="h-5 w-5 text-amber-500" />;
    case 'GITHUB_CONNECTED':
      return <FolderGit2 className="h-5 w-5 text-foreground" />;
    case 'GITHUB_DISCONNECTED':
      return <FolderGit2 className="h-5 w-5 text-muted-foreground" />;
    case 'FEATURE_GITHUB_LINKED':
      return <GitPullRequest className="h-5 w-5 text-indigo-500" />;
    default:
      return <FileEdit className="h-5 w-5 text-muted-foreground" />;
  }
}

function formatMessage(log: AuditLogEntry) {
  const meta = log.metadata ?? {};
  const norm = log.action.toUpperCase().replace('.', '_');

  switch (norm) {
    case 'FLAG_TOGGLED': {
      const enabled = meta['enabled'] as boolean | undefined;
      const featureKey = (meta['featureKey'] ?? meta['key']) as string | undefined;
      const envSlug = meta['envSlug'] as string | undefined;
      return (
        <>
          Toggled <span className="rounded bg-muted px-1 font-mono font-medium">{featureKey ?? '?'}</span> to{' '}
          <span className={`font-medium ${enabled ? 'text-success' : 'text-muted-foreground'}`}>
            {enabled ? 'ON' : 'OFF'}
          </span>
          {envSlug ? <> in <span className="rounded bg-muted px-1 font-mono">{envSlug}</span></> : null}
        </>
      );
    }
    case 'FEATURE_CREATED': {
      const featureKey = (meta['featureKey'] ?? meta['key']) as string | undefined;
      return (
        <>
          Created feature flag{' '}
          <span className="rounded bg-muted px-1 font-mono font-medium">{featureKey ?? '?'}</span>
        </>
      );
    }
    case 'ENVIRONMENT_CREATED': {
      const name = (meta['name'] ?? meta['slug']) as string | undefined;
      return (
        <>
          Created environment{' '}
          <span className="rounded bg-muted px-1 font-mono font-medium">{name ?? '?'}</span>
        </>
      );
    }
    case 'PROJECT_CREATED':
      return <>Created the project</>;
    case 'PROJECT_UPDATED':
      return <>Updated project settings</>;
    case 'PROJECT_ARCHIVED':
      return <span className="text-amber-500">Archived the project</span>;
    case 'GITHUB_CONNECTED': {
      const repo = meta['repo'] as string | undefined;
      return (
        <>
          Connected GitHub repository{' '}
          <span className="rounded bg-muted px-1 font-mono font-medium">{repo ?? ''}</span>
        </>
      );
    }
    case 'GITHUB_DISCONNECTED':
      return <>Disconnected GitHub repository</>;
    case 'FEATURE_GITHUB_LINKED': {
      const featureKey = (meta['featureKey'] ?? meta['key']) as string | undefined;
      const branch = meta['branch'] as string | undefined;
      const prNumber = meta['prNumber'] as number | undefined;
      return (
        <>
          Linked feature <span className="rounded bg-muted px-1 font-mono font-medium">{featureKey ?? '?'}</span> to{' '}
          {branch ? <span className="rounded bg-muted px-1 font-mono font-medium">{branch}</span> : null}
          {prNumber ? <> (PR #{prNumber})</> : null}
        </>
      );
    }
    default:
      return <span className="text-muted-foreground">{log.action}</span>;
  }
}

export default function AuditLog() {
  const { project } = useOutletContext<{ project: Project }>();
  const [page, setPage] = useState(1);
  const { data, isLoading } = useAuditLogs(project.id, page);
  const [exportingCsv, setExportingCsv] = useState(false);
  const [exportingJson, setExportingJson] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const handleExport = async (format: 'csv' | 'json') => {
    const isCsv = format === 'csv';
    if (isCsv) setExportingCsv(true);
    else setExportingJson(true);
    setExportError(null);

    try {
      const token = localStorage.getItem('aura_token');
      const url = resolveApiUrl(`/projects/${project.id}/audit-logs/export?format=${format}`);

      const res = await fetch(url, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });

      if (!res.ok) {
        throw new Error(`Export failed with HTTP ${res.status}`);
      }

      const blob = await res.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = downloadUrl;
      const disposition = res.headers.get('content-disposition');
      let filename = `audit-logs-${project.name.toLowerCase().replace(/\s+/g, '-')}.${format}`;
      if (disposition && disposition.includes('filename=')) {
        const match = disposition.match(/filename="?([^"]+)"?/);
        if (match?.[1]) filename = match[1];
      }
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(downloadUrl);
    } catch (err) {
      setExportError(err instanceof Error ? err.message : 'Export failed');
    } finally {
      if (isCsv) setExportingCsv(false);
      else setExportingJson(false);
    }
  };

  if (isLoading) {
    return <PageLoader />;
  }

  const logs = data?.data ?? [];
  const meta = data?.meta;
  const totalPages = meta?.totalPages ?? 1;

  return (
    <div className="space-y-6" data-testid="audit-log">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Activity className="h-5 w-5 text-primary" />
            Activity history & audit trail
          </h2>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Immutable log of all flag changes, project updates, and environment lifecycle events.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void handleExport('csv')}
            disabled={exportingCsv || logs.length === 0}
            data-testid="export-audit-csv"
            className="gap-1.5"
          >
            <Download className="h-3.5 w-3.5" />
            {exportingCsv ? 'Exporting…' : 'Export CSV'}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void handleExport('json')}
            disabled={exportingJson || logs.length === 0}
            data-testid="export-audit-json"
            className="gap-1.5"
          >
            <FileText className="h-3.5 w-3.5" />
            {exportingJson ? 'Exporting…' : 'Export JSON'}
          </Button>
        </div>
      </div>

      {exportError ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
          {exportError}
        </div>
      ) : null}

      <div className="rounded-xl border bg-card p-6 shadow-sm">
        {logs.length === 0 ? (
          <div className="py-12 text-center text-muted-foreground">No activity recorded yet.</div>
        ) : (
          <>
            <div className="relative space-y-8 before:absolute before:inset-0 before:ml-5 before:h-full before:w-0.5 before:-translate-x-px before:bg-gradient-to-b before:from-transparent before:via-border before:to-transparent md:before:mx-auto md:before:translate-x-0">
              {logs.map((log) => {
                const userLabel = log.userName
                  ? `${log.userName} (${log.userEmail ?? 'user'})`
                  : log.userEmail
                  ? log.userEmail
                  : log.userId
                  ? `User ${log.userId.slice(0, 8)}…`
                  : 'System';

                return (
                  <div
                    key={log.id}
                    className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse"
                    data-testid={`audit-entry-${log.id}`}
                  >
                    <div className="z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full border bg-card shadow transition-colors md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2">
                      {getLogIcon(log.action)}
                    </div>
                    <div className="w-[calc(100%-4rem)] rounded-xl border bg-card/70 p-4 shadow-sm backdrop-blur-sm transition-all hover:bg-card md:w-[calc(50%-2.5rem)]">
                      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-foreground/90">
                          {userLabel}
                        </span>
                        <time className="text-xs text-muted-foreground">
                          {new Date(log.createdAt).toLocaleString()}
                        </time>
                      </div>
                      <div className="text-sm">{formatMessage(log)}</div>
                    </div>
                  </div>
                );
              })}
            </div>

            {meta && totalPages > 1 ? (
              <div className="mt-8 flex items-center justify-between border-t pt-6">
                <p className="text-xs text-muted-foreground">
                  Page {meta.page} of {totalPages} · {meta.total} events
                </p>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={page <= 1}
                    onClick={() => setPage((p) => Math.max(1, p - 1))}
                    data-testid="audit-prev"
                  >
                    <ChevronLeft className="h-4 w-4" />
                    Previous
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={page >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                    data-testid="audit-next"
                  >
                    Next
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}
