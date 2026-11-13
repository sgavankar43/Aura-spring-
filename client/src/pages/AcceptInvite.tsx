import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { AuthShell } from '@/components/auth/AuthShell';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { useAuth } from '@/contexts/AuthContext';
import { ApiError, apiFetch } from '@/lib/api';
import type { User } from '@/types/domain';
import { ShieldCheck, AlertCircle, ArrowRight } from 'lucide-react';

interface InviteInfo {
  token: string;
  projectId: string;
  projectName: string;
  email: string;
  name: string;
  role: string;
  status: string;
}

export default function AcceptInvite() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const navigate = useNavigate();
  const { login } = useAuth();

  const [invite, setInvite] = useState<InviteInfo | null>(null);
  const [isLoadingInvite, setIsLoadingInvite] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setFetchError('Missing invitation token in URL.');
      setIsLoadingInvite(false);
      return;
    }

    let isMounted = true;
    async function loadInvite() {
      try {
        const data = await apiFetch<InviteInfo>(`/auth/invite/${token}`, { skipAuth: true });
        if (isMounted) {
          setInvite(data);
          setName(data.name || '');
        }
      } catch (err) {
        if (isMounted) {
          const msg = err instanceof ApiError || err instanceof Error
            ? err.message
            : 'Invitation link is invalid or has expired.';
          setFetchError(msg);
        }
      } finally {
        if (isMounted) setIsLoadingInvite(false);
      }
    }

    loadInvite();
    return () => { isMounted = false; };
  }, [token]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setSubmitting(true);
    try {
      const resp = await apiFetch<{ token: string; user: User }>('/auth/invite/accept', {
        method: 'POST',
        body: JSON.stringify({
          token,
          password,
          name: name.trim(),
        }),
        skipAuth: true,
      });

      login(resp.token, resp.user);
      navigate(`/projects/${invite?.projectId || ''}`, {
        replace: true,
        state: { message: `Welcome to ${invite?.projectName || 'the project'}!` },
      });
    } catch (err) {
      const msg = err instanceof ApiError || err instanceof Error ? err.message : 'Failed to accept invitation.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoadingInvite) {
    return (
      <AuthShell title="Verifying Invitation" subtitle="Connecting to Aura secure access directory...">
        <div className="py-12 text-center text-xs text-muted-foreground animate-pulse font-mono">
          Verifying token cryptographic signature…
        </div>
      </AuthShell>
    );
  }

  if (fetchError || !invite) {
    return (
      <AuthShell title="Invalid Invitation" subtitle="This invitation link cannot be processed.">
        <div className="space-y-4">
          <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3.5 text-xs text-destructive flex items-start gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Unable to accept invitation</p>
              <p className="mt-0.5">{fetchError || 'Invitation token expired or already used.'}</p>
            </div>
          </div>
          <div className="pt-2 flex flex-col gap-2">
            <Link to="/login">
              <Button variant="outline" className="w-full text-xs">
                Go to Sign in
              </Button>
            </Link>
          </div>
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell
      title={`Join ${invite.projectName}`}
      subtitle={`You've been invited as a project ${invite.role.toLowerCase()}. Set your password to continue.`}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Project Invitation Banner */}
        <div className="rounded-lg border border-border bg-paper-2 p-3 space-y-1.5 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground font-medium">Invited Email:</span>
            <span className="font-mono font-semibold text-foreground">{invite.email}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground font-medium">Assigned Role:</span>
            <span className="inline-flex items-center gap-1 rounded bg-primary/10 text-primary border border-primary/20 px-1.5 py-0.5 font-mono text-[11px] font-semibold">
              <ShieldCheck className="h-3 w-3" />
              {invite.role}
            </span>
          </div>
        </div>

        {error && (
          <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            {error}
          </div>
        )}

        <div className="space-y-1.5">
          <Label htmlFor="invite-accept-name">Your Full Name</Label>
          <Input
            id="invite-accept-name"
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Jane Doe"
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="invite-accept-password">Choose Password</Label>
          <Input
            id="invite-accept-password"
            type="password"
            required
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
          />
          <p className="text-[10px] text-muted-foreground">Minimum 6 characters</p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="invite-accept-confirm">Confirm Password</Label>
          <Input
            id="invite-accept-confirm"
            type="password"
            required
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="••••••••"
          />
        </div>

        <Button type="submit" className="w-full mt-2 gap-2" disabled={submitting}>
          {submitting ? 'Activating account…' : 'Accept Invitation & Enter'}
          <ArrowRight className="h-3.5 w-3.5" />
        </Button>
      </form>
    </AuthShell>
  );
}
