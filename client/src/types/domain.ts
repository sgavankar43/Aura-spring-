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
  requireApproval?: boolean;
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

export interface TargetingRule {
  attribute: string;
  operator: string;
  value: string;
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
  rolloutPercentages?: Record<string, number>;
  targetingRules?: Record<string, string>;
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
  isDefault?: boolean;
  isOccupied?: boolean;
  occupiedByFeatureKey?: string | null;
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

export interface ProjectMember {
  id: string;
  projectId: string;
  userId?: string | null;
  name: string;
  email: string;
  role: 'ADMIN' | 'EDITOR' | 'VIEWER';
  status: 'ACTIVE' | 'PENDING_INVITE';
  inviteToken?: string | null;
  tempPassword?: string | null;
  createdAt: string;
  updatedAt?: string;
}

export interface WebhookDelivery {
  id: string;
  webhookId: string;
  projectId: string;
  eventType: string;
  payloadJson: string;
  responseStatus?: number | null;
  responseBody?: string | null;
  durationMs?: number | null;
  success: boolean;
  attemptNumber: number;
  createdAt: string;
}

export interface StaleFlagItem {
  id: string;
  key: string;
  name: string;
  description?: string | null;
  status: 'READY_FOR_RETIREMENT' | 'DORMANT_INACTIVE' | 'IN_ROLLOUT' | 'HEALTHY_ACTIVE' | 'ARCHIVED';
  daysSinceLastModified: number;
  lastModified: string;
  archivedAt?: string | null;
  fullyRolledOut: boolean;
  allDisabled: boolean;
  states: Record<string, boolean>;
  rolloutPercentages: Record<string, number>;
  targetingRules?: Record<string, string>;
  referencedFilesCount: number;
  referencedFiles: string[];
  recommendation: string;
}

export interface StaleFlagReport {
  totalFlags: number;
  staleCount: number;
  dormantCount: number;
  activeCount: number;
  techDebtScore: number;
  flags: StaleFlagItem[];
}

export interface GithubPrScanResult {
  prNumber: number;
  prTitle: string;
  headBranch: string;
  baseBranch: string;
  repoOwner: string;
  repoName: string;
  filesTouchedCount: number;
  referencedFlags: Array<{
    key: string;
    name: string;
    description?: string;
    states: Record<string, boolean>;
    rolloutPercentages: Record<string, number>;
    targetingRules?: Record<string, string>;
    occurrencesInFiles: string[];
  }>;
  commentMarkdown: string;
  commentPosted: boolean;
  commentUrl?: string | null;
}

export interface GithubPrGraduationResult {
  prNumber: number;
  prTitle: string;
  headBranch: string;
  baseBranch: string;
  targetEnvironment: string;
  graduatedFlags: string[];
  graduationDetails: Record<string, any>;
  commentPosted: boolean;
  message: string;
}

export interface FlagCleanupPrResponse {
  flagKey: string;
  branchName: string;
  prTitle: string;
  prBody: string;
  diffPreview: string;
  prUrl?: string | null;
  status: string;
  affectedFiles: string[];
}

export interface ChangeRequest {
  id: string;
  projectId: string;
  featureId: string;
  featureKey: string;
  featureName: string;
  environmentId: string;
  environmentSlug: string;
  environmentName: string;
  requesterId: string;
  requesterName: string;
  requesterEmail: string;
  reviewerId?: string | null;
  reviewerName?: string | null;
  targetState: boolean;
  targetRollout: number;
  targetRules?: string | null;
  reason?: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'APPLIED';
  createdAt: string;
  reviewedAt?: string | null;
}

export interface Webhook {
  id: string;
  projectId: string;
  url: string;
  description?: string | null;
  events: string;
  enabled: boolean;
  lastStatus?: number | null;
  lastTrigger?: string | null;
  createdAt: string;
}

export interface WebhookTestResult {
  success: boolean;
  statusCode: number;
  responseBody?: string | null;
  latencyMs: number;
  message: string;
}
