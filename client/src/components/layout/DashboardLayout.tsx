import {
  Activity,
  BarChart2,
  Boxes,
  Flag,
  FolderGit2,
  Key,
  LayoutDashboard,
  LayoutGrid,
  LogOut,
  Menu,
  ScrollText,
  Settings,
  Shapes,
  Users,
  X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { useAuth } from '@/contexts/AuthContext';
import { useHealth } from '@/hooks/queries';
import { cn } from '@/lib/utils';

function HealthBadge() {
  const { data, isError, isPending } = useHealth();

  if (isPending) {
    return (
      <span
        className="inline-flex items-center gap-1.5 text-xs text-muted-foreground"
        data-testid="health-status"
      >
        <span className="h-1.5 w-1.5 rounded-full bg-muted-foreground animate-pulse" />
        Checking API
      </span>
    );
  }

  if (isError || !data) {
    return (
      <span
        className="inline-flex items-center gap-1.5 text-xs text-destructive"
        data-testid="health-status"
      >
        <span className="h-1.5 w-1.5 rounded-full bg-destructive" />
        Control plane unreachable
      </span>
    );
  }

  const healthy = data.status === 'healthy' || data.status === 'ok';

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 text-xs',
        healthy ? 'text-muted-foreground' : 'text-amber-500',
      )}
      data-testid="health-status"
    >
      <Activity className={cn('h-3.5 w-3.5', healthy ? 'text-emerald-500' : 'text-amber-500')} />
      {healthy ? 'API operational' : `Status: ${data.status ?? 'unknown'}`}
    </span>
  );
}

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center gap-2.5 rounded-md px-3 py-2 text-xs font-medium transition-colors duration-150',
    isActive
      ? 'bg-primary text-primary-foreground font-semibold shadow-sm'
      : 'text-muted-foreground hover:bg-paper hover:text-foreground',
  );

export default function DashboardLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  const projectMatch = location.pathname.match(/^\/projects\/([^/]+)/);
  const projectId = projectMatch?.[1];
  const insideProject = Boolean(projectId) && projectId !== undefined;

  return (
    <div className="flex min-h-screen w-full bg-background" data-testid="dashboard-layout">
      {mobileNavOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-40 bg-black/60 md:hidden"
          aria-label="Close menu"
          onClick={() => setMobileNavOpen(false)}
        />
      ) : null}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-60 flex-col border-r border-border bg-card transition-transform md:static md:translate-x-0',
          mobileNavOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0',
        )}
        data-testid="sidebar"
      >
        <div className="flex items-center justify-between border-b border-border p-4 md:block">
          <div>
            <h1 className="text-sm font-semibold tracking-tight text-foreground">
              Aura Control Plane
            </h1>
            <p className="text-[11px] text-muted-foreground">Feature flag coordination</p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={() => setMobileNavOpen(false)}
            aria-label="Close sidebar"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>

        <nav className="flex flex-1 flex-col gap-1 p-3">
          <NavLink to="/projects" className={navLinkClass} data-testid="nav-projects" end>
            <LayoutDashboard className="h-4 w-4 shrink-0" />
            Projects
          </NavLink>

          {insideProject ? (
            <>
              <div className="my-2 border-t border-border pt-2">
                <p className="px-3 pb-1 text-[11px] font-medium text-muted-foreground">
                  Project navigation
                </p>
              </div>
              <NavLink
                to={`/projects/${projectId}/overview`}
                className={navLinkClass}
                data-testid="nav-overview"
              >
                <LayoutGrid className="h-4 w-4 shrink-0" />
                Overview
              </NavLink>
              <NavLink
                to={`/projects/${projectId}`}
                className={navLinkClass}
                data-testid="nav-flags"
                end
              >
                <Flag className="h-4 w-4 shrink-0" />
                Feature flags
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/analytics`}
                className={navLinkClass}
                data-testid="nav-analytics"
              >
                <BarChart2 className="h-4 w-4 shrink-0" />
                Analytics
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/environments`}
                className={navLinkClass}
                data-testid="nav-environments"
              >
                <Boxes className="h-4 w-4 shrink-0" />
                Environments
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/api-keys`}
                className={navLinkClass}
                data-testid="nav-api-keys"
              >
                <Key className="h-4 w-4 shrink-0" />
                API Keys
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/github`}
                className={navLinkClass}
                data-testid="nav-github"
              >
                <FolderGit2 className="h-4 w-4 shrink-0" />
                GitHub repository
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/templates`}
                className={navLinkClass}
                data-testid="nav-templates"
              >
                <Shapes className="h-4 w-4 shrink-0" />
                Templates
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/team`}
                className={navLinkClass}
                data-testid="nav-team"
              >
                <Users className="h-4 w-4 shrink-0" />
                Team
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/audit`}
                className={navLinkClass}
                data-testid="nav-audit"
              >
                <ScrollText className="h-4 w-4 shrink-0" />
                Audit log
              </NavLink>
              <NavLink
                to={`/projects/${projectId}/settings`}
                className={navLinkClass}
                data-testid="nav-settings"
              >
                <Settings className="h-4 w-4 shrink-0" />
                Project settings
              </NavLink>
            </>
          ) : null}
        </nav>

        <div className="border-t border-border p-3">
          <div className="flex items-center gap-2 rounded-md bg-paper p-2 border border-border">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-primary text-xs font-semibold text-primary-foreground">
              {user?.name.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-foreground">{user?.name}</p>
              <p className="truncate text-[11px] text-muted-foreground">{user?.email}</p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7 shrink-0 text-muted-foreground hover:text-destructive"
              onClick={logout}
              title="Sign out"
              data-testid="logout"
            >
              <LogOut className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </aside>

      <div className="flex min-h-screen min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border bg-background px-4 md:px-8">
          <div className="flex items-center gap-3">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="md:hidden"
              onClick={() => setMobileNavOpen(true)}
              aria-label="Open menu"
              data-testid="mobile-menu"
            >
              <Menu className="h-4 w-4" />
            </Button>
            <HealthBadge />
          </div>
          <ThemeToggle />
        </header>

        <main className="flex-1 overflow-auto p-4 md:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
