import { useEffect, useMemo, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import {
  Check,
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
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Modal } from '@/components/ui/Modal';
import { useAuth } from '@/contexts/AuthContext';
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

  const storageKey = `aura_project_members_${project.id}`;

  const [members, setMembers] = useState<ProjectMember[]>(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved) {
        return JSON.parse(saved) as ProjectMember[];
      }
    } catch {
      // ignore
    }
    // Default seed members
    return [
      {
        id: 'owner-1',
        projectId: project.id,
        name: currentUser?.name || 'System Administrator',
        email: currentUser?.email || 'admin@aura.dev',
        role: 'ADMIN',
        createdAt: project.createdAt,
      },
    ];
  });

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(members));
    } catch {
      // ignore
    }
  }, [members, storageKey]);

  const [search, setSearch] = useState('');
  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [inviteName, setInviteName] = useState('');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<MemberRole>('EDITOR');
  const [inviteSuccess, setInviteSuccess] = useState(false);
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

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

  const handleInvite = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteName.trim() || !inviteEmail.trim()) return;

    const newMember: ProjectMember = {
      id: `member-${Date.now()}`,
      projectId: project.id,
      name: inviteName.trim(),
      email: inviteEmail.trim().toLowerCase(),
      role: inviteRole,
      createdAt: new Date().toISOString(),
    };

    setMembers((prev) => [...prev, newMember]);
    setInviteName('');
    setInviteEmail('');
    setInviteRole('EDITOR');
    setInviteSuccess(true);
    setTimeout(() => {
      setInviteSuccess(false);
      setIsInviteOpen(false);
    }, 1200);
  };

  const handleUpdateRole = (id: string, newRole: MemberRole) => {
    setMembers((prev) => prev.map((m) => (m.id === id ? { ...m, role: newRole } : m)));
    setActiveMenuId(null);
  };

  const handleRemoveMember = (id: string) => {
    if (members.length <= 1) return;
    setMembers((prev) => prev.filter((m) => m.id !== id));
    setActiveMenuId(null);
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
                    </div>
                    <p className="truncate text-[11px] text-muted-foreground mt-0.5">
                      {member.email}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-3">
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
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={() => setIsInviteOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" className="gap-1.5">
                <Plus className="h-3.5 w-3.5" />
                Send invite
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
