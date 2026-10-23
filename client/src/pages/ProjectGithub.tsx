import {
  AlertCircle,
  ArrowLeft,
  Check,
  ChevronRight,
  Clock,
  Code2,
  Copy,
  ExternalLink,
  FileCode,
  Folder,
  FolderGit2,
  GitBranch,
  GitCommit,
  GitFork,
  GitPullRequest,
  Lock,
  RefreshCw,
  Star,
  Tag,
  Trash2,
  Unlock,
} from 'lucide-react';
import React, { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { PageLoader } from '@/components/feedback/PageLoader';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Modal } from '@/components/ui/Modal';
import {
  useConnectGithub,
  useDisconnectGithub,
  useGithubBranches,
  useGithubCommits,
  useGithubPullRequests,
  useGithubStatus,
  useGithubTags,
  useGithubTree,
} from '@/hooks/queries';
import { cn } from '@/lib/utils';
import type { GithubContent, Project } from '@/types/domain';

type ActiveTab = 'commits' | 'branches' | 'tags' | 'prs' | 'code';

export default function ProjectGithub() {
  const { project } = useOutletContext<{ project: Project }>();
  const { data: status, isLoading, refetch: refetchStatus } = useGithubStatus(project.id);
  const connectMutation = useConnectGithub(project.id);
  const disconnectMutation = useDisconnectGithub(project.id);

  // Connect form state
  const [repoInput, setRepoInput] = useState('');
  const [tokenInput, setTokenInput] = useState('');
  const [branchInput, setBranchInput] = useState('');
  const [connectError, setConnectError] = useState('');

  // Connected view state
  const [activeTab, setActiveTab] = useState<ActiveTab>('commits');
  const [selectedBranch, setSelectedBranch] = useState<string>('');
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const [prStateFilter, setPrStateFilter] = useState<'all' | 'open' | 'closed'>('open');

  // Code explorer state
  const [currentPath, setCurrentPath] = useState<string>('');
  const [activeFile, setActiveFile] = useState<GithubContent | null>(null);
  const [copiedSha, setCopiedSha] = useState<string | null>(null);

  const isConnected = Boolean(status?.connected);
  const effectiveBranch = selectedBranch || status?.defaultBranch || 'main';

  // Fetch sub-resources when connected
  const { data: branches, isLoading: branchesLoading } = useGithubBranches(project.id, isConnected);
  const { data: tags, isLoading: tagsLoading } = useGithubTags(project.id, isConnected);
  const { data: commits, isLoading: commitsLoading } = useGithubCommits(
    project.id,
    effectiveBranch,
    isConnected,
  );
  const { data: treeItems, isLoading: treeLoading } = useGithubTree(
    project.id,
    effectiveBranch,
    currentPath,
    isConnected && activeTab === 'code',
  );
  const { data: pullRequests, isLoading: prsLoading } = useGithubPullRequests(
    project.id,
    prStateFilter,
    isConnected && activeTab === 'prs',
  );

  const handleConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    setConnectError('');
    try {
      await connectMutation.mutateAsync({
        repository: repoInput.trim(),
        accessToken: tokenInput.trim() || undefined,
        defaultBranch: branchInput.trim() || undefined,
      });
      setRepoInput('');
      setTokenInput('');
      setBranchInput('');
    } catch (err) {
      setConnectError(err instanceof Error ? err.message : 'Failed to connect repository');
    }
  };

  const handleDisconnect = async () => {
    try {
      await disconnectMutation.mutateAsync();
      setConfirmDisconnect(false);
      setActiveFile(null);
      setCurrentPath('');
    } catch {
      // Handled by query client
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    void navigator.clipboard.writeText(text);
    setCopiedSha(id);
    setTimeout(() => setCopiedSha(null), 2000);
  };

  const navigateToFolder = (path: string) => {
    setActiveFile(null);
    setCurrentPath(path);
  };

  const navigateUp = () => {
    setActiveFile(null);
    if (!currentPath) return;
    const parts = currentPath.split('/');
    parts.pop();
    setCurrentPath(parts.join('/'));
  };

  if (isLoading) {
    return <PageLoader />;
  }

  // View: NOT CONNECTED
  if (!isConnected || !status) {
    return (
      <div className="space-y-6" data-testid="github-not-connected">
        <div className="flex flex-col gap-1">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <FolderGit2 className="h-5 w-5 text-primary" />
            GitHub Repository Integration
          </h2>
          <p className="text-sm text-muted-foreground">
            Connect this project to a GitHub repository to track branches, code versions, releases,
            pull requests, and tie feature flags directly to repository code.
          </p>
        </div>

        <Card className="max-w-2xl">
          <CardHeader>
            <div className="flex items-center gap-2">
              <FolderGit2 className="h-5 w-5 text-foreground" />
              <CardTitle>Connect Repository</CardTitle>
            </div>
            <CardDescription>
              Enter the repository slug (e.g. <code>facebook/react</code>, <code>vercel/next.js</code>
              , or your private repo).
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleConnect} className="space-y-4">
              {connectError ? (
                <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{connectError}</span>
                </div>
              ) : null}

              <div className="space-y-2">
                <Label htmlFor="gh-repo">Repository (owner/repo)</Label>
                <Input
                  id="gh-repo"
                  required
                  placeholder="owner/repo (e.g. torvalds/linux or org/my-app)"
                  value={repoInput}
                  onChange={(e) => setRepoInput(e.target.value)}
                  className="font-mono text-sm"
                  data-testid="github-repo-input"
                />
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="gh-token">Personal Access Token (optional)</Label>
                  <span className="text-xs text-muted-foreground">Recommended for private repos</span>
                </div>
                <Input
                  id="gh-token"
                  type="password"
                  placeholder="ghp_xxxxxxxxxxxxxxxxxxxx or github_pat_..."
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                  className="font-mono text-sm"
                  data-testid="github-token-input"
                />
                <p className="text-xs text-muted-foreground">
                  Public repos work without a token. Provide a token with <code>repo</code> scope to
                  access private repositories or increase GitHub rate limits.
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="gh-branch">Default branch (optional)</Label>
                <Input
                  id="gh-branch"
                  placeholder="main (auto-detected if blank)"
                  value={branchInput}
                  onChange={(e) => setBranchInput(e.target.value)}
                  className="font-mono text-sm"
                />
              </div>

              <div className="flex justify-end pt-2">
                <Button
                  type="submit"
                  disabled={connectMutation.isPending}
                  data-testid="github-connect-btn"
                  className="gap-2"
                >
                  {connectMutation.isPending ? (
                    <>
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      Connecting…
                    </>
                  ) : (
                    <>
                      <FolderGit2 className="h-4 w-4" />
                      Connect repository
                    </>
                  )}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    );
  }

  // View: CONNECTED
  return (
    <div className="space-y-6" data-testid="github-connected-view">
      {/* Repository Overview Banner */}
      <div className="rounded-xl border bg-card p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2.5">
              <FolderGit2 className="h-6 w-6 text-primary" />
              <h2 className="text-xl font-bold tracking-tight">{status.repoFullName}</h2>
              <span
                className={cn(
                  'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium border',
                  status.isPrivate
                    ? 'border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400'
                    : 'border-success/30 bg-success/10 text-success',
                )}
              >
                {status.isPrivate ? (
                  <>
                    <Lock className="h-3 w-3" /> Private
                  </>
                ) : (
                  <>
                    <Unlock className="h-3 w-3" /> Public
                  </>
                )}
              </span>
              <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 font-mono text-xs text-muted-foreground">
                <GitBranch className="h-3 w-3" /> {status.defaultBranch}
              </span>
            </div>
            {status.description ? (
              <p className="text-sm text-muted-foreground">{status.description}</p>
            ) : null}
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {status.starsCount !== undefined ? (
              <div className="flex items-center gap-1.5 rounded-lg border bg-muted/40 px-3 py-1.5 text-xs font-medium">
                <Star className="h-3.5 w-3.5 text-amber-500" />
                <span>{status.starsCount.toLocaleString()} stars</span>
              </div>
            ) : null}
            {status.forksCount !== undefined ? (
              <div className="flex items-center gap-1.5 rounded-lg border bg-muted/40 px-3 py-1.5 text-xs font-medium">
                <GitFork className="h-3.5 w-3.5 text-primary" />
                <span>{status.forksCount.toLocaleString()} forks</span>
              </div>
            ) : null}
            {status.repoUrl ? (
              <a
                href={status.repoUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 rounded-lg border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted transition-colors"
              >
                <span>GitHub</span>
                <ExternalLink className="h-3.5 w-3.5 opacity-60" />
              </a>
            ) : null}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void refetchStatus()}
              title="Refresh repository data"
            >
              <RefreshCw className="h-3.5 w-3.5" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
              onClick={() => setConfirmDisconnect(true)}
              data-testid="github-disconnect-open"
            >
              <Trash2 className="h-3.5 w-3.5" />
              Disconnect
            </Button>
          </div>
        </div>
      </div>

      {/* Navigation Sub-Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b pb-2">
        <div className="flex gap-1 overflow-x-auto">
          <button
            type="button"
            onClick={() => setActiveTab('commits')}
            className={cn(
              'flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
              activeTab === 'commits'
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
            data-testid="gh-tab-commits"
          >
            <GitCommit className="h-3.5 w-3.5" />
            Commits
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('branches')}
            className={cn(
              'flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
              activeTab === 'branches'
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
            data-testid="gh-tab-branches"
          >
            <GitBranch className="h-3.5 w-3.5" />
            Branches ({branches?.length ?? 0})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('tags')}
            className={cn(
              'flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
              activeTab === 'tags'
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
            data-testid="gh-tab-tags"
          >
            <Tag className="h-3.5 w-3.5" />
            Releases & Versions ({tags?.length ?? 0})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('prs')}
            className={cn(
              'flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
              activeTab === 'prs'
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
            data-testid="gh-tab-prs"
          >
            <GitPullRequest className="h-3.5 w-3.5" />
            Pull Requests
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('code')}
            className={cn(
              'flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
              activeTab === 'code'
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
            data-testid="gh-tab-code"
          >
            <Code2 className="h-3.5 w-3.5" />
            Code Explorer
          </button>
        </div>

        {/* Branch Selector for Commits / Code view */}
        {(activeTab === 'commits' || activeTab === 'code') && branches && branches.length > 0 ? (
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground">Branch:</span>
            <select
              value={effectiveBranch}
              onChange={(e) => {
                setSelectedBranch(e.target.value);
                setActiveFile(null);
              }}
              className="rounded-md border bg-background px-2.5 py-1 text-xs font-mono font-medium focus:outline-none focus:ring-1 focus:ring-primary"
            >
              {branches.map((b) => (
                <option key={b.name} value={b.name}>
                  {b.name} {b.isProtected ? '🔒' : ''}
                </option>
              ))}
            </select>
          </div>
        ) : null}
      </div>

      {/* Tab 1: COMMITS */}
      {activeTab === 'commits' ? (
        <div className="space-y-3">
          {commitsLoading ? (
            <PageLoader />
          ) : !commits || commits.length === 0 ? (
            <div className="rounded-xl border bg-card p-12 text-center text-muted-foreground">
              No commits found on branch <code>{effectiveBranch}</code>.
            </div>
          ) : (
            <div className="divide-y rounded-xl border bg-card shadow-sm">
              {commits.map((c) => (
                <div
                  key={c.sha}
                  className="flex flex-col gap-2 p-4 transition-colors hover:bg-muted/30 sm:flex-row sm:items-center sm:justify-between"
                  data-testid={`commit-row-${c.shortSha}`}
                >
                  <div className="min-w-0 flex-1 space-y-1">
                    <p className="font-medium text-sm text-foreground break-words line-clamp-2">
                      {c.message.split('\n')[0]}
                    </p>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      {c.authorAvatar ? (
                        <img
                          src={c.authorAvatar}
                          alt={c.authorName}
                          className="h-4 w-4 rounded-full"
                        />
                      ) : null}
                      <span className="font-medium text-foreground/80">{c.authorName}</span>
                      <span>·</span>
                      {c.date ? (
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {new Date(c.date).toLocaleString()}
                        </span>
                      ) : null}
                    </div>
                  </div>

                  <div className="flex items-center gap-2 self-start sm:self-auto shrink-0">
                    <button
                      type="button"
                      onClick={() => copyToClipboard(c.sha, c.sha)}
                      className="inline-flex items-center gap-1 rounded bg-muted px-2 py-1 font-mono text-xs text-muted-foreground hover:text-foreground transition-colors"
                      title="Copy full SHA"
                    >
                      {copiedSha === c.sha ? (
                        <Check className="h-3 w-3 text-success" />
                      ) : (
                        <Copy className="h-3 w-3" />
                      )}
                      <span>{c.shortSha}</span>
                    </button>
                    {c.htmlUrl ? (
                      <a
                        href={c.htmlUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="rounded p-1 text-muted-foreground hover:text-foreground transition-colors"
                        title="View on GitHub"
                      >
                        <ExternalLink className="h-3.5 w-3.5" />
                      </a>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : null}

      {/* Tab 2: BRANCHES */}
      {activeTab === 'branches' ? (
        <div className="space-y-4">
          {branchesLoading ? (
            <PageLoader />
          ) : !branches || branches.length === 0 ? (
            <div className="rounded-xl border bg-card p-12 text-center text-muted-foreground">
              No branches found.
            </div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {branches.map((b) => (
                <div
                  key={b.name}
                  className="flex items-center justify-between rounded-xl border bg-card p-4 shadow-sm hover:border-primary/40 transition-colors"
                  data-testid={`branch-card-${b.name}`}
                >
                  <div className="min-w-0 space-y-1">
                    <div className="flex items-center gap-1.5">
                      <GitBranch className="h-4 w-4 text-primary shrink-0" />
                      <span className="font-mono text-xs font-semibold truncate">{b.name}</span>
                      {b.name === status.defaultBranch ? (
                        <span className="rounded bg-primary/10 px-1.5 py-0.2 text-[10px] font-medium text-primary">
                          default
                        </span>
                      ) : null}
                      {b.isProtected ? <Lock className="h-3 w-3 text-amber-500" /> : null}
                    </div>
                    <div className="font-mono text-[11px] text-muted-foreground">
                      SHA: {b.commitSha.slice(0, 7)}
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="text-xs"
                    onClick={() => {
                      setSelectedBranch(b.name);
                      setActiveTab('commits');
                    }}
                  >
                    View commits
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : null}

      {/* Tab 3: TAGS & VERSIONS */}
      {activeTab === 'tags' ? (
        <div className="space-y-4">
          {tagsLoading ? (
            <PageLoader />
          ) : !tags || tags.length === 0 ? (
            <div className="rounded-xl border bg-card p-12 text-center text-muted-foreground">
              No releases or git tags found in this repository.
            </div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {tags.map((t) => (
                <div
                  key={t.name}
                  className="rounded-xl border bg-card p-4 shadow-sm space-y-3"
                  data-testid={`tag-card-${t.name}`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5">
                      <Tag className="h-4 w-4 text-success" />
                      <span className="font-mono font-semibold text-sm">{t.name}</span>
                    </div>
                    <span className="font-mono text-xs text-muted-foreground">
                      {t.commitSha.slice(0, 7)}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 pt-1 border-t text-xs">
                    {t.zipballUrl ? (
                      <a
                        href={t.zipballUrl}
                        className="rounded border px-2 py-1 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                        download
                      >
                        Download .zip
                      </a>
                    ) : null}
                    {t.tarballUrl ? (
                      <a
                        href={t.tarballUrl}
                        className="rounded border px-2 py-1 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                        download
                      >
                        Download .tar.gz
                      </a>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : null}

      {/* Tab 4: PULL REQUESTS */}
      {activeTab === 'prs' ? (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground">Filter:</span>
            {(['open', 'closed', 'all'] as const).map((filter) => (
              <button
                key={filter}
                type="button"
                onClick={() => setPrStateFilter(filter)}
                className={cn(
                  'rounded px-2.5 py-1 text-xs font-medium capitalize transition-colors',
                  prStateFilter === filter
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground hover:text-foreground',
                )}
              >
                {filter}
              </button>
            ))}
          </div>

          {prsLoading ? (
            <PageLoader />
          ) : !pullRequests || pullRequests.length === 0 ? (
            <div className="rounded-xl border bg-card p-12 text-center text-muted-foreground">
              No {prStateFilter !== 'all' ? prStateFilter : ''} pull requests found.
            </div>
          ) : (
            <div className="divide-y rounded-xl border bg-card shadow-sm">
              {pullRequests.map((pr) => (
                <div
                  key={pr.number}
                  className="flex flex-col gap-2 p-4 transition-colors hover:bg-muted/30 sm:flex-row sm:items-center sm:justify-between"
                  data-testid={`pr-row-${pr.number}`}
                >
                  <div className="min-w-0 space-y-1">
                    <div className="flex items-center gap-2">
                      <span
                        className={cn(
                          'inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-semibold',
                          pr.state === 'open'
                            ? 'bg-success/10 text-success'
                            : 'bg-purple-500/10 text-purple-600 dark:text-purple-400',
                        )}
                      >
                        <GitPullRequest className="h-3 w-3" />
                        #{pr.number} {pr.state}
                      </span>
                      {pr.draft ? (
                        <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                          Draft
                        </span>
                      ) : null}
                      <p className="font-semibold text-sm text-foreground">{pr.title}</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <span>by {pr.authorName}</span>
                      <span>·</span>
                      <span className="font-mono bg-muted/60 px-1 rounded">{pr.headBranch}</span>
                      <span>→</span>
                      <span className="font-mono bg-muted/60 px-1 rounded">{pr.baseBranch}</span>
                    </div>
                  </div>

                  {pr.htmlUrl ? (
                    <a
                      href={pr.htmlUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="self-start sm:self-auto inline-flex items-center gap-1 rounded-md border px-2.5 py-1 text-xs font-medium hover:bg-muted transition-colors shrink-0"
                    >
                      <span>View on GitHub</span>
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </div>
      ) : null}

      {/* Tab 5: CODE & DIRECTORY EXPLORER */}
      {activeTab === 'code' ? (
        <div className="space-y-4">
          {/* Breadcrumbs & Navigation Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/40 p-3 text-xs">
            <div className="flex flex-wrap items-center gap-1">
              <button
                type="button"
                onClick={() => navigateToFolder('')}
                className="font-medium hover:text-primary transition-colors flex items-center gap-1"
              >
                <FolderGit2 className="h-3.5 w-3.5" />
                <span>root</span>
              </button>
              {currentPath ? (
                <>
                  {currentPath.split('/').map((seg, idx, arr) => {
                    const subPath = arr.slice(0, idx + 1).join('/');
                    const isLast = idx === arr.length - 1;
                    return (
                      <React.Fragment key={subPath}>
                        <ChevronRight className="h-3 w-3 opacity-50" />
                        <button
                          type="button"
                          disabled={isLast && !activeFile}
                          onClick={() => navigateToFolder(subPath)}
                          className={cn(
                            'hover:text-primary transition-colors',
                            isLast && !activeFile ? 'font-semibold text-foreground' : 'text-muted-foreground',
                          )}
                        >
                          {seg}
                        </button>
                      </React.Fragment>
                    );
                  })}
                </>
              ) : null}
              {activeFile ? (
                <>
                  <ChevronRight className="h-3 w-3 opacity-50" />
                  <span className="font-semibold text-foreground">{activeFile.name}</span>
                </>
              ) : null}
            </div>

            {currentPath || activeFile ? (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={activeFile ? () => setActiveFile(null) : navigateUp}
                className="h-7 text-xs gap-1"
              >
                <ArrowLeft className="h-3 w-3" />
                Back
              </Button>
            ) : null}
          </div>

          {treeLoading ? (
            <PageLoader />
          ) : activeFile ? (
            /* File Content Preview */
            <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
              <div className="flex items-center justify-between border-b bg-muted/40 px-4 py-2 text-xs">
                <span className="font-mono font-medium">{activeFile.name}</span>
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground">
                    {(activeFile.size / 1024).toFixed(1)} KB
                  </span>
                  {activeFile.downloadUrl ? (
                    <a
                      href={activeFile.downloadUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="rounded border bg-background px-2 py-0.5 text-xs hover:bg-muted"
                      download
                    >
                      Raw
                    </a>
                  ) : null}
                </div>
              </div>
              <pre className="p-4 text-xs font-mono overflow-x-auto max-h-[600px] leading-relaxed bg-muted/10">
                <code>{activeFile.content ?? '(Binary or empty file)'}</code>
              </pre>
            </div>
          ) : !treeItems || treeItems.length === 0 ? (
            <div className="rounded-xl border bg-card p-12 text-center text-muted-foreground">
              Directory is empty.
            </div>
          ) : (
            /* Folder Tree Table */
            <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
              <table className="w-full text-left text-sm">
                <thead className="border-b bg-muted/40 text-xs font-semibold text-muted-foreground">
                  <tr>
                    <th className="px-4 py-2.5">Name</th>
                    <th className="px-4 py-2.5 text-right">Size</th>
                  </tr>
                </thead>
                <tbody className="divide-y text-xs font-mono">
                  {treeItems.map((item) => {
                    const isDir = item.type === 'dir';
                    return (
                      <tr
                        key={item.sha || item.name}
                        onClick={() => {
                          if (isDir) {
                            navigateToFolder(item.path);
                          } else {
                            setActiveFile(item);
                          }
                        }}
                        className="cursor-pointer transition-colors hover:bg-muted/40"
                        data-testid={`tree-item-${item.name}`}
                      >
                        <td className="flex items-center gap-2 px-4 py-2.5">
                          {isDir ? (
                            <Folder className="h-4 w-4 text-blue-500 shrink-0" />
                          ) : (
                            <FileCode className="h-4 w-4 text-muted-foreground shrink-0" />
                          )}
                          <span className={cn(isDir ? 'font-semibold text-foreground' : 'text-foreground/90')}>
                            {item.name}
                          </span>
                        </td>
                        <td className="px-4 py-2.5 text-right text-muted-foreground">
                          {isDir ? '—' : `${(item.size / 1024).toFixed(1)} KB`}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : null}

      {/* Disconnect Confirmation Modal */}
      <Modal
        open={confirmDisconnect}
        onClose={() => setConfirmDisconnect(false)}
        title="Disconnect GitHub repository?"
        description="This will remove the connection between this project and the GitHub repository. Feature flags will remain intact."
      >
        <div className="flex justify-end gap-2 pt-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => setConfirmDisconnect(false)}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={() => void handleDisconnect()}
            disabled={disconnectMutation.isPending}
            data-testid="github-confirm-disconnect"
          >
            {disconnectMutation.isPending ? 'Disconnecting…' : 'Disconnect'}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
