/** Domain types aligned with Aura REST API responses. */

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
}

export interface Environment {
  id: string;
  name: string;
  slug: string;
  color: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  projectId: string;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  apiKey: string;
  createdAt: string;
  updatedAt: string;
  archivedAt: string | null;
  environments?: Environment[];
  features?: unknown[];
}

export interface FeatureRow {
  id: string;
  key: string;
  name: string;
  description: string | null;
  defaultEnabled: boolean;
  githubBranch?: string | null;
  githubPrNumber?: number | null;
  states: Record<string, boolean>;
}

export interface AuditLogEntry {
  id: string;
  action: string;
  entity: string;
  entityId: string;
  userId: string | null;
  userName?: string | null;
  userEmail?: string | null;
  projectId: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

export interface PaginatedAuditLogs {
  data: AuditLogEntry[];
  meta: {
    page: number;
    total: number;
    totalPages: number;
  };
}

export interface HealthStatus {
  status: string;
}

export interface GithubStatus {
  connected: boolean;
  repoOwner?: string;
  repoName?: string;
  repoFullName?: string;
  repoUrl?: string;
  description?: string;
  defaultBranch?: string;
  isPrivate?: boolean;
  starsCount?: number;
  forksCount?: number;
  openIssuesCount?: number;
  connectedAt?: string;
  updatedAt?: string;
}

export interface GithubBranch {
  name: string;
  commitSha: string;
  isProtected: boolean;
}

export interface GithubTag {
  name: string;
  commitSha: string;
  tarballUrl?: string | null;
  zipballUrl?: string | null;
}

export interface GithubCommit {
  sha: string;
  shortSha: string;
  message: string;
  authorName: string;
  authorEmail?: string | null;
  authorAvatar?: string | null;
  date?: string | null;
  htmlUrl?: string | null;
}

export interface GithubContent {
  name: string;
  path: string;
  sha: string;
  size: number;
  type: 'file' | 'dir';
  downloadUrl?: string | null;
  content?: string | null;
  encoding?: string | null;
}

export interface GithubPullRequest {
  number: number;
  title: string;
  state: 'open' | 'closed' | string;
  headBranch: string;
  baseBranch: string;
  authorName: string;
  authorAvatar?: string | null;
  createdAt?: string | null;
  htmlUrl?: string | null;
  draft: boolean;
}
