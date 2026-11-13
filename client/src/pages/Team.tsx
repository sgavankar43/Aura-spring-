import { useMemo, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import {
  Check,
  CheckCircle2,
  Copy,
  Mail,
  MoreVertical,
  Plus,
  Search,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  UserCheck,
  UserPlus,
  Users,
  Zap,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Modal } from '@/components/ui/Modal';
import { useAuth } from '@/contexts/AuthContext';
import {
  useActivateMember,
  useInviteMember,
  useProjectMembers,
  useRemoveMember,
  useUpdateMemberRole,
} from '@/hooks/queries';
import type { Project, ProjectMember } from '@/types/domain';

type MemberRole = 'ADMIN' | 'EDITOR' | 'VIEWER';

const ROLE_BADGE: Record<MemberRole, { label: string; className: string; icon: typeof Shield }> = {
  ADMIN: {
    label: 'Admin',
    className: 'bg-primary/10 text-primary border-primary/20',
    icon: ShieldAlert,
  },
  EDITOR: {
    label: 'Editor',
    className: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20',
    icon: ShieldCheck,
  },
  VIEWER: {
    label: 'Viewer',
    className: 'bg-muted text-muted-foreground border-border',
    icon: Shield,
  },
};

export default function Team() {
  const { project } = useOutletContext<{ project: Project }>();
  const { user: currentUser } = useAuth();

  const { data: members = [], isLoading } = useProjectMembers(project.id);
  const inviteMutation = useInviteMember(project.id);
  const activateMutation = useActivateMember(project.id);
  const updateRoleMutation = useUpdateMemberRole(project.id);
  const removeMemberMutation = useRemoveMember(project.id);

  const [search, setSearch] = useState('');
  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [inviteName, setInviteName] = useState('');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<MemberRole>('EDITOR');
  const [activateImmediately, setActivateImmediately] = useState(false);
  const [tempPassword, setTempPassword] = useState('AuraWelcome123!');
  const [inviteSuccess, setInviteSuccess] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);
  const [copiedToken, setCopiedToken] = useState<string | null>(null);
  const [activatingId, setActivatingId] = useState<string | null>(null);
  const [credentialsModal, setCredentialsModal] = useState<{
    name: string;
    email: string;
    role: string;
    tempPassword?: string;
  } | null>(null);
  const [copiedPassword, setCopiedPassword] = useState(false);

  const filteredMembers = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return members;
    return members.filter(
      (m) => m.name.toLowerCase().includes(q) || m.email.toLowerCase().includes(q),
    );
  }, [members, search]);

  const stats = useMemo(() => {
    const adminCount = members.filter((m) => m.role === 'ADMIN').length;
    const editorCount = members.filter((m) => m.role === 'EDITOR').length;
    const viewerCount = members.filter((m) => m.role === 'VIEWER').length;
    return { total: members.length, adminCount, editorCount, viewerCount };
  }, [members]);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteName.trim() || !inviteEmail.trim()) return;

    setInviteError(null);
    try {
      const res = await inviteMutation.mutateAsync({
        name: inviteName.trim(),
        email: inviteEmail.trim().toLowerCase(),
        role: inviteRole,
        activateImmediately,
        tempPassword: activateImmediately ? tempPassword.trim() : undefined,
      });

      const memberName = inviteName.trim();
      const memberEmail = inviteEmail.trim().toLowerCase();
      const memberRole = inviteRole;

      setInviteName('');
      setInviteEmail('');
      setInviteRole('EDITOR');
      setActivateImmediately(false);
      setTempPassword('AuraWelcome123!');

      if (res.tempPassword) {
        setIsInviteOpen(false);
        setCredentialsModal({
          name: memberName,
          email: memberEmail,
          role: memberRole,
          tempPassword: res.tempPassword,
        });
      } else {
        setInviteSuccess(true);
        setTimeout(() => {
          setInviteSuccess(false);
          setIsInviteOpen(false);
        }, 1200);
      }
    } catch (err: any) {
      setInviteError(err?.message || 'Failed to invite team member');
    }
  };

  const handleActivateNow = async (member: ProjectMember) => {
    setActivatingId(member.id);
    try {
      const res = await activateMutation.mutateAsync(member.id);
      if (res.tempPassword) {
        setCredentialsModal({
          name: member.name,
          email: member.email,
          role: member.role,
          tempPassword: res.tempPassword,
        });
      }
    } catch (err: any) {
      alert(err?.message || 'Failed to activate member');
    } finally {
      setActivatingId(null);
    }
  };

  const handleUpdateRole = async (memberId: string, newRole: MemberRole) => {
    try {
      await updateRoleMutation.mutateAsync({ memberId, role: newRole });
      setActiveMenuId(null);
    } catch (err: any) {
      alert(err?.message || 'Failed to update member role');
    }
  };

  const handleRemoveMember = async (memberId: string) => {
    if (confirm('Are you sure you want to remove this member from the project?')) {
      try {
        await removeMemberMutation.mutateAsync(memberId);
        setActiveMenuId(null);
      } catch (err: any) {
        alert(err?.message || 'Failed to remove member');
      }
    }
  };

  const handleCopyInvite = (token: string) => {
    const inviteUrl = `${window.location.origin}/invite?token=${token}`;
    navigator.clipboard.writeText(inviteUrl);
    setCopiedToken(token);
    setTimeout(() => setCopiedToken(null), 2500);
  };

  return (
    <div className="space-y-6" data-testid="team-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold text-foreground">Team &amp; Access</h2>
          <p className="text-sm text-muted-foreground mt-1">
            Manage collaborators, assign role permissions, and control access for this project.
          </p>
        </div>
        <Button onClick={() => setIsInviteOpen(true)} className="gap-2 shrink-0">
          <UserPlus className="h-4 w-4" />
          Invite member
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <Card className="p-4 bg-card border-border">
          <div className="flex items-center gap-2 text-muted-foreground text-xs mb-1">
            <Users className="h-3.5 w-3.5 text-primary" />
            <span>Total members</span>
          </div>
          <span className="text-2xl font-mono font-semibold text-foreground">{stats.total}</span>
        </Card>
        <Card className="p-4 bg-card border-border">
          <div className="flex items-center gap-2 text-muted-foreground text-xs mb-1">
            <ShieldAlert className="h-3.5 w-3.5 text-primary" />
            <span>Admins</span>
          </div>
          <span className="text-2xl font-mono font-semibold text-foreground">
            {stats.adminCount}
          </span>
        </Card>
        <Card className="p-4 bg-card border-border">
          <div className="flex items-center gap-2 text-muted-foreground text-xs mb-1">
            <ShieldCheck className="h-3.5 w-3.5 text-emerald-500" />
            <span>Editors</span>
          </div>
          <span className="text-2xl font-mono font-semibold text-foreground">
            {stats.editorCount}
          </span>
        </Card>
        <Card className="p-4 bg-card border-border">
          <div className="flex items-center gap-2 text-muted-foreground text-xs mb-1">
            <Shield className="h-3.5 w-3.5 text-muted-foreground" />
            <span>Viewers</span>
          </div>
          <span className="text-2xl font-mono font-semibold text-foreground">
            {stats.viewerCount}
          </span>
        </Card>
      </div>

      {/* Filter / Search Bar */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or email…"
            className="pl-9 h-9 text-xs"
          />
        </div>
      </div>

      {/* Member Table */}
      <div className="rounded-xl border border-border bg-card overflow-hidden">
        {isLoading ? (
          <div className="py-12 text-center text-xs text-muted-foreground font-mono">
            Loading team roster…
          </div>
        ) : filteredMembers.length === 0 ? (
          <div className="py-12 text-center text-xs text-muted-foreground">
            No team members found matching your search.
          </div>
        ) : (
          <div className="divide-y divide-border">
          {filteredMembers.map((member) => {
            const roleInfo = ROLE_BADGE[member.role];
            const isOwner = member.id === 'owner-1' || member.email === currentUser?.email;
            const Icon = roleInfo.icon;

            return (
              <div
                key={member.id}
                className="flex items-center justify-between p-4 sm:px-6 hover:bg-paper transition-colors group"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-paper-2 border border-border text-sm font-semibold text-primary">
                    {member.name.charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="truncate text-xs font-semibold text-foreground">
                        {member.name}
                      </p>
                      {isOwner && (
                        <span className="rounded bg-paper-2 border border-border px-1.5 py-0.2 text-[10px] font-mono text-muted-foreground">
                          Owner
                        </span>
                      )}
                      {member.status === 'PENDING_INVITE' && (
                        <span className="rounded bg-amber-500/10 border border-amber-500/20 px-1.5 py-0.2 text-[10px] font-mono text-amber-600 dark:text-amber-400">
                          Pending Invite
                        </span>
                      )}
                    </div>
                    <p className="truncate text-[11px] text-muted-foreground mt-0.5">
                      {member.email}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {member.status === 'PENDING_INVITE' && (
                    <>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 text-xs gap-1 font-mono text-amber-600 dark:text-amber-400 border-amber-500/30 hover:bg-amber-500/10"
                        disabled={activatingId === member.id}
                        onClick={() => handleActivateNow(member)}
                        title="Immediately provision credentials and activate this user account"
                      >
                        <Zap className="h-3 w-3 fill-amber-500 text-amber-500" />
                        <span>{activatingId === member.id ? 'Activating…' : 'Activate Now'}</span>
                      </Button>

                      {member.inviteToken && (
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 text-xs gap-1 font-mono"
                          onClick={() => handleCopyInvite(member.inviteToken!)}
                          title="Copy shareable self-service invite link"
                        >
                          {copiedToken === member.inviteToken ? (
                            <>
                              <Check className="h-3 w-3 text-emerald-500" />
                              <span>Copied</span>
                            </>
                          ) : (
                            <>
                              <Copy className="h-3 w-3 text-muted-foreground" />
                              <span>Copy link</span>
                            </>
                          )}
                        </Button>
                      )}
                    </>
                  )}

                  <span
                    className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-mono font-medium ${roleInfo.className}`}
                  >
                    <Icon className="h-3 w-3" />
                    {roleInfo.label}
                  </span>

                  <div className="relative">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 text-muted-foreground hover:text-foreground"
                      onClick={() =>
                        setActiveMenuId((current) => (current === member.id ? null : member.id))
                      }
                      disabled={isOwner}
                    >
                      <MoreVertical className="h-4 w-4" />
                    </Button>

                    {activeMenuId === member.id && !isOwner && (
                      <div className="absolute right-0 top-full mt-1 z-20 w-44 rounded-lg border border-border bg-card p-1 shadow-lg space-y-0.5">
                        <div className="px-2 py-1 text-[10px] font-mono text-muted-foreground uppercase tracking-wide">
                          Change Role
                        </div>
                        {(['ADMIN', 'EDITOR', 'VIEWER'] as MemberRole[]).map((r) => (
                          <button
                            key={r}
                            type="button"
                            onClick={() => handleUpdateRole(member.id, r)}
                            className={`w-full flex items-center justify-between rounded px-2 py-1.5 text-xs text-left transition-colors ${
                              member.role === r
                                ? 'bg-primary/10 text-primary font-medium'
                                : 'text-foreground hover:bg-paper'
                            }`}
                          >
                            <span>{ROLE_BADGE[r].label}</span>
                            {member.role === r && <Check className="h-3.5 w-3.5" />}
                          </button>
                        ))}
                        <div className="my-1 border-t border-border" />
                        <button
                          type="button"
                          onClick={() => handleRemoveMember(member.id)}
                          className="w-full flex items-center gap-2 rounded px-2 py-1.5 text-xs text-destructive hover:bg-destructive/10 text-left transition-colors"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          <span>Remove member</span>
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
        )}
      </div>

      {/* Role Matrix Card */}
      <div className="rounded-xl border border-border bg-paper-2 p-5 space-y-3">
        <h3 className="text-xs font-semibold text-foreground uppercase tracking-wide">
          Role Permission Matrix
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-1">
          <div className="rounded-lg border border-border bg-paper p-3 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-primary">
              <ShieldAlert className="h-3.5 w-3.5" />
              <span>Admin</span>
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              Full control: create/delete flags, rotate API keys, configure GitHub integration, invite
              or remove team members, and manage environments.
            </p>
          </div>
          <div className="rounded-lg border border-border bg-paper p-3 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
              <ShieldCheck className="h-3.5 w-3.5" />
              <span>Editor</span>
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              Operational control: toggle flags across environments, create new flags, use
              templates, and view audit trail. Cannot rotate keys or modify team roles.
            </p>
          </div>
          <div className="rounded-lg border border-border bg-paper p-3 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
              <Shield className="h-3.5 w-3.5" />
              <span>Viewer</span>
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              Read-only visibility: inspect flag evaluation states, view environments and analytics,
              read audit events. Cannot toggle flags or alter project settings.
            </p>
          </div>
        </div>
      </div>

      {/* Invite Member Modal */}
      <Modal open={isInviteOpen} onClose={() => setIsInviteOpen(false)} title="Invite team member">
        {inviteSuccess ? (
          <div className="py-6 text-center space-y-2">
            <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
              <UserCheck className="h-5 w-5" />
            </div>
            <p className="text-sm font-semibold text-foreground">Invitation sent</p>
            <p className="text-xs text-muted-foreground">
              The member has been added to this project's access list.
            </p>
          </div>
        ) : (
          <form onSubmit={handleInvite} className="space-y-4">
            {inviteError && (
              <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
                {inviteError}
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="invite-name">Full name</Label>
              <Input
                id="invite-name"
                value={inviteName}
                onChange={(e) => setInviteName(e.target.value)}
                placeholder="Jane Developer"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="invite-email">Email address</Label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                <Input
                  id="invite-email"
                  type="email"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="jane@company.com"
                  className="pl-9"
                  required
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="invite-role">Project role</Label>
              <select
                id="invite-role"
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as MemberRole)}
                className="w-full rounded-md border border-border bg-paper-2 px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ADMIN">Admin — Full project access</option>
                <option value="EDITOR">Editor — Toggle &amp; create flags</option>
                <option value="VIEWER">Viewer — Read-only access</option>
              </select>
            </div>

            {/* Instant Account Provisioning */}
            <div className="rounded-lg border border-border bg-paper-2 p-3 space-y-2">
              <label className="flex items-start gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={activateImmediately}
                  onChange={(e) => setActivateImmediately(e.target.checked)}
                  className="mt-0.5 rounded border-border text-primary focus:ring-primary"
                />
                <div>
                  <p className="text-xs font-medium text-foreground flex items-center gap-1.5">
                    <Zap className="h-3 w-3 text-amber-500 fill-amber-500" />
                    Activate account immediately
                  </p>
                  <p className="text-[11px] text-muted-foreground mt-0.5">
                    Generates user login credentials right away without waiting for self-service invite link acceptance.
                  </p>
                </div>
              </label>
              {activateImmediately && (
                <div className="pt-2 border-t border-border space-y-1">
                  <Label htmlFor="temp-password" className="text-[11px]">Temporary Password</Label>
                  <Input
                    id="temp-password"
                    type="text"
                    value={tempPassword}
                    onChange={(e) => setTempPassword(e.target.value)}
                    className="font-mono text-xs"
                    required
                  />
                </div>
              )}
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={() => setIsInviteOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" className="gap-1.5">
                <Plus className="h-3.5 w-3.5" />
                {activateImmediately ? 'Provision & Add' : 'Send invite'}
              </Button>
            </div>
          </form>
        )}
      </Modal>

      {/* Credentials / Instant Activation Notification Modal */}
      <Modal
        open={credentialsModal !== null}
        onClose={() => {
          setCredentialsModal(null);
          setCopiedPassword(false);
        }}
        title="Account Provisioned & Ready"
      >
        {credentialsModal && (
          <div className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-medium text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 rounded-md p-2.5">
              <CheckCircle2 className="h-4 w-4 shrink-0" />
              <span>User account is active! Share these credentials with the team member.</span>
            </div>

            <div className="rounded-lg border border-border bg-paper-2 p-3.5 space-y-2.5 text-xs">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Member:</span>
                <span className="font-semibold text-foreground">{credentialsModal.name}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Login Email:</span>
                <span className="font-mono font-medium text-foreground">{credentialsModal.email}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Project Role:</span>
                <span className="font-mono text-primary font-semibold">{credentialsModal.role}</span>
              </div>
              <div className="pt-2 border-t border-border flex items-center justify-between">
                <span className="text-muted-foreground">Temporary Password:</span>
                <div className="flex items-center gap-2">
                  <code className="bg-background px-2 py-1 rounded border border-border font-mono text-xs text-foreground font-semibold">
                    {credentialsModal.tempPassword}
                  </code>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs gap-1 font-mono"
                    onClick={() => {
                      navigator.clipboard.writeText(credentialsModal.tempPassword || '');
                      setCopiedPassword(true);
                      setTimeout(() => setCopiedPassword(false), 2000);
                    }}
                  >
                    {copiedPassword ? (
                      <>
                        <Check className="h-3 w-3 text-emerald-500" />
                        <span>Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="h-3 w-3 text-muted-foreground" />
                        <span>Copy</span>
                      </>
                    )}
                  </Button>
                </div>
              </div>
            </div>

            <div className="text-[11px] text-muted-foreground bg-paper rounded-md p-2.5 border border-border">
              💡 The user can now visit the Aura login page (<code>/login</code>) and enter their email and temporary password to access the project immediately.
            </div>

            <div className="flex justify-end pt-2">
              <Button
                type="button"
                onClick={() => {
                  setCredentialsModal(null);
                  setCopiedPassword(false);
                }}
              >
                Done
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
