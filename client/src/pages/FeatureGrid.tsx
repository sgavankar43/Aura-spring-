import {
  CheckCircle2,
  FolderGit2,
  GitBranch,
  Link,
  Plus,
  Power,
  SlidersHorizontal,
  Trash2,
  Users,
} from 'lucide-react';
import React, { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { PageLoader } from '@/components/feedback/PageLoader';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Modal } from '@/components/ui/Modal';
import {
  useCreateFeature,
  useFeatures,
  useGithubBranches,
  useGithubStatus,
  useLinkFeatureGithub,
  useToggleFlag,
  useUpdateFlagTargeting,
} from '@/hooks/queries';
import { cn } from '@/lib/utils';
import type { Environment, FeatureRow, Project, TargetingRule } from '@/types/domain';

export default function FeatureGrid() {
  const { project } = useOutletContext<{ project: Project }>();
  const { data: features, isLoading } = useFeatures(project.id);
  const toggleFlag = useToggleFlag(project.id);
  const updateTargeting = useUpdateFlagTargeting(project.id);
  const createFeature = useCreateFeature(project.id);
  const linkFeatureGithub = useLinkFeatureGithub(project.id);

  const { data: ghStatus } = useGithubStatus(project.id);
  const isGhConnected = Boolean(ghStatus?.connected);
  const { data: ghBranches } = useGithubBranches(project.id, isGhConnected);

  // New Feature modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [defaultEnabled, setDefaultEnabled] = useState(false);
  const [initialBranch, setInitialBranch] = useState('');
  const [formError, setFormError] = useState('');

  // Link GitHub modal state
  const [linkModalOpen, setLinkModalOpen] = useState(false);
  const [linkingFeature, setLinkingFeature] = useState<FeatureRow | null>(null);
  const [linkBranch, setLinkBranch] = useState('');
  const [linkPrNumber, setLinkPrNumber] = useState<string>('');
  const [linkError, setLinkError] = useState('');

  // Targeting & Canary Rollout modal state
  const [targetingModalOpen, setTargetingModalOpen] = useState(false);
  const [activeTargetingFeature, setActiveTargetingFeature] = useState<FeatureRow | null>(null);
  const [activeTargetingEnv, setActiveTargetingEnv] = useState<Environment | null>(null);
  const [rolloutPct, setRolloutPct] = useState<number>(100);
  const [rulesList, setRulesList] = useState<TargetingRule[]>([]);
  const [targetingError, setTargetingError] = useState('');

  const environments: Environment[] = project.environments ?? [];

  const resetForm = () => {
    setKey('');
    setName('');
    setDescription('');
    setDefaultEnabled(false);
    setInitialBranch('');
    setFormError('');
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    try {
      const res = await createFeature.mutateAsync({
        key: key.trim(),
        name: name.trim(),
        description: description.trim() || undefined,
        defaultEnabled,
      });

      if (initialBranch.trim() && res && (res as { id?: string }).id) {
        await linkFeatureGithub.mutateAsync({
          featureId: (res as { id: string }).id,
          branch: initialBranch.trim(),
        });
      }

      resetForm();
      setModalOpen(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Could not create feature');
    }
  };

  const openLinkModal = (feature: FeatureRow) => {
    setLinkingFeature(feature);
    setLinkBranch(feature.githubBranch ?? '');
    setLinkPrNumber(feature.githubPrNumber ? String(feature.githubPrNumber) : '');
    setLinkError('');
    setLinkModalOpen(true);
  };

  const handleSaveGithubLink = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!linkingFeature) return;
    setLinkError('');
    try {
      const prNum = linkPrNumber.trim() ? parseInt(linkPrNumber.trim(), 10) : null;
      await linkFeatureGithub.mutateAsync({
        featureId: linkingFeature.id,
        branch: linkBranch.trim() || null,
        prNumber: prNum && !isNaN(prNum) ? prNum : null,
      });
      setLinkModalOpen(false);
      setLinkingFeature(null);
    } catch (err) {
      setLinkError(err instanceof Error ? err.message : 'Failed to update GitHub link');
    }
  };

  const openTargetingModal = (feature: FeatureRow, env: Environment) => {
    setActiveTargetingFeature(feature);
    setActiveTargetingEnv(env);
    setTargetingError('');

    const pct = feature.rolloutPercentages?.[env.slug] ?? 100;
    setRolloutPct(pct);

    const rawRules = feature.targetingRules?.[env.slug];
    if (rawRules) {
      try {
        const parsed = JSON.parse(rawRules) as TargetingRule[];
        setRulesList(Array.isArray(parsed) ? parsed : []);
      } catch {
        setRulesList([]);
      }
    } else {
      setRulesList([]);
    }

    setTargetingModalOpen(true);
  };

  const handleAddRule = () => {
    setRulesList([
      ...rulesList,
      { attribute: 'email', operator: 'endsWith', value: '@aura.dev' },
    ]);
  };

  const handleUpdateRule = (index: number, updated: Partial<TargetingRule>) => {
    const next = [...rulesList];
    next[index] = { ...next[index], ...updated };
    setRulesList(next);
  };

  const handleRemoveRule = (index: number) => {
    setRulesList(rulesList.filter((_, i) => i !== index));
  };

  const handleSaveTargeting = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeTargetingFeature || !activeTargetingEnv) return;

    setTargetingError('');
    try {
      const cleanRules = rulesList
        .filter((r) => r.attribute.trim() && r.value.trim())
        .map((r) => ({
          attribute: r.attribute.trim(),
          operator: r.operator.trim(),
          value: r.value.trim(),
        }));

      const rulesPayload = cleanRules.length > 0 ? JSON.stringify(cleanRules) : null;

      await updateTargeting.mutateAsync({
        envSlug: activeTargetingEnv.slug,
        key: activeTargetingFeature.key,
        rolloutPercentage: rolloutPct,
        rules: rulesPayload,
      });

      setTargetingModalOpen(false);
    } catch (err) {
      setTargetingError(err instanceof Error ? err.message : 'Failed to update targeting rules');
    }
  };

  if (isLoading) {
    return <PageLoader />;
  }

  const pending = toggleFlag.variables;
  const isToggling = (envSlug: string, featureKey: string) =>
    toggleFlag.isPending && pending?.envSlug === envSlug && pending?.key === featureKey;

  return (
    <div className="space-y-4" data-testid="feature-grid">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">
            Manage flags, progressive percentage rollouts, and user segmentation rules per environment.
          </p>
          {isGhConnected ? (
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
              <FolderGit2 className="h-3.5 w-3.5 text-primary" />
              Connected to{' '}
              <span className="font-mono font-medium text-foreground">
                {ghStatus?.repoFullName}
              </span>
            </p>
          ) : null}
        </div>
        <Button
          type="button"
          onClick={() => setModalOpen(true)}
          disabled={environments.length === 0}
          data-testid="add-feature"
        >
          <Plus className="h-4 w-4" />
          New feature
        </Button>
      </div>

      {environments.length === 0 ? (
        <div className="rounded-xl border border-dashed border-amber-500/40 bg-amber-500/5 p-6 text-sm text-amber-800 dark:text-amber-200">
          Add at least one environment before you can define feature flags for this project.
        </div>
      ) : null}

      <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm" data-testid="feature-table">
            <thead className="border-b border-border bg-paper-2 text-xs font-medium text-muted-foreground">
              <tr>
                <th className="px-4 py-3 md:px-6">Feature</th>
                {isGhConnected ? (
                  <th className="px-3 py-3 text-center text-xs font-medium">
                    GitHub Code Link
                  </th>
                ) : null}
                {environments.map((env) => (
                  <th key={env.id} className="px-4 py-3 text-center md:px-6">
                    <span className="inline-flex items-center justify-center gap-2">
                      <span
                        className="h-2 w-2 rounded-full ring-2 ring-background"
                        style={{ backgroundColor: env.color || 'var(--color-muted)' }}
                      />
                      {env.name}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y">
              {features?.length === 0 ? (
                <tr>
                  <td
                    colSpan={environments.length + (isGhConnected ? 2 : 1)}
                    className="px-6 py-12 text-center text-muted-foreground"
                  >
                    No features yet. Create one to start rolling out safely.
                  </td>
                </tr>
              ) : null}

              {(features ?? []).map((feature: FeatureRow) => (
                <tr
                  key={feature.id}
                  className="hover:bg-muted/20"
                  data-testid={`feature-row-${feature.key}`}
                >
                  <td className="px-4 py-4 md:px-6">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-xs font-semibold text-foreground">
                        {feature.key}
                      </span>
                    </div>
                    <p className="mt-0.5 text-xs text-muted-foreground">{feature.name}</p>
                  </td>

                  {isGhConnected ? (
                    <td className="px-3 py-4 text-center">
                      {feature.githubBranch || feature.githubPrNumber ? (
                        <button
                          type="button"
                          onClick={() => openLinkModal(feature)}
                          className="inline-flex items-center gap-1.5 rounded-md border border-primary/20 bg-primary/5 px-2 py-1 text-xs font-mono text-primary hover:bg-primary/10 transition-colors"
                          title="Click to edit GitHub link"
                        >
                          <GitBranch className="h-3 w-3" />
                          <span>{feature.githubBranch ?? 'branch'}</span>
                          {feature.githubPrNumber ? (
                            <span className="rounded bg-primary/15 px-1 py-0.2 text-[10px]">
                              PR #{feature.githubPrNumber}
                            </span>
                          ) : null}
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => openLinkModal(feature)}
                          className="inline-flex items-center gap-1 rounded border border-dashed px-2 py-0.5 text-xs text-muted-foreground hover:border-primary/50 hover:text-foreground transition-colors"
                          title="Link to GitHub Branch or PR"
                        >
                          <Link className="h-3 w-3" />
                          <span>Link branch</span>
                        </button>
                      )}
                    </td>
                  ) : null}

                  {environments.map((env) => {
                    const stateVal = feature.states[env.slug] ?? feature.defaultEnabled;
                    const rollout = feature.rolloutPercentages?.[env.slug] ?? 100;
                    const hasRules = Boolean(feature.targetingRules?.[env.slug]);

                    return (
                      <td key={env.id} className="px-4 py-4 text-center md:px-6">
                        <div className="flex flex-col items-center gap-1.5">
                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              data-testid={`flag-toggle-${feature.key}-${env.slug}`}
                              onClick={() =>
                                toggleFlag.mutate({
                                  envSlug: env.slug,
                                  key: feature.key,
                                  enabled: !stateVal,
                                })
                              }
                              disabled={isToggling(env.slug, feature.key)}
                              className={cn(
                                'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors shadow-sm',
                                stateVal
                                  ? 'border-success/30 bg-success/10 text-success hover:bg-success/20'
                                  : 'border-transparent bg-muted/60 text-muted-foreground hover:bg-muted',
                              )}
                            >
                              <Power className={cn('h-3 w-3', !stateVal && 'opacity-50')} />
                              {stateVal ? 'On' : 'Off'}
                            </button>

                            <button
                              type="button"
                              onClick={() => openTargetingModal(feature, env)}
                              className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                              title="Configure Canary Rollout & Targeting Rules"
                              data-testid={`targeting-btn-${feature.key}-${env.slug}`}
                            >
                              <SlidersHorizontal className="h-3.5 w-3.5" />
                            </button>
                          </div>

                          {/* Canary / Rules status badge */}
                          {stateVal && (rollout < 100 || hasRules) ? (
                            <div className="flex flex-wrap items-center justify-center gap-1">
                              {rollout < 100 ? (
                                <span className="inline-flex items-center rounded border border-amber-500/30 bg-amber-500/10 px-1.5 py-0.2 text-[10px] font-mono font-medium text-amber-600 dark:text-amber-400">
                                  {rollout}% canary
                                </span>
                              ) : null}
                              {hasRules ? (
                                <span className="inline-flex items-center gap-0.5 rounded border border-primary/20 bg-primary/10 px-1.5 py-0.2 text-[10px] font-mono text-primary font-medium">
                                  <Users className="h-2.5 w-2.5" />
                                  rules
                                </span>
                              ) : null}
                            </div>
                          ) : null}
                        </div>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* New Feature Modal */}
      <Modal
        open={modalOpen}
        onClose={() => {
          setModalOpen(false);
          resetForm();
        }}
        title="New feature flag"
        description="Keys should be stable identifiers (e.g. billing-v2). They cannot be renamed lightly in production."
        data-testid="feature-modal"
      >
        <form onSubmit={handleCreate} className="space-y-4">
          {formError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {formError}
            </div>
          ) : null}
          <div className="space-y-2">
            <Label htmlFor="feat-key">Key</Label>
            <Input
              id="feat-key"
              required
              value={key}
              onChange={(e) => setKey(e.target.value)}
              className="font-mono"
              placeholder="new-checkout"
              data-testid="feature-key-input"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="feat-name">Display name</Label>
            <Input
              id="feat-name"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="New checkout flow"
              data-testid="feature-name-input"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="feat-desc">Description (optional)</Label>
            <textarea
              id="feat-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            />
          </div>

          {isGhConnected && ghBranches && ghBranches.length > 0 ? (
            <div className="space-y-2">
              <Label htmlFor="feat-branch">Link to GitHub Branch (optional)</Label>
              <select
                id="feat-branch"
                value={initialBranch}
                onChange={(e) => setInitialBranch(e.target.value)}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">-- No branch linked --</option>
                {ghBranches.map((b) => (
                  <option key={b.name} value={b.name}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>
          ) : null}

          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={defaultEnabled}
              onChange={(e) => setDefaultEnabled(e.target.checked)}
              className="h-4 w-4 rounded border-input"
            />
            Default on (when no per-environment state exists)
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setModalOpen(false);
                resetForm();
              }}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={createFeature.isPending} data-testid="feature-submit">
              {createFeature.isPending ? 'Creating…' : 'Create feature'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Targeting & Canary Rollout Modal */}
      <Modal
        open={targetingModalOpen}
        onClose={() => setTargetingModalOpen(false)}
        title={`Targeting & Rollout — ${activeTargetingFeature?.key}`}
        description={`Configure progressive canary exposure and segmentation for the ${activeTargetingEnv?.name} environment.`}
      >
        <form onSubmit={handleSaveTargeting} className="space-y-5">
          {targetingError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              {targetingError}
            </div>
          ) : null}

          {/* Section 1: Progressive Rollout Slider */}
          <div className="space-y-3 rounded-lg border border-border bg-paper-2 p-4">
            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <Label className="text-sm font-semibold">Percentage Rollout (Canary)</Label>
                <p className="text-xs text-muted-foreground">
                  Deterministically hashes user ID using MurmurHash3.
                </p>
              </div>
              <span className="rounded-md border border-primary/20 bg-primary/10 px-2.5 py-1 font-mono text-sm font-bold text-primary">
                {rolloutPct}%
              </span>
            </div>

            <input
              type="range"
              min="0"
              max="100"
              step="5"
              value={rolloutPct}
              onChange={(e) => setRolloutPct(parseInt(e.target.value, 10))}
              className="w-full accent-primary cursor-pointer"
            />

            <div className="flex flex-wrap gap-1.5 pt-1">
              {[
                { label: '0% Kill', val: 0 },
                { label: '10% Canary', val: 10 },
                { label: '25% Beta', val: 25 },
                { label: '50% Staged', val: 50 },
                { label: '100% Full', val: 100 },
              ].map((preset) => (
                <button
                  key={preset.val}
                  type="button"
                  onClick={() => setRolloutPct(preset.val)}
                  className={cn(
                    'rounded border px-2 py-0.5 text-xs font-mono transition-colors',
                    rolloutPct === preset.val
                      ? 'border-primary bg-primary text-primary-foreground font-semibold'
                      : 'border-border bg-background text-muted-foreground hover:text-foreground',
                  )}
                >
                  {preset.label}
                </button>
              ))}
            </div>
          </div>

          {/* Section 2: User Segmentation Rules */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <Label className="text-sm font-semibold">User Segmentation Rules</Label>
                <p className="text-xs text-muted-foreground">
                  Grant instant flag access to specific cohorts (e.g. employee domains or beta tiers).
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleAddRule}
                className="text-xs gap-1"
              >
                <Plus className="h-3.5 w-3.5" />
                Add rule
              </Button>
            </div>

            {rulesList.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-muted-foreground">
                No custom rules defined. Flag evaluates strictly based on rollout percentage.
              </div>
            ) : (
              <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                {rulesList.map((rule, idx) => (
                  <div
                    key={idx}
                    className="flex items-center gap-2 rounded-lg border border-border bg-background p-2.5 text-xs"
                  >
                    <div className="w-28 shrink-0">
                      <Input
                        placeholder="attribute (e.g. email)"
                        value={rule.attribute}
                        onChange={(e) => handleUpdateRule(idx, { attribute: e.target.value })}
                        className="h-8 font-mono text-xs"
                      />
                    </div>

                    <div className="w-32 shrink-0">
                      <select
                        value={rule.operator}
                        onChange={(e) => handleUpdateRule(idx, { operator: e.target.value })}
                        className="h-8 w-full rounded-md border border-input bg-background px-2 font-mono text-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
                      >
                        <option value="equals">equals (=)</option>
                        <option value="notEquals">not equals (≠)</option>
                        <option value="contains">contains</option>
                        <option value="startsWith">starts with</option>
                        <option value="endsWith">ends with</option>
                        <option value="in">in list (a,b,c)</option>
                        <option value="greaterThan">greater than (&gt;)</option>
                        <option value="lessThan">less than (&lt;)</option>
                      </select>
                    </div>

                    <div className="flex-1">
                      <Input
                        placeholder="target value (e.g. @company.com)"
                        value={rule.value}
                        onChange={(e) => handleUpdateRule(idx, { value: e.target.value })}
                        className="h-8 font-mono text-xs"
                      />
                    </div>

                    <button
                      type="button"
                      onClick={() => handleRemoveRule(idx)}
                      className="p-1 text-muted-foreground hover:text-destructive transition-colors shrink-0"
                      title="Remove condition"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* SSE Live Push Alert */}
          <div className="flex items-start gap-2 rounded-lg border border-primary/20 bg-primary/5 p-3 text-xs text-muted-foreground">
            <CheckCircle2 className="h-4 w-4 text-primary shrink-0 mt-0.5" />
            <span>
              Targeting rules are synchronized in real time via Server-Sent Events (&lt;15ms) directly to connected client SDKs.
            </span>
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-border">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setTargetingModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={updateTargeting.isPending}
            >
              {updateTargeting.isPending ? 'Saving targeting…' : 'Save targeting'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Link to GitHub Branch/PR Modal */}
      <Modal
        open={linkModalOpen}
        onClose={() => setLinkModalOpen(false)}
        title={`Link "${linkingFeature?.key}" to GitHub`}
        description="Tie this feature flag directly to code in your repository."
      >
        <form onSubmit={handleSaveGithubLink} className="space-y-4">
          {linkError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              {linkError}
            </div>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor="link-branch">Branch name</Label>
            {ghBranches && ghBranches.length > 0 ? (
              <select
                id="link-branch"
                value={linkBranch}
                onChange={(e) => setLinkBranch(e.target.value)}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">-- No branch --</option>
                {ghBranches.map((b) => (
                  <option key={b.name} value={b.name}>
                    {b.name}
                  </option>
                ))}
              </select>
            ) : (
              <Input
                id="link-branch"
                placeholder="e.g. feature/new-checkout"
                value={linkBranch}
                onChange={(e) => setLinkBranch(e.target.value)}
                className="font-mono text-xs"
              />
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="link-pr">Pull Request number (optional)</Label>
            <Input
              id="link-pr"
              type="number"
              placeholder="e.g. 42"
              value={linkPrNumber}
              onChange={(e) => setLinkPrNumber(e.target.value)}
              className="font-mono text-xs"
            />
          </div>

          <div className="flex items-center justify-between pt-2">
            {linkingFeature?.githubBranch || linkingFeature?.githubPrNumber ? (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-destructive text-xs"
                onClick={() => {
                  setLinkBranch('');
                  setLinkPrNumber('');
                }}
              >
                Clear link
              </Button>
            ) : <span />}

            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setLinkModalOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                size="sm"
                disabled={linkFeatureGithub.isPending}
              >
                {linkFeatureGithub.isPending ? 'Saving…' : 'Save link'}
              </Button>
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
}
