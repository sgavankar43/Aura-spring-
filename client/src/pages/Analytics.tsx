import { useMemo } from 'react';
import { useOutletContext } from 'react-router-dom';
import { BarChart2, Flag, TrendingDown, TrendingUp } from 'lucide-react';
import { useAuditLogs, useFeatures } from '@/hooks/queries';
import type { AuditLogEntry, Project } from '@/types/domain';

/* ── Helpers ────────────────────────────────────────────────── */
function getDayLabel(daysAgo: number): string {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

function getDateKey(dateStr: string): string {
  return new Date(dateStr).toISOString().slice(0, 10);
}

/* ── Mini bar chart (plain SVG, no deps) ─────────────────────── */
function MiniBarChart({ data }: { data: { label: string; count: number }[] }) {
  const max = Math.max(...data.map((d) => d.count), 1);
  return (
    <div className="flex items-end gap-1.5 h-24">
      {data.map((d, i) => {
        const pct = d.count / max;
        return (
          <div key={i} className="flex flex-col items-center gap-1 flex-1 group">
            <div
              className="w-full rounded-t bg-primary/60 hover:bg-primary transition-colors relative"
              style={{ height: `${Math.max(pct * 80, d.count > 0 ? 6 : 2)}px` }}
              title={`${d.label}: ${d.count} events`}
            >
              {d.count > 0 && (
                <span className="absolute -top-5 left-1/2 -translate-x-1/2 text-[9px] font-mono text-muted-foreground opacity-0 group-hover:opacity-100 whitespace-nowrap">
                  {d.count}
                </span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

/* ── Horizontal bar ─────────────────────────────────────────── */
function HBar({ label, count, max }: { label: string; count: number; max: number }) {
  const pct = max > 0 ? (count / max) * 100 : 0;
  return (
    <div className="flex items-center gap-3">
      <code className="text-xs font-mono text-foreground w-32 shrink-0 truncate">{label}</code>
      <div className="flex-1 h-2 rounded-full bg-paper-3 overflow-hidden">
        <div
          className="h-full rounded-full bg-primary/70 transition-[width] duration-500"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-[11px] font-mono text-muted-foreground w-6 text-right">{count}</span>
    </div>
  );
}

/* ── Main ───────────────────────────────────────────────────── */
export default function Analytics() {
  const { project } = useOutletContext<{ project: Project }>();

  // Pull pages 1-3 to get enough data for aggregation (max 60 events)
  const { data: page1 } = useAuditLogs(project.id, 1);
  const { data: page2 } = useAuditLogs(project.id, 2);
  const { data: page3 } = useAuditLogs(project.id, 3);
  const { data: features = [] } = useFeatures(project.id);

  const allEvents: AuditLogEntry[] = useMemo(() => {
    return [
      ...(page1?.data ?? []),
      ...(page2?.data ?? []),
      ...(page3?.data ?? []),
    ];
  }, [page1, page2, page3]);

  /* 7-day chart */
  const dailyCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      counts[d.toISOString().slice(0, 10)] = 0;
    }
    for (const e of allEvents) {
      const key = getDateKey(e.createdAt);
      if (key in counts) counts[key] = (counts[key] ?? 0) + 1;
    }
    return Object.entries(counts).map(([, count], i) => ({
      label: getDayLabel(6 - i),
      count,
    }));
  }, [allEvents]);

  /* Top toggled flags */
  const topFlags = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const e of allEvents) {
      if (e.action === 'FLAG_ENABLED' || e.action === 'FLAG_DISABLED') {
        const key = String(e.metadata?.flagKey ?? e.entityId?.slice(0, 8) ?? e.id);
        counts[key] = (counts[key] ?? 0) + 1;
      }
    }
    return Object.entries(counts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 7);
  }, [allEvents]);

  /* Action breakdown */
  const actionBreakdown = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const e of allEvents) {
      counts[e.action] = (counts[e.action] ?? 0) + 1;
    }
    return Object.entries(counts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 6);
  }, [allEvents]);

  const totalThisWeek = dailyCounts.reduce((n, d) => n + d.count, 0);
  const thisWeek = dailyCounts.slice(3).reduce((n, d) => n + d.count, 0);
  const lastWeek = dailyCounts.slice(0, 3).reduce((n, d) => n + d.count, 0);
  const trend = thisWeek >= lastWeek ? 'up' : 'down';

  return (
    <div className="space-y-6" data-testid="analytics-page">
      {/* Header */}
      <div>
        <h2 className="text-xl font-semibold text-foreground">Analytics</h2>
        <p className="text-sm text-muted-foreground mt-1">
          Activity overview derived from your audit log. Last ~60 events analysed.
        </p>
      </div>

      {/* Summary strip */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="flex items-center gap-2 mb-2">
            <BarChart2 className="h-4 w-4 text-primary" />
            <span className="text-xs text-muted-foreground">Events this week</span>
          </div>
          <p className="text-2xl font-semibold font-mono tabular-nums text-foreground">{totalThisWeek}</p>
          <div className="flex items-center gap-1 mt-1 text-xs text-muted-foreground">
            {trend === 'up' ? (
              <TrendingUp className="h-3.5 w-3.5 text-emerald-500" />
            ) : (
              <TrendingDown className="h-3.5 w-3.5 text-amber-500" />
            )}
            <span>{trend === 'up' ? 'More' : 'Less'} activity than 3 days prior</span>
          </div>
        </div>
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="flex items-center gap-2 mb-2">
            <Flag className="h-4 w-4 text-primary" />
            <span className="text-xs text-muted-foreground">Total flags</span>
          </div>
          <p className="text-2xl font-semibold font-mono tabular-nums text-foreground">{features.length}</p>
        </div>
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="flex items-center gap-2 mb-2">
            <BarChart2 className="h-4 w-4 text-primary" />
            <span className="text-xs text-muted-foreground">Unique event types</span>
          </div>
          <p className="text-2xl font-semibold font-mono tabular-nums text-foreground">
            {actionBreakdown.length}
          </p>
        </div>
      </div>

      {/* 7-day chart */}
      <div className="rounded-xl border border-border bg-card p-6 space-y-4">
        <h3 className="text-sm font-semibold text-foreground">7-day activity</h3>
        {totalThisWeek === 0 ? (
          <p className="text-xs text-muted-foreground py-8 text-center">
            No audit events in the last 7 days.
          </p>
        ) : (
          <>
            <MiniBarChart data={dailyCounts} />
            <div className="flex items-end gap-1.5">
              {dailyCounts.map((d, i) => (
                <div key={i} className="flex-1 text-center text-[9px] text-muted-foreground truncate">
                  {d.label.split(' ')[0]}
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top toggled flags */}
        <div className="rounded-xl border border-border bg-card p-6 space-y-4">
          <h3 className="text-sm font-semibold text-foreground">Most toggled flags</h3>
          {topFlags.length === 0 ? (
            <p className="text-xs text-muted-foreground py-4 text-center">No toggle events yet.</p>
          ) : (
            <div className="space-y-3">
              {topFlags.map(([key, count]) => (
                <HBar key={key} label={key} count={count} max={topFlags[0][1]} />
              ))}
            </div>
          )}
        </div>

        {/* Action type breakdown */}
        <div className="rounded-xl border border-border bg-card p-6 space-y-4">
          <h3 className="text-sm font-semibold text-foreground">Event type breakdown</h3>
          {actionBreakdown.length === 0 ? (
            <p className="text-xs text-muted-foreground py-4 text-center">No events yet.</p>
          ) : (
            <div className="space-y-3">
              {actionBreakdown.map(([action, count]) => (
                <HBar
                  key={action}
                  label={action.replace(/_/g, ' ').toLowerCase()}
                  count={count}
                  max={actionBreakdown[0][1]}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      <p className="text-[11px] text-muted-foreground">
        Analytics are derived client-side from the audit log. Data reflects the last ~60 recorded events.
      </p>
    </div>
  );
}
