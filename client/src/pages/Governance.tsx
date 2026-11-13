import {
  Bot,
  Check,
  Copy,
  Globe,
  Play,
  Plus,
  RefreshCw,
  Send,
  Shield,
  ShieldCheck,
  Trash2,
  Webhook as WebhookIcon,
  X,
} from 'lucide-react';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { PageLoader } from '@/components/feedback/PageLoader';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Modal } from '@/components/ui/Modal';
import {
  useChangeRequests,
  useCreateChangeRequest,
  useCreateWebhook,
  useDeleteWebhook,
  useFeatures,
  useReviewChangeRequest,
  useTestWebhook,
  useToggleEnvironmentApproval,
  useWebhooks,
} from '@/hooks/queries';
import { apiFetch } from '@/lib/api';
import { cn } from '@/lib/utils';
import type { ChangeRequest, Project, WebhookTestResult } from '@/types/domain';

type GovernanceTab = 'approvals' | 'webhooks' | 'mcp' | 'environments';
type CrStatusFilter = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED';

export default function Governance() {
  const { project } = useOutletContext<{ project: Project }>();
  const [activeTab, setActiveTab] = useState<GovernanceTab>('approvals');
  const [crFilter, setCrFilter] = useState<CrStatusFilter>('PENDING');

  // Queries
  const {
    data: changeRequests = [],
    isLoading: loadingCr,
    refetch: refetchCr,
  } = useChangeRequests(project.id, crFilter === 'ALL' ? undefined : crFilter);
  const {
    data: webhooks = [],
    isLoading: loadingWebhooks,
    refetch: refetchWebhooks,
  } = useWebhooks(project.id);
  const { data: features = [] } = useFeatures(project.id);

  // Mutations
  const createCrMutation = useCreateChangeRequest(project.id);
  const reviewCrMutation = useReviewChangeRequest(project.id);
  const createWebhookMutation = useCreateWebhook(project.id);
  const deleteWebhookMutation = useDeleteWebhook(project.id);
  const testWebhookMutation = useTestWebhook(project.id);
  const toggleEnvApprovalMutation = useToggleEnvironmentApproval(project.id);

  // Modal States
  const [crModalOpen, setCrModalOpen] = useState(false);
  const [webhookModalOpen, setWebhookModalOpen] = useState(false);
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [selectedCr, setSelectedCr] = useState<ChangeRequest | null>(null);
  const [reviewComment, setReviewComment] = useState('');
  const [isApproving, setIsApproving] = useState(true);

  // CR Form State
  const [crFeatureKey, setCrFeatureKey] = useState('');
  const [crEnvSlug, setCrEnvSlug] = useState('');
  const [crTargetState, setCrTargetState] = useState(true);
  const [crTargetRollout, setCrTargetRollout] = useState(100);
  const [crReason, setCrReason] = useState('');
  const [crError, setCrError] = useState('');

  // Webhook Form State
  const [whUrl, setWhUrl] = useState('');
  const [whDescription, setWhDescription] = useState('');
  const [whSecret, setWhSecret] = useState('');
  const [whEvents, setWhEvents] = useState<string[]>(['FLAG_CHANGED', 'TARGETING_CHANGED']);
  const [whError, setWhError] = useState('');

  // Webhook Test State
  const [testingWhId, setTestingWhId] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<{ id: string; result: WebhookTestResult } | null>(
    null,
  );

  // MCP Interactive Playground State
  const [mcpTool, setMcpTool] = useState('list_flags');
  const [mcpParams, setMcpParams] = useState('{}');
  const [mcpOutput, setMcpOutput] = useState<string | null>(null);
  const [mcpExecuting, setMcpExecuting] = useState(false);
  const [copiedSnippet, setCopiedSnippet] = useState<string | null>(null);

  // Copy helper
  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSnippet(id);
    setTimeout(() => setCopiedSnippet(null), 2500);
  };

  const handleOpenReview = (cr: ChangeRequest, approve: boolean) => {
    setSelectedCr(cr);
    setIsApproving(approve);
    setReviewComment('');
    setReviewModalOpen(true);
  };

  const handleConfirmReview = async () => {
    if (!selectedCr) return;
    try {
      await reviewCrMutation.mutateAsync({
        changeRequestId: selectedCr.id,
        decision: isApproving ? 'APPROVE' : 'REJECT',
        comment: reviewComment.trim() || undefined,
      });
      setReviewModalOpen(false);
      setSelectedCr(null);
      refetchCr();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Review action failed');
    }
  };

  const handleCreateCr = async (e: React.FormEvent) => {
    e.preventDefault();
    setCrError('');
    if (!crFeatureKey || !crEnvSlug) {
      setCrError('Please select both a feature flag and an environment.');
      return;
    }
    try {
      await createCrMutation.mutateAsync({
        featureKey: crFeatureKey,
        environmentSlug: crEnvSlug,
        targetState: crTargetState,
        targetRollout: crTargetRollout,
        reason: crReason.trim() || undefined,
      });
      setCrModalOpen(false);
      setCrReason('');
      refetchCr();
    } catch (err) {
      setCrError(err instanceof Error ? err.message : 'Failed to submit change request');
    }
  };

  const handleCreateWebhook = async (e: React.FormEvent) => {
    e.preventDefault();
    setWhError('');
    if (!whUrl.trim().startsWith('http')) {
      setWhError('Please enter a valid HTTP or HTTPS webhook URL.');
      return;
    }
    try {
      await createWebhookMutation.mutateAsync({
        url: whUrl.trim(),
        description: whDescription.trim() || undefined,
        secret: whSecret.trim() || undefined,
        events: whEvents.join(','),
      });
      setWebhookModalOpen(false);
      setWhUrl('');
      setWhDescription('');
      setWhSecret('');
      refetchWebhooks();
    } catch (err) {
      setWhError(err instanceof Error ? err.message : 'Failed to create webhook');
    }
  };

  const handleTestWebhook = async (id: string) => {
    setTestingWhId(id);
    try {
      const res = await testWebhookMutation.mutateAsync(id);
      setTestResult({ id, result: res });
      refetchWebhooks();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Webhook ping test failed');
    } finally {
      setTestingWhId(null);
    }
  };

  const handleExecuteMcpTool = async () => {
    setMcpExecuting(true);
    setMcpOutput(null);
    try {
      let parsed = {};
      try {
        parsed = JSON.parse(mcpParams);
      } catch {
        throw new Error('Invalid JSON parameters');
      }

      const res = await apiFetch<any>('/mcp/rpc', {
        method: 'POST',
        body: JSON.stringify({
          jsonrpc: '2.0',
          id: 'test-' + Date.now(),
          method: 'tools/call',
          params: {
            name: mcpTool,
            arguments: parsed,
          },
        }),
      });
      setMcpOutput(JSON.stringify(res, null, 2));
    } catch (err) {
      setMcpOutput(
        JSON.stringify(
          {
            error: err instanceof Error ? err.message : 'MCP execution error',
          },
          null,
          2,
        ),
      );
    } finally {
      setMcpExecuting(false);
    }
  };

  const cursorSnippet = `{
  "mcpServers": {
    "aura": {
      "command": "node",
      "args": [
        "${window.location.origin.includes('localhost') ? '/Users/sahilgavankar/Desktop/MEGA/' : '/workspace/'}packages/aura-mcp/dist/index.js"
      ],
      "env": {
        "AURA_API_URL": "${window.location.origin}",
        "AURA_API_KEY": "${project.apiKey}"
      }
    }
  }
}`;

  const claudeDesktopSnippet = `{
  "mcpServers": {
    "aura-flags": {
      "command": "npx",
      "args": ["-y", "@aura/mcp-server"],
      "env": {
        "AURA_API_URL": "${window.location.origin}",
        "AURA_API_KEY": "${project.apiKey}"
      }
    }
  }
}`;

  const pendingCount = changeRequests.filter((c) => c.status === 'PENDING').length;

  return (
    <div className="space-y-6" data-testid="governance-page">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-6 w-6 text-primary" />
            <h2 className="text-xl font-bold tracking-tight">Enterprise Governance & AI MCP</h2>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            Peer review approval workflows, HMAC-signed outbound webhooks, and Model Context
            Protocol tools for AI coding agents.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {activeTab === 'approvals' && (
            <Button
              type="button"
              onClick={() => {
                setCrFeatureKey(features[0]?.key || '');
                setCrEnvSlug(project.environments?.[0]?.slug || 'production');
                setCrError('');
                setCrModalOpen(true);
              }}
              data-testid="btn-new-cr"
            >
              <Plus className="mr-2 h-4 w-4" />
              New Change Request
            </Button>
          )}

          {activeTab === 'webhooks' && (
            <Button
              type="button"
              onClick={() => {
                setWhError('');
                setWebhookModalOpen(true);
              }}
              data-testid="btn-new-webhook"
            >
              <Plus className="mr-2 h-4 w-4" />
              Add Outbound Webhook
            </Button>
          )}
        </div>
      </div>

      {/* Navigation Sub-Tabs */}
      <div className="flex gap-2 border-b border-border/70 pb-2">
        <button
          type="button"
          onClick={() => setActiveTab('approvals')}
          className={cn(
            'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            activeTab === 'approvals'
              ? 'bg-primary/10 text-primary'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground',
          )}
        >
          <Shield className="h-4 w-4" />
          <span>Four-Eyes Approvals</span>
          {pendingCount > 0 && (
            <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-xs font-semibold text-amber-600 dark:text-amber-400">
              {pendingCount}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('webhooks')}
          className={cn(
            'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            activeTab === 'webhooks'
              ? 'bg-primary/10 text-primary'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground',
          )}
        >
          <WebhookIcon className="h-4 w-4" />
          <span>Outbound Webhooks</span>
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            {webhooks.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('mcp')}
          className={cn(
            'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            activeTab === 'mcp'
              ? 'bg-primary/10 text-primary'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground',
          )}
        >
          <Bot className="h-4 w-4" />
          <span>AI Agent MCP Server</span>
          <span className="rounded-full bg-emerald-500/10 px-2 py-0.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
            7 Tools Live
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('environments')}
          className={cn(
            'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            activeTab === 'environments'
              ? 'bg-primary/10 text-primary'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground',
          )}
        >
          <Globe className="h-4 w-4" />
          <span>Environment Protection</span>
        </button>
      </div>

      {/* TAB 1: Four-Eyes Approvals */}
      {activeTab === 'approvals' && (
        <div className="space-y-6">
          {/* Filter Pills */}
          <div className="flex items-center justify-between">
            <div className="flex gap-2">
              {(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as CrStatusFilter[]).map((f) => (
                <button
                  key={f}
                  type="button"
                  onClick={() => setCrFilter(f)}
                  className={cn(
                    'rounded-md px-3 py-1.5 text-xs font-medium transition-all',
                    crFilter === f
                      ? 'bg-foreground text-background shadow'
                      : 'bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground',
                  )}
                >
                  {f === 'ALL' ? 'All Requests' : f.charAt(0) + f.slice(1).toLowerCase()}
                </button>
              ))}
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={() => refetchCr()}
              title="Refresh change requests"
            >
              <RefreshCw className="h-3.5 w-3.5" />
            </Button>
          </div>

          {loadingCr ? (
            <PageLoader />
          ) : changeRequests.length === 0 ? (
            <Card className="border-dashed p-12 text-center">
              <ShieldCheck className="mx-auto h-12 w-12 text-muted-foreground/40" />
              <h3 className="mt-4 text-base font-semibold">No Change Requests</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                {crFilter === 'PENDING'
                  ? 'All pending flag changes have been reviewed and applied.'
                  : 'No change requests match the current filter.'}
              </p>
              <div className="mt-6">
                <Button
                  onClick={() => {
                    setCrFeatureKey(features[0]?.key || '');
                    setCrEnvSlug(project.environments?.[0]?.slug || 'production');
                    setCrModalOpen(true);
                  }}
                >
                  <Plus className="mr-2 h-4 w-4" />
                  Submit New Request
                </Button>
              </div>
            </Card>
          ) : (
            <div className="grid gap-4">
              {changeRequests.map((cr) => (
                <Card
                  key={cr.id}
                  className={cn(
                    'overflow-hidden transition-all border',
                    cr.status === 'PENDING'
                      ? 'border-amber-500/30 bg-amber-500/[0.02]'
                      : cr.status === 'APPROVED'
                        ? 'border-emerald-500/20'
                        : 'border-border',
                  )}
                >
                  <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1.5">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-sm font-bold text-foreground">
                          {cr.featureKey}
                        </span>
                        <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                          Env: {cr.environmentName || cr.environmentSlug}
                        </span>
                        <span
                          className={cn(
                            'rounded-full px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wider',
                            cr.status === 'PENDING' &&
                              'bg-amber-500/10 text-amber-600 border border-amber-500/20',
                            cr.status === 'APPROVED' &&
                              'bg-emerald-500/10 text-emerald-600 border border-emerald-500/20',
                            cr.status === 'REJECTED' &&
                              'bg-rose-500/10 text-rose-600 border border-rose-500/20',
                          )}
                        >
                          {cr.status}
                        </span>
                      </div>

                      <div className="text-sm text-muted-foreground">
                        Requested state:{' '}
                        <span
                          className={cn(
                            'font-semibold',
                            cr.targetState ? 'text-emerald-500' : 'text-rose-500',
                          )}
                        >
                          {cr.targetState ? 'ENABLED (ON)' : 'DISABLED (OFF)'}
                        </span>{' '}
                        · Rollout:{' '}
                        <span className="font-semibold text-foreground">{cr.targetRollout}%</span>
                      </div>

                      {cr.reason && (
                        <p className="text-xs italic text-muted-foreground bg-muted/40 rounded px-2.5 py-1 max-w-xl">
                          "{cr.reason}"
                        </p>
                      )}

                      <div className="flex items-center gap-3 text-xs text-muted-foreground/75 pt-1">
                        <span>
                          By: <strong className="text-foreground">{cr.requesterName}</strong>
                        </span>
                        <span>•</span>
                        <span>{new Date(cr.createdAt).toLocaleString()}</span>
                        {cr.reviewerName && (
                          <>
                            <span>•</span>
                            <span>
                              Reviewed by:{' '}
                              <strong className="text-foreground">{cr.reviewerName}</strong>
                            </span>
                          </>
                        )}
                      </div>
                    </div>

                    {/* Action buttons for Pending */}
                    {cr.status === 'PENDING' && (
                      <div className="flex items-center gap-2 self-start sm:self-center">
                        <Button
                          size="sm"
                          variant="outline"
                          className="border-rose-500/30 text-rose-600 hover:bg-rose-500/10 hover:text-rose-700"
                          onClick={() => handleOpenReview(cr, false)}
                        >
                          <X className="mr-1 h-3.5 w-3.5" />
                          Reject
                        </Button>
                        <Button
                          size="sm"
                          className="bg-emerald-600 hover:bg-emerald-700 text-white"
                          onClick={() => handleOpenReview(cr, true)}
                        >
                          <Check className="mr-1 h-3.5 w-3.5" />
                          Approve & Apply
                        </Button>
                      </div>
                    )}
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: Outbound Webhooks */}
      {activeTab === 'webhooks' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 text-sm text-foreground">
            <div className="flex items-start gap-3">
              <WebhookIcon className="h-5 w-5 text-primary shrink-0 mt-0.5" />
              <div>
                <p className="font-medium">HMAC-SHA256 Signed Outbound Events</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Aura signs all outbound webhook payloads using your custom secret with the{' '}
                  <code className="rounded bg-muted px-1 py-0.5 font-mono">X-Aura-Signature</code>{' '}
                  header. Receive instantaneous real-time alerts in Slack, Discord, Datadog, or
                  internal microservices when flags toggle.
                </p>
              </div>
            </div>
          </div>

          {loadingWebhooks ? (
            <PageLoader />
          ) : webhooks.length === 0 ? (
            <Card className="border-dashed p-12 text-center">
              <WebhookIcon className="mx-auto h-12 w-12 text-muted-foreground/40" />
              <h3 className="mt-4 text-base font-semibold">No Outbound Webhooks Configured</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                Set up webhooks to notify external services whenever flags or target rules change.
              </p>
              <div className="mt-6">
                <Button onClick={() => setWebhookModalOpen(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  Add First Webhook
                </Button>
              </div>
            </Card>
          ) : (
            <div className="grid gap-4">
              {webhooks.map((wh) => (
                <Card key={wh.id} className="p-5">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1.5 max-w-xl">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-sm font-semibold break-all text-foreground">
                          {wh.url}
                        </span>
                        {wh.lastStatus ? (
                          <span
                            className={cn(
                              'rounded px-2 py-0.5 text-xs font-mono font-semibold',
                              wh.lastStatus >= 200 && wh.lastStatus < 300
                                ? 'bg-emerald-500/10 text-emerald-600 border border-emerald-500/20'
                                : 'bg-rose-500/10 text-rose-600 border border-rose-500/20',
                            )}
                          >
                            HTTP {wh.lastStatus}
                          </span>
                        ) : (
                          <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                            Not triggered
                          </span>
                        )}
                      </div>

                      {wh.description && (
                        <p className="text-xs text-muted-foreground">{wh.description}</p>
                      )}

                      <div className="flex flex-wrap items-center gap-1 pt-1">
                        {wh.events.split(',').map((evt) => (
                          <span
                            key={evt}
                            className="rounded bg-primary/10 px-2 py-0.5 text-[11px] font-mono text-primary"
                          >
                            {evt}
                          </span>
                        ))}
                      </div>

                      {wh.lastTrigger && (
                        <p className="text-xs text-muted-foreground/75 pt-1">
                          Last triggered: {new Date(wh.lastTrigger).toLocaleString()}
                        </p>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={testingWhId === wh.id}
                        onClick={() => handleTestWebhook(wh.id)}
                      >
                        {testingWhId === wh.id ? (
                          <RefreshCw className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <Play className="mr-1.5 h-3.5 w-3.5" />
                        )}
                        Test Ping
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-destructive hover:bg-destructive/10"
                        onClick={async () => {
                          if (confirm('Delete this webhook?')) {
                            await deleteWebhookMutation.mutateAsync(wh.id);
                          }
                        }}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>

                  {/* Show test ping response if just tested */}
                  {testResult && testResult.id === wh.id && (
                    <div className="mt-4 rounded-lg border border-border/80 bg-muted/30 p-3 text-xs font-mono">
                      <div className="flex items-center justify-between text-muted-foreground mb-1">
                        <span>Ping Result:</span>
                        <span>Latency: {testResult.result.latencyMs}ms</span>
                      </div>
                      <div
                        className={cn(
                          'font-semibold',
                          testResult.result.success ? 'text-emerald-500' : 'text-rose-500',
                        )}
                      >
                        {testResult.result.message} (HTTP {testResult.result.statusCode})
                      </div>
                    </div>
                  )}
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: AI Agent MCP Server */}
      {activeTab === 'mcp' && (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-3">
            {/* Overview & Live Tools */}
            <Card className="lg:col-span-2">
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Bot className="h-5 w-5 text-primary" />
                  <CardTitle>Aura Model Context Protocol (MCP) Server</CardTitle>
                </div>
                <CardDescription>
                  Autonomous coding assistants (Cursor, Antigravity, Claude Desktop) can safely
                  read, toggle, roll out, and clean up flags in this project.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-2 sm:grid-cols-2">
                  <div className="rounded-lg border border-border/80 p-3">
                    <span className="font-mono text-xs font-semibold text-primary">
                      list_flags
                    </span>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Lists all feature flags and environment statuses in project.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border/80 p-3">
                    <span className="font-mono text-xs font-semibold text-primary">
                      get_flag
                    </span>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Inspect targeting rules, rollout percentages, and branch.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border/80 p-3">
                    <span className="font-mono text-xs font-semibold text-primary">
                      toggle_flag
                    </span>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Enables or disables a flag in a specific environment.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border/80 p-3">
                    <span className="font-mono text-xs font-semibold text-primary">
                      update_rollout
                    </span>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Adjusts gradual canary percentage exposure (0-100%).
                    </p>
                  </div>
                  <div className="rounded-lg border border-border/80 p-3">
                    <span className="font-mono text-xs font-semibold text-primary">
                      evaluate_flags
                    </span>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Evaluates flags for simulated user attributes/groups.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border/80 p-3">
                    <span className="font-mono text-xs font-semibold text-primary">
                      get_tech_debt
                    </span>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Scans codebase for dormant, stale, and retired flags.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border/80 p-3 sm:col-span-2">
                    <span className="font-mono text-xs font-semibold text-primary">
                      cleanup_flag
                    </span>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Generates PR branch removing legacy flag conditionals from code.
                    </p>
                  </div>
                </div>

                {/* Interactive Playground */}
                <div className="mt-6 border-t border-border pt-4">
                  <h4 className="text-sm font-semibold flex items-center gap-2">
                    <Play className="h-4 w-4 text-emerald-500" />
                    Interactive MCP Tool Playground
                  </h4>
                  <div className="mt-3 grid gap-3 sm:grid-cols-2">
                    <div>
                      <Label className="text-xs">Select Tool</Label>
                      <select
                        className="mt-1 w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs font-mono"
                        value={mcpTool}
                        onChange={(e) => {
                          setMcpTool(e.target.value);
                          if (e.target.value === 'list_flags' || e.target.value === 'get_tech_debt') {
                            setMcpParams('{}');
                          } else if (e.target.value === 'get_flag') {
                            setMcpParams(
                              JSON.stringify({ flagKey: features[0]?.key || 'new-feature' }, null, 2),
                            );
                          } else if (e.target.value === 'toggle_flag') {
                            setMcpParams(
                              JSON.stringify(
                                {
                                  flagKey: features[0]?.key || 'new-feature',
                                  environmentSlug: 'development',
                                  enabled: true,
                                },
                                null,
                                2,
                              ),
                            );
                          } else if (e.target.value === 'evaluate_flags') {
                            setMcpParams(
                              JSON.stringify(
                                {
                                  environmentSlug: 'development',
                                  userId: 'user-123',
                                  attributes: { plan: 'enterprise', country: 'US' },
                                },
                                null,
                                2,
                              ),
                            );
                          }
                        }}
                      >
                        <option value="list_flags">list_flags</option>
                        <option value="get_flag">get_flag</option>
                        <option value="toggle_flag">toggle_flag</option>
                        <option value="update_rollout">update_rollout</option>
                        <option value="evaluate_flags">evaluate_flags</option>
                        <option value="get_tech_debt">get_tech_debt</option>
                        <option value="cleanup_flag">cleanup_flag</option>
                      </select>
                    </div>

                    <div>
                      <Label className="text-xs">JSON Arguments</Label>
                      <textarea
                        rows={3}
                        className="mt-1 w-full rounded-md border border-input bg-background p-2 text-xs font-mono"
                        value={mcpParams}
                        onChange={(e) => setMcpParams(e.target.value)}
                      />
                    </div>
                  </div>

                  <div className="mt-3 flex justify-end">
                    <Button
                      size="sm"
                      onClick={handleExecuteMcpTool}
                      disabled={mcpExecuting}
                      data-testid="btn-run-mcp"
                    >
                      {mcpExecuting ? (
                        <RefreshCw className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <Send className="mr-1.5 h-3.5 w-3.5" />
                      )}
                      Execute MCP Tool
                    </Button>
                  </div>

                  {mcpOutput && (
                    <div className="mt-3 rounded-md bg-zinc-950 p-3 text-xs font-mono text-zinc-100 overflow-x-auto max-h-60">
                      <pre>{mcpOutput}</pre>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>

            {/* Quick Connect Snippets */}
            <div className="space-y-4">
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-sm">Cursor (.cursor/mcp.json)</CardTitle>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => handleCopy(cursorSnippet, 'cursor')}
                    >
                      {copiedSnippet === 'cursor' ? (
                        <Check className="h-3.5 w-3.5 text-emerald-500" />
                      ) : (
                        <Copy className="h-3.5 w-3.5" />
                      )}
                    </Button>
                  </div>
                </CardHeader>
                <CardContent>
                  <pre className="rounded bg-zinc-950 p-2 text-[11px] font-mono text-zinc-200 overflow-x-auto max-h-44">
                    {cursorSnippet}
                  </pre>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-sm">Claude Desktop Config</CardTitle>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => handleCopy(claudeDesktopSnippet, 'claude')}
                    >
                      {copiedSnippet === 'claude' ? (
                        <Check className="h-3.5 w-3.5 text-emerald-500" />
                      ) : (
                        <Copy className="h-3.5 w-3.5" />
                      )}
                    </Button>
                  </div>
                </CardHeader>
                <CardContent>
                  <pre className="rounded bg-zinc-950 p-2 text-[11px] font-mono text-zinc-200 overflow-x-auto max-h-44">
                    {claudeDesktopSnippet}
                  </pre>
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      )}

      {/* TAB 4: Environment Protection Policies */}
      {activeTab === 'environments' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-border/80 bg-muted/20 p-4 text-sm text-foreground">
            <p className="font-medium">Enforce Peer-Review Protection on Critical Environments</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              When enabled, direct flag toggles or rollout alterations on that environment are
              blocked and converted into pending Change Requests requiring senior peer approval.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {project.environments?.map((env) => (
              <Card key={env.id} className="p-5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div
                      className="h-3 w-3 rounded-full"
                      style={{ backgroundColor: env.color || '#6B7280' }}
                    />
                    <span className="font-semibold text-foreground">{env.name}</span>
                    <span className="font-mono text-xs text-muted-foreground">({env.slug})</span>
                  </div>

                  {env.requireApproval ? (
                    <span className="rounded-full bg-amber-500/10 px-2 py-0.5 text-xs font-semibold text-amber-600 border border-amber-500/20">
                      Protected
                    </span>
                  ) : (
                    <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      Direct
                    </span>
                  )}
                </div>

                <div className="mt-4 pt-4 border-t border-border flex items-center justify-between text-xs">
                  <div>
                    <span className="font-medium">Four-Eyes Approval:</span>
                    <p className="text-muted-foreground">
                      {env.requireApproval
                        ? 'Requires 2nd pair of eyes before apply'
                        : 'Immediate toggle allowed'}
                    </p>
                  </div>

                  <Button
                    size="sm"
                    variant={env.requireApproval ? 'outline' : 'default'}
                    onClick={async () => {
                      await toggleEnvApprovalMutation.mutateAsync({
                        environmentId: env.id,
                        requireApproval: !env.requireApproval,
                      });
                    }}
                  >
                    {env.requireApproval ? 'Disable' : 'Enable'}
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* MODAL: Submit Change Request */}
      <Modal
        open={crModalOpen}
        onClose={() => setCrModalOpen(false)}
        title="Submit Four-Eyes Change Request"
        description="Submit a proposed flag state change for peer review before it applies."
      >
        <form onSubmit={handleCreateCr} className="space-y-4">
          {crError && (
            <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
              {crError}
            </div>
          )}

          <div>
            <Label>Feature Flag</Label>
            <select
              className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={crFeatureKey}
              onChange={(e) => setCrFeatureKey(e.target.value)}
              required
            >
              {features.map((f) => (
                <option key={f.id} value={f.key}>
                  {f.name} ({f.key})
                </option>
              ))}
            </select>
          </div>

          <div>
            <Label>Target Environment</Label>
            <select
              className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={crEnvSlug}
              onChange={(e) => setCrEnvSlug(e.target.value)}
              required
            >
              {project.environments?.map((env) => (
                <option key={env.id} value={env.slug}>
                  {env.name} ({env.slug}) {env.requireApproval ? '🔒 Protected' : ''}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Target State</Label>
              <select
                className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={crTargetState ? 'true' : 'false'}
                onChange={(e) => setCrTargetState(e.target.value === 'true')}
              >
                <option value="true">Enabled (ON)</option>
                <option value="false">Disabled (OFF)</option>
              </select>
            </div>

            <div>
              <Label>Rollout Percentage ({crTargetRollout}%)</Label>
              <input
                type="range"
                min={0}
                max={100}
                step={5}
                className="mt-3 w-full"
                value={crTargetRollout}
                onChange={(e) => setCrTargetRollout(Number(e.target.value))}
              />
            </div>
          </div>

          <div>
            <Label>Reason / Jira Reference</Label>
            <Input
              placeholder="e.g. PROD-1042: Gradual enablement for Q4 beta users"
              value={crReason}
              onChange={(e) => setCrReason(e.target.value)}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setCrModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={createCrMutation.isPending}>
              {createCrMutation.isPending ? 'Submitting...' : 'Submit Request'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: Review Change Request */}
      <Modal
        open={reviewModalOpen}
        onClose={() => setReviewModalOpen(false)}
        title={isApproving ? 'Approve & Apply Change Request' : 'Reject Change Request'}
        description={`Perform Four-Eyes peer review for flag "${selectedCr?.featureKey}" on environment "${selectedCr?.environmentSlug}".`}
      >
        <div className="space-y-4">
          <div className="rounded-lg border border-border/80 bg-muted/30 p-3 text-xs space-y-1">
            <div>
              Target State:{' '}
              <strong className={selectedCr?.targetState ? 'text-emerald-500' : 'text-rose-500'}>
                {selectedCr?.targetState ? 'ENABLED (ON)' : 'DISABLED (OFF)'}
              </strong>
            </div>
            <div>
              Target Rollout: <strong>{selectedCr?.targetRollout}%</strong>
            </div>
            {selectedCr?.reason && <div>Reason: "{selectedCr.reason}"</div>}
          </div>

          <div>
            <Label>Review Notes (Optional)</Label>
            <Input
              placeholder="e.g. Verified metrics in staging; safe to proceed."
              value={reviewComment}
              onChange={(e) => setReviewComment(e.target.value)}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setReviewModalOpen(false)}>
              Cancel
            </Button>
            <Button
              type="button"
              className={
                isApproving
                  ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                  : 'bg-rose-600 hover:bg-rose-700 text-white'
              }
              onClick={handleConfirmReview}
              disabled={reviewCrMutation.isPending}
            >
              {reviewCrMutation.isPending
                ? 'Processing...'
                : isApproving
                  ? 'Approve & Apply Now'
                  : 'Reject Request'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* MODAL: Add Webhook */}
      <Modal
        open={webhookModalOpen}
        onClose={() => setWebhookModalOpen(false)}
        title="Add Outbound Webhook"
        description="Receive HMAC-signed HTTP POST notifications whenever flag states or rules change."
      >
        <form onSubmit={handleCreateWebhook} className="space-y-4">
          {whError && (
            <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
              {whError}
            </div>
          )}

          <div>
            <Label>Webhook Destination URL</Label>
            <Input
              placeholder="https://api.yourcompany.com/aura-events"
              value={whUrl}
              onChange={(e) => setWhUrl(e.target.value)}
              required
            />
          </div>

          <div>
            <Label>Description</Label>
            <Input
              placeholder="e.g. Slack #releases channel notification"
              value={whDescription}
              onChange={(e) => setWhDescription(e.target.value)}
            />
          </div>

          <div>
            <Label>HMAC Secret (Optional)</Label>
            <Input
              placeholder="Secret used to sign X-Aura-Signature header"
              value={whSecret}
              onChange={(e) => setWhSecret(e.target.value)}
            />
          </div>

          <div>
            <Label>Event Triggers</Label>
            <div className="mt-2 space-y-2 text-xs">
              {['FLAG_CHANGED', 'TARGETING_CHANGED', 'ROLLOUT_CHANGED'].map((evt) => (
                <label key={evt} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={whEvents.includes(evt)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setWhEvents([...whEvents, evt]);
                      } else {
                        setWhEvents(whEvents.filter((x) => x !== evt));
                      }
                    }}
                  />
                  <span className="font-mono">{evt}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setWebhookModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={createWebhookMutation.isPending}>
              {createWebhookMutation.isPending ? 'Saving...' : 'Register Webhook'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
