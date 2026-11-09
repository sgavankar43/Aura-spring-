import { Layers } from 'lucide-react';
import { type ReactNode } from 'react';
import { Link } from 'react-router-dom';

export function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <div data-testid="auth-shell" className="relative min-h-screen bg-background flex flex-col justify-center px-4 py-12">
      <div className="mx-auto w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <Link
            to="/"
            className="mb-4 inline-flex items-center gap-2 text-foreground hover:opacity-90 transition-opacity"
          >
            <span className="flex h-8 w-8 items-center justify-center rounded-md border border-border bg-paper-2 text-primary">
              <Layers className="h-4 w-4" aria-hidden />
            </span>
            <span className="text-sm font-semibold tracking-tight text-foreground">
              Aura
            </span>
          </Link>
          <h1 className="text-xl font-medium tracking-tight text-foreground">
            {title}
          </h1>
          <p className="mt-1.5 text-xs text-muted-foreground leading-relaxed">
            {subtitle}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card p-6 shadow-sm">
          {children}
        </div>
      </div>
    </div>
  );
}
