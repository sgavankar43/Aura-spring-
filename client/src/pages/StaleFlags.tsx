import {
  AlertTriangle,
  Archive,
  Check,
  CheckCircle2,
  Code2,
  Copy,
  ExternalLink,
  Filter,
  Flame,
  GitBranch,
  GitPullRequest,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  Sparkles,
} from 'lucide-react';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { PageLoader } from '@/components/feedback/PageLoader';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Modal } from '@/components/ui/Modal';
import {
  useArchiveFlag,
  useGenerateCleanupPr,
  useStaleFlags,
  useUnarchiveFlag,
} from '@/hooks/queries';
import { cn } from '@/lib/utils';
import type { FlagCleanupPrResponse, Project, StaleFlagItem } from '@/types/domain';

type StatusFilter = 'ALL' | 'READY_FOR_RETIREMENT' | 'DORMANT_INACTIVE' | 'ACTIVE' | 'ARCHIVED';

export default function StaleFlags() {
  const { project } = useOutletContext<{ project: Project }>();
  const { data: report, isLoading, refetch, isFetching } = useStaleFlags(project.id);

  const archiveMutation = useArchiveFlag(project.id);
  const unarchiveMutation = useUnarchiveFlag(project.id);
  const cleanupPrMutation = useGenerateCleanupPr(project.id);

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [selectedFlagForPr, setSelectedFlagForPr] = useState<StaleFlagItem | null>(null);
  const [cleanupResult, setCleanupResult] = useState<FlagCleanupPrResponse | null>(null);
  const [isCreatingPr, setIsCreatingPr] = useState(false);
  const [copiedDiff, setCopiedDiff] = useState(false);

  if (isLoading) {
    return <PageLoader />;
  }

  const flags = report?.flags || [];

  const filteredFlags = flags.filter((flag) => {
    if (statusFilter === 'ALL') return flag.status !== 'ARCHIVED';
    if (statusFilter === 'READY_FOR_RETIREMENT') return flag.status === 'READY_FOR_RETIREMENT';
    if (statusFilter === 'DORMANT_INACTIVE') return flag.status === 'DORMANT_INACTIVE';
    if (statusFilter === 'ACTIVE') return flag.status === 'HEALTHY_ACTIVE' || flag.status === 'IN_ROLLOUT';
    if (statusFilter === 'ARCHIVED') return flag.status === 'ARCHIVED';
    return true;
  });

  const techDebtScore = report?.techDebtScore ?? 100;
  const scoreColor =
    techDebtScore >= 85 ? 'text-emerald-500' : techDebtScore >= 60 ? 'text-amber-500' : 'text-rose-500';
  const scoreBadgeBg =
    techDebtScore >= 85 ? 'bg-emerald-500/10 border-emerald-500/20' : techDebtScore >= 60 ? 'bg-amber-500/10 border-amber-500/20' : 'bg-rose-500/10 border-rose-500/20';

  const handleOpenCleanupModal = async (flag: StaleFlagItem) => {
    setSelectedFlagForPr(flag);
    setIsCreatingPr(true);
    try {
      const res = await cleanupPrMutation.mutateAsync({ key: flag.key, createOnGithub: false });
      setCleanupResult(res);
    } finally {
      setIsCreatingPr(false);
    }
  };

  const handleCreateRealPr = async () => {
    if (!selectedFlagForPr) return;
    setIsCreatingPr(true);
    try {
      const res = await cleanupPrMutation.mutateAsync({ key: selectedFlagForPr.key, createOnGithub: true });
      setCleanupResult(res);
    } finally {
      setIsCreatingPr(false);
    }
  };

  const copyDiff = () => {
    if (!cleanupResult?.diffPreview) return;
    navigator.clipboard.writeText(cleanupResult.diffPreview);
    setCopiedDiff(true);
    setTimeout(() => setCopiedDiff(false), 2000);
  };

  return (
    <div className="space-y-6" data-testid="stale-flags-page">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold tracking-tight">Tech Debt & Stale Flag Scanner</h2>
            <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
              <Sparkles className="h-3 w-3" /> Phase 8
            </span>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            Automatically detect flags ready for retirement, dormant dead code, and generate cleanup PRs.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => refetch()}
          disabled={isFetching}
          className="gap-1.5 self-start sm:self-auto"
        >
          <RefreshCw className={cn('h-3.5 w-3.5', isFetching && 'animate-spin')} />
          <span>{isFetching ? 'Scanning Code...' : 'Rescan Project'}</span>
        </Button>
      </div>

      {/* KPI Stats Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Score Card */}
        <Card className="border-border/60 shadow-sm relative overflow-hidden">
          <div className="absolute right-3 top-3 opacity-10">
            <ShieldAlert className="h-16 w-16 text-primary" />
          </div>
          <CardHeader className="pb-2">
            <CardDescription className="text-xs uppercase tracking-wider font-semibold">Tech Debt Health</CardDescription>
            <div className="flex items-baseline gap-2">
              <CardTitle className={cn('text-3xl font-extrabold font-mono', scoreColor)}>
                {techDebtScore}
              </CardTitle>
              <span className="text-xs text-muted-foreground font-medium">/ 100</span>
            </div>
          </CardHeader>
          <CardContent className="pt-0">
            <div className={cn('inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-semibold', scoreBadgeBg, scoreColor)}>
              {techDebtScore >= 85 ? '✨ Clean Architecture' : techDebtScore >= 60 ? '⚠️ Moderate Debt' : '🚨 High Technical Debt'}
            </div>
          </CardContent>
        </Card>

        {/* Ready for Retirement */}
        <Card className="border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardDescription className="text-xs uppercase tracking-wider font-semibold">Ready for Retirement</CardDescription>
            <CardTitle className="text-3xl font-extrabold text-amber-500 font-mono">
              {report?.staleCount ?? 0}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <p className="text-xs text-muted-foreground">
              100% enabled everywhere. Conditional checks can safely be removed.
            </p>
          </CardContent>
        </Card>

        {/* Dormant / Inactive */}
        <Card className="border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardDescription className="text-xs uppercase tracking-wider font-semibold">Dormant (Dead Code)</CardDescription>
            <CardTitle className="text-3xl font-extrabold text-rose-500 font-mono">
              {report?.dormantCount ?? 0}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <p className="text-xs text-muted-foreground">
              Disabled in all environments. Check for abandoned features.
            </p>
          </CardContent>
        </Card>

        {/* Active Flags */}
        <Card className="border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardDescription className="text-xs uppercase tracking-wider font-semibold">Active Canary / Toggles</CardDescription>
            <CardTitle className="text-3xl font-extrabold text-emerald-500 font-mono">
              {report?.activeCount ?? 0}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <p className="text-xs text-muted-foreground">
              Undergoing active canary releases or environment staging.
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Filter Tabs */}
      <div className="flex flex-wrap items-center gap-2 border-b border-border/60 pb-3">
        <span className="text-xs font-semibold text-muted-foreground flex items-center gap-1 mr-2">
          <Filter className="h-3.5 w-3.5" /> Filter:
        </span>
        {[
          { key: 'ALL', label: `All Flags (${flags.filter((f) => f.status !== 'ARCHIVED').length})` },
          { key: 'READY_FOR_RETIREMENT', label: `Ready for Retirement (${report?.staleCount ?? 0})` },
          { key: 'DORMANT_INACTIVE', label: `Dormant (${report?.dormantCount ?? 0})` },
          { key: 'ACTIVE', label: `Active (${report?.activeCount ?? 0})` },
          { key: 'ARCHIVED', label: `Archived (${flags.filter((f) => f.status === 'ARCHIVED').length})` },
        ].map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setStatusFilter(tab.key as StatusFilter)}
            className={cn(
              'rounded-md px-3 py-1 text-xs font-medium transition-colors',
              statusFilter === tab.key
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground'
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Flags List */}
      {filteredFlags.length === 0 ? (
        <Card className="p-12 text-center text-muted-foreground">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-muted/60 text-muted-foreground">
            <CheckCircle2 className="h-6 w-6 text-emerald-500" />
          </div>
          <h3 className="mt-4 font-semibold text-foreground">No matching flags found</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            No flags match the current status filter. Your codebase is clean!
          </p>
        </Card>
      ) : (
        <div className="space-y-3">
          {filteredFlags.map((flag) => {
            const isStale = flag.status === 'READY_FOR_RETIREMENT';
            const isDormant = flag.status === 'DORMANT_INACTIVE';
            const isArchived = flag.status === 'ARCHIVED';
            const isCanary = flag.status === 'IN_ROLLOUT';

            return (
              <div
                key={flag.id}
                className={cn(
                  'rounded-xl border p-4 transition-all shadow-sm flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between',
                  isStale
                    ? 'border-amber-500/30 bg-amber-500/5 hover:border-amber-500/50'
                    : isDormant
                    ? 'border-rose-500/30 bg-rose-500/5 hover:border-rose-500/50'
                    : isArchived
                    ? 'border-border/40 bg-muted/20 opacity-70'
                    : 'border-border/60 bg-card hover:border-border'
                )}
                data-testid={`stale-flag-row-${flag.key}`}
              >
                {/* Left info */}
                <div className="min-w-0 flex-1 space-y-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono font-bold text-sm text-foreground bg-muted/80 px-2 py-0.5 rounded border border-border/50">
                      {flag.key}
                    </span>
                    <span className="font-semibold text-sm text-foreground/90">{flag.name}</span>

                    {/* Status Badge */}
                    {isStale ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 border border-amber-500/30 px-2 py-0.5 text-xs font-semibold text-amber-600 dark:text-amber-400">
                        <Flame className="h-3 w-3" /> Ready for Retirement (100%)
                      </span>
                    ) : isDormant ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-rose-500/15 border border-rose-500/30 px-2 py-0.5 text-xs font-semibold text-rose-600 dark:text-rose-400">
                        <AlertTriangle className="h-3 w-3" /> Dormant (0% Everywhere)
                      </span>
                    ) : isCanary ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-purple-500/15 border border-purple-500/30 px-2 py-0.5 text-xs font-semibold text-purple-600 dark:text-purple-400">
                        <GitBranch className="h-3 w-3" /> Progressive Canary Rollout
                      </span>
                    ) : isArchived ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-muted border px-2 py-0.5 text-xs font-semibold text-muted-foreground">
                        <Archive className="h-3 w-3" /> Archived
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/15 border border-emerald-500/30 px-2 py-0.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                        <CheckCircle2 className="h-3 w-3" /> Healthy Active
                      </span>
                    )}
                  </div>

                  <p className="text-xs text-muted-foreground leading-relaxed line-clamp-2">
                    {flag.recommendation}
                  </p>

                  {/* Environments rollout summary */}
                  <div className="flex flex-wrap items-center gap-2 pt-1 text-xs">
                    <span className="text-muted-foreground font-medium">Rollouts:</span>
                    {Object.entries(flag.states).map(([env, isEnabled]) => {
                      const rollout = flag.rolloutPercentages?.[env] ?? 100;
                      return (
                        <span
                          key={env}
                          className={cn(
                            'inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] font-mono',
                            isEnabled
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                              : 'bg-muted text-muted-foreground border border-border/50'
                          )}
                        >
                          {env}: {isEnabled ? `${rollout}%` : 'OFF'}
                        </span>
                      );
                    })}
                  </div>
                </div>

                {/* Right action buttons */}
                <div className="flex flex-wrap items-center gap-2 shrink-0 self-start lg:self-center">
                  {!isArchived ? (
                    <>
                      <Button
                        variant={isStale ? 'default' : 'outline'}
                        size="sm"
                        onClick={() => handleOpenCleanupModal(flag)}
                        className="gap-1.5 text-xs"
                      >
                        <Code2 className="h-3.5 w-3.5" />
                        <span>Cleanup PR</span>
                      </Button>

                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          if (confirm(`Archive feature flag '${flag.key}'? It will be removed from active evaluations.`)) {
                            archiveMutation.mutate(flag.key);
                          }
                        }}
                        disabled={archiveMutation.isPending}
                        className="gap-1 text-xs text-muted-foreground hover:text-destructive hover:border-destructive/40"
                      >
                        <Archive className="h-3.5 w-3.5" />
                        <span>Archive</span>
                      </Button>
                    </>
                  ) : (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => unarchiveMutation.mutate(flag.key)}
                      disabled={unarchiveMutation.isPending}
                      className="gap-1.5 text-xs"
                    >
                      <RotateCcw className="h-3.5 w-3.5" />
                      <span>Restore Flag</span>
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Cleanup PR Refactoring Modal */}
      {selectedFlagForPr ? (
        <Modal
          open={Boolean(selectedFlagForPr)}
          onClose={() => {
            setSelectedFlagForPr(null);
            setCleanupResult(null);
          }}
          title={`Automated Code Cleanup: ${selectedFlagForPr.key}`}
        >
          <div className="space-y-4">
            <p className="text-xs text-muted-foreground">
              Aura generated the automated refactoring diff below to eliminate technical debt by removing the conditional check for this fully graduated flag.
            </p>

            {isCreatingPr ? (
              <div className="py-8">
                <PageLoader />
              </div>
            ) : cleanupResult ? (
              <div className="space-y-3">
                {/* Branch and PR metadata */}
                <div className="rounded-lg border bg-muted/40 p-3 space-y-1 text-xs">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-foreground">Suggested Branch:</span>
                    <code className="bg-background px-1.5 py-0.5 rounded font-mono text-primary font-bold">
                      {cleanupResult.branchName}
                    </code>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-foreground">PR Title:</span>
                    <span className="text-foreground/90 font-medium">{cleanupResult.prTitle}</span>
                  </div>
                </div>

                {/* Diff Preview */}
                <div className="space-y-1">
                  <div className="flex items-center justify-between text-xs font-semibold text-muted-foreground">
                    <span>Generated Git Refactoring Diff:</span>
                    <button
                      type="button"
                      onClick={copyDiff}
                      className="inline-flex items-center gap-1 text-primary hover:underline"
                    >
                      {copiedDiff ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}
                      <span>{copiedDiff ? 'Copied!' : 'Copy Diff'}</span>
                    </button>
                  </div>
                  <pre className="rounded-lg border bg-zinc-950 p-3 text-xs font-mono text-zinc-100 overflow-x-auto max-h-64">
                    {cleanupResult.diffPreview}
                  </pre>
                </div>

                {/* PR URL if created */}
                {cleanupResult.prUrl ? (
                  <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 flex items-center justify-between text-xs text-emerald-600 dark:text-emerald-400">
                    <span className="font-semibold">GitHub Pull Request Created Successfully!</span>
                    <a
                      href={cleanupResult.prUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 font-bold underline"
                    >
                      Open on GitHub <ExternalLink className="h-3 w-3" />
                    </a>
                  </div>
                ) : null}
              </div>
            ) : null}

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-2 pt-3 border-t">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSelectedFlagForPr(null);
                  setCleanupResult(null);
                }}
              >
                Close
              </Button>
              <Button
                variant="default"
                size="sm"
                onClick={handleCreateRealPr}
                disabled={isCreatingPr || Boolean(cleanupResult?.prUrl)}
                className="gap-1.5"
              >
                <GitPullRequest className="h-3.5 w-3.5" />
                <span>{cleanupResult?.prUrl ? 'PR Created' : 'Create GitHub PR'}</span>
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}
