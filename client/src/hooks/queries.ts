import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, publicFetch } from '@/lib/api';
import type {
  ChangeRequest,
  Environment,
  FeatureRow,
  FlagCleanupPrResponse,
  GithubBranch,
  GithubCommit,
  GithubContent,
  GithubPrGraduationResult,
  GithubPrScanResult,
  GithubPullRequest,
  GithubStatus,
  GithubTag,
  PaginatedAuditLogs,
  Project,
  ProjectMember,
  StaleFlagReport,
  Webhook,
  WebhookDelivery,
  WebhookTestResult,
} from '@/types/domain';

export function useProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: async () => {
      const res = await apiFetch<{ projects: Project[] }>('/projects');
      return res.projects;
    },
  });
}

export function useCreateProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; description?: string }) =>
      apiFetch<{ project: Project }>('/projects', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects'] }),
  });
}

export function useProject(projectId: string | undefined) {
  return useQuery({
    queryKey: ['project', projectId],
    queryFn: async () => {
      const res = await apiFetch<{ project: Project }>(`/projects/${projectId}`);
      return res.project;
    },
    enabled: Boolean(projectId),
  });
}

export function useUpdateProject(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { name?: string; description?: string | null }) =>
      apiFetch<{ project: Project }>(`/projects/${projectId}`, {
        method: 'PATCH',
        body: JSON.stringify(data),
      }),
    onSuccess: (res) => {
      queryClient.setQueryData(['project', projectId], res.project);
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useArchiveProject(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<{ project: Project }>(`/projects/${projectId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useRotateApiKey(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<{ project: Project }>(`/projects/${projectId}/rotate-key`, { method: 'POST' }),
    onSuccess: (res) => {
      queryClient.setQueryData(['project', projectId], res.project);
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useFeatures(projectId: string) {
  return useQuery({
    queryKey: ['features', projectId],
    queryFn: async () => {
      const res = await apiFetch<{ features: FeatureRow[] }>(`/projects/${projectId}/features`);
      return res.features;
    },
    enabled: Boolean(projectId),
  });
}

export function useCreateFeature(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      key: string;
      name: string;
      description?: string;
      defaultEnabled?: boolean;
      githubBranch?: string;
      githubPrNumber?: number;
    }) =>
      apiFetch(`/projects/${projectId}/features`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['github-branches', projectId] });
    },
  });
}

export function useDeleteFeature(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (key: string) =>
      apiFetch<{ message: string; key: string }>(`/projects/${projectId}/features/${key}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['stale-flags', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['github-branches', projectId] });
    },
  });
}

export function useCreateEnvironment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; slug: string; color?: string }) =>
      apiFetch<{ environment: Environment }>(`/projects/${projectId}/environments`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useToggleFlag(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ envSlug, key, enabled }: { envSlug: string; key: string; enabled: boolean }) =>
      apiFetch(
        `/projects/${projectId}/flags/${encodeURIComponent(envSlug)}/${encodeURIComponent(key)}`,
        {
          method: 'PATCH',
          body: JSON.stringify({ enabled }),
        },
      ),
    onMutate: async (params) => {
      await queryClient.cancelQueries({ queryKey: ['features', projectId] });
      const previous = queryClient.getQueryData<FeatureRow[]>(['features', projectId]);

      queryClient.setQueryData<FeatureRow[]>(['features', projectId], (old) => {
        if (!old) {
          return old;
        }
        return old.map((f) => {
          if (f.key !== params.key) {
            return f;
          }
          return {
            ...f,
            states: {
              ...f.states,
              [params.envSlug]: params.enabled,
            },
          };
        });
      });

      return { previous };
    },
    onError: (_err, _params, context) => {
      if (context?.previous) {
        queryClient.setQueryData(['features', projectId], context.previous);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useUpdateFlagTargeting(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      envSlug,
      key,
      enabled,
      rolloutPercentage,
      rules,
    }: {
      envSlug: string;
      key: string;
      enabled?: boolean;
      rolloutPercentage?: number;
      rules?: string | null;
    }) =>
      apiFetch(
        `/projects/${projectId}/flags/${encodeURIComponent(envSlug)}/${encodeURIComponent(key)}/targeting`,
        {
          method: 'PUT',
          body: JSON.stringify({ enabled, rolloutPercentage, rules }),
        },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
      queryClient.invalidateQueries({ queryKey: ['github-branches', projectId] });
    },
  });
}

export function useAuditLogs(projectId: string, page: number) {
  return useQuery({
    queryKey: ['audit-logs', projectId, page],
    queryFn: () =>
      apiFetch<PaginatedAuditLogs>(`/projects/${projectId}/audit-logs?page=${page}&limit=20`),
    enabled: Boolean(projectId),
  });
}

/** Server returns `{ status: 'ok' }` or similar — keep loose for forward compatibility. */
interface HealthJson {
  status?: string;
}

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => publicFetch<HealthJson>('/health'),
    staleTime: 30_000,
    retry: 1,
  });
}

export function useGithubStatus(projectId: string | undefined) {
  return useQuery({
    queryKey: ['github-status', projectId],
    queryFn: () => apiFetch<GithubStatus>(`/projects/${projectId}/github/status`),
    enabled: Boolean(projectId),
    staleTime: 60_000,
  });
}

export function useConnectGithub(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { repository: string; accessToken?: string; defaultBranch?: string }) =>
      apiFetch<GithubStatus>(`/projects/${projectId}/github/connect`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: (res) => {
      queryClient.setQueryData(['github-status', projectId], res);
      queryClient.invalidateQueries({ queryKey: ['github-branches', projectId] });
      queryClient.invalidateQueries({ queryKey: ['github-tags', projectId] });
      queryClient.invalidateQueries({ queryKey: ['github-commits', projectId] });
      queryClient.invalidateQueries({ queryKey: ['github-pulls', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useDisconnectGithub(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch(`/projects/${projectId}/github/disconnect`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.setQueryData(['github-status', projectId], { connected: false });
      queryClient.removeQueries({ queryKey: ['github-branches', projectId] });
      queryClient.removeQueries({ queryKey: ['github-tags', projectId] });
      queryClient.removeQueries({ queryKey: ['github-commits', projectId] });
      queryClient.removeQueries({ queryKey: ['github-tree', projectId] });
      queryClient.removeQueries({ queryKey: ['github-pulls', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useGithubBranches(projectId: string, enabled = true) {
  return useQuery({
    queryKey: ['github-branches', projectId],
    queryFn: () => apiFetch<GithubBranch[]>(`/projects/${projectId}/github/branches`),
    enabled: Boolean(projectId) && enabled,
    staleTime: 30_000,
  });
}

export function useInstalledGithubRepos(projectId: string, enabled = true) {
  return useQuery({
    queryKey: ['github-installed-repos', projectId],
    queryFn: () => apiFetch<string[]>(`/projects/${projectId}/github/installed-repositories`),
    enabled: Boolean(projectId) && enabled,
    staleTime: 30_000,
  });
}

export function useGithubTags(projectId: string, enabled = true) {
  return useQuery({
    queryKey: ['github-tags', projectId],
    queryFn: () => apiFetch<GithubTag[]>(`/projects/${projectId}/github/tags`),
    enabled: Boolean(projectId) && enabled,
    staleTime: 60_000,
  });
}

export function useGithubCommits(projectId: string, branch?: string, enabled = true) {
  return useQuery({
    queryKey: ['github-commits', projectId, branch],
    queryFn: () => {
      const q = branch ? `?branch=${encodeURIComponent(branch)}&limit=30` : '?limit=30';
      return apiFetch<GithubCommit[]>(`/projects/${projectId}/github/commits${q}`);
    },
    enabled: Boolean(projectId) && enabled,
    staleTime: 30_000,
  });
}

export function useGithubTree(projectId: string, branch?: string, path = '', enabled = true) {
  return useQuery({
    queryKey: ['github-tree', projectId, branch, path],
    queryFn: () => {
      const params = new URLSearchParams();
      if (branch) params.set('branch', branch);
      if (path) params.set('path', path);
      const query = params.toString() ? `?${params.toString()}` : '';
      return apiFetch<GithubContent[]>(`/projects/${projectId}/github/tree${query}`);
    },
    enabled: Boolean(projectId) && enabled,
    staleTime: 60_000,
  });
}

export function useGithubPullRequests(projectId: string, state = 'all', enabled = true) {
  return useQuery({
    queryKey: ['github-pulls', projectId, state],
    queryFn: () => apiFetch<GithubPullRequest[]>(`/projects/${projectId}/github/pulls?state=${state}`),
    enabled: Boolean(projectId) && enabled,
    staleTime: 30_000,
  });
}

export function useLinkFeatureGithub(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      featureId,
      branch,
      prNumber,
    }: {
      featureId: string;
      branch?: string | null;
      prNumber?: number | null;
    }) =>
      apiFetch<FeatureRow>(`/projects/${projectId}/features/${featureId}/github`, {
        method: 'PATCH',
        body: JSON.stringify({ branch, prNumber }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
      queryClient.invalidateQueries({ queryKey: ['github-branches', projectId] });
    },
  });
}

export function useStaleFlags(projectId: string | undefined) {
  return useQuery({
    queryKey: ['stale-flags', projectId],
    queryFn: () => apiFetch<StaleFlagReport>(`/projects/${projectId}/stale-flags`),
    enabled: Boolean(projectId),
    staleTime: 30_000,
  });
}

export function useArchiveFlag(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (key: string) =>
      apiFetch<{ message: string }>(`/projects/${projectId}/flags/${key}/archive`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['stale-flags', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useUnarchiveFlag(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (key: string) =>
      apiFetch<{ message: string }>(`/projects/${projectId}/flags/${key}/unarchive`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['stale-flags', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useGenerateCleanupPr(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, createOnGithub }: { key: string; createOnGithub?: boolean }) =>
      apiFetch<FlagCleanupPrResponse>(
        `/projects/${projectId}/flags/${key}/cleanup-pr?createOnGithub=${Boolean(createOnGithub)}`,
        { method: 'POST' }
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stale-flags', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useScanPr(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ prNumber, postComment }: { prNumber: number; postComment?: boolean }) =>
      apiFetch<GithubPrScanResult>(
        `/projects/${projectId}/github/pulls/${prNumber}/scan?postComment=${postComment !== false}`,
        { method: 'POST' }
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useSimulateMergeGraduation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      prNumber,
      baseBranch,
      headBranch,
      prTitle,
    }: {
      prNumber: number;
      baseBranch?: string;
      headBranch?: string;
      prTitle?: string;
    }) => {
      const params = new URLSearchParams();
      if (baseBranch) params.set('baseBranch', baseBranch);
      if (headBranch) params.set('headBranch', headBranch);
      if (prTitle) params.set('prTitle', prTitle);
      return apiFetch<GithubPrGraduationResult>(
        `/projects/${projectId}/github/pulls/${prNumber}/simulate-merge?${params.toString()}`,
        { method: 'POST' }
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['stale-flags', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useChangeRequests(projectId: string | undefined, status?: string) {
  return useQuery({
    queryKey: ['change-requests', projectId, status],
    queryFn: () => {
      const q = status ? `?status=${status}` : '';
      return apiFetch<ChangeRequest[]>(`/projects/${projectId}/change-requests${q}`);
    },
    enabled: Boolean(projectId),
    staleTime: 15_000,
  });
}

export function useCreateChangeRequest(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      featureKey: string;
      environmentSlug: string;
      targetState: boolean;
      targetRollout?: number;
      targetRules?: string;
      reason?: string;
    }) =>
      apiFetch<ChangeRequest>(`/projects/${projectId}/change-requests`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['change-requests', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useReviewChangeRequest(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      changeRequestId,
      decision,
      comment,
    }: {
      changeRequestId: string;
      decision: 'APPROVE' | 'REJECT';
      comment?: string;
    }) =>
      apiFetch<ChangeRequest>(`/projects/${projectId}/change-requests/${changeRequestId}/review`, {
        method: 'POST',
        body: JSON.stringify({ decision, comment }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['change-requests', projectId] });
      queryClient.invalidateQueries({ queryKey: ['features', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useWebhooks(projectId: string | undefined) {
  return useQuery({
    queryKey: ['webhooks', projectId],
    queryFn: () => apiFetch<Webhook[]>(`/projects/${projectId}/webhooks`),
    enabled: Boolean(projectId),
    staleTime: 30_000,
  });
}

export function useCreateWebhook(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { url: string; secret?: string; description?: string; events?: string; enabled?: boolean }) =>
      apiFetch<Webhook>(`/projects/${projectId}/webhooks`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useDeleteWebhook(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (webhookId: string) =>
      apiFetch<{ message: string }>(`/projects/${projectId}/webhooks/${webhookId}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useTestWebhook(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (webhookId: string) =>
      apiFetch<WebhookTestResult>(`/projects/${projectId}/webhooks/${webhookId}/test`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks', projectId] });
    },
  });
}

export function useToggleEnvironmentApproval(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ environmentId, requireApproval }: { environmentId: string; requireApproval: boolean }) =>
      apiFetch<{ environment: Environment }>(`/projects/${projectId}/environments/${environmentId}/approval`, {
        method: 'PATCH',
        body: JSON.stringify({ requireApproval }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useProjectMembers(projectId: string | undefined) {
  return useQuery({
    queryKey: ['project-members', projectId],
    queryFn: () => apiFetch<ProjectMember[]>(`/projects/${projectId}/members`),
    enabled: Boolean(projectId),
    staleTime: 30_000,
  });
}

export function useInviteMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      name: string;
      email: string;
      role: 'ADMIN' | 'EDITOR' | 'VIEWER';
      activateImmediately?: boolean;
      tempPassword?: string;
    }) =>
      apiFetch<ProjectMember>(`/projects/${projectId}/members`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useActivateMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      apiFetch<ProjectMember>(`/projects/${projectId}/members/${memberId}/activate`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useUpdateMemberRole(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ memberId, role }: { memberId: string; role: 'ADMIN' | 'EDITOR' | 'VIEWER' }) =>
      apiFetch<ProjectMember>(`/projects/${projectId}/members/${memberId}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ role }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useRemoveMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      apiFetch<{ message: string }>(`/projects/${projectId}/members/${memberId}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}

export function useWebhookDeliveries(projectId: string | undefined, webhookId: string | undefined) {
  return useQuery({
    queryKey: ['webhook-deliveries', projectId, webhookId],
    queryFn: () => apiFetch<WebhookDelivery[]>(`/projects/${projectId}/webhooks/${webhookId}/deliveries`),
    enabled: Boolean(projectId && webhookId),
    staleTime: 10_000,
  });
}

export function useRetryWebhookDelivery(projectId: string, webhookId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (deliveryId: string) =>
      apiFetch<WebhookDelivery>(`/projects/${projectId}/webhooks/${webhookId}/deliveries/${deliveryId}/retry`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhook-deliveries', projectId, webhookId] });
      queryClient.invalidateQueries({ queryKey: ['webhooks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['audit-logs', projectId] });
    },
  });
}
