import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { Check, Copy, Eye, EyeOff, Key, RefreshCw, ShieldCheck } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useRotateApiKey } from '@/hooks/queries';
import type { Project } from '@/types/domain';

function MaskedKey({ apiKey }: { apiKey: string }) {
  const [revealed, setRevealed] = useState(false);
  const [copied, setCopied] = useState(false);

  const display = revealed ? apiKey : `${apiKey.slice(0, 12)}${'•'.repeat(24)}`;

  const handleCopy = async () => {
    await navigator.clipboard.writeText(apiKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex items-center gap-2 rounded-lg border border-border bg-paper-3 px-4 py-3 font-mono text-sm">
      <code className="flex-1 text-foreground break-all select-all">{display}</code>
      <button
        type="button"
        onClick={() => setRevealed((v) => !v)}
        className="text-muted-foreground hover:text-foreground transition-colors p-1"
        title={revealed ? 'Hide key' : 'Reveal key'}
      >
        {revealed ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
      <button
        type="button"
        onClick={handleCopy}
        className="text-muted-foreground hover:text-foreground transition-colors p-1"
        title="Copy to clipboard"
      >
        {copied ? (
          <Check className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
        ) : (
          <Copy className="h-4 w-4" />
        )}
      </button>
    </div>
  );
}

export default function ApiKeys() {
  const { project } = useOutletContext<{ project: Project }>();
  const rotateKey = useRotateApiKey(project.id);
  const [confirmRotate, setConfirmRotate] = useState(false);
  const [rotated, setRotated] = useState(false);

  const handleRotate = async () => {
    if (!confirmRotate) {
      setConfirmRotate(true);
      return;
    }
    await rotateKey.mutateAsync();
    setConfirmRotate(false);
    setRotated(true);
    setTimeout(() => setRotated(false), 4000);
  };

  return (
    <div className="space-y-6" data-testid="api-keys-page">
      <div>
        <h2 className="text-xl font-semibold text-foreground">API Keys</h2>
        <p className="text-sm text-muted-foreground mt-1">
          Manage the credentials used to authenticate SDK calls and webhook integrations for this project.
        </p>
      </div>

      {/* Current key card */}
      <div className="rounded-xl border border-border bg-card p-6 space-y-5">
        <div className="flex items-center gap-2">
          <div className="h-8 w-8 rounded-lg bg-paper-2 border border-border flex items-center justify-center text-primary">
            <Key className="h-4 w-4" />
          </div>
          <div>
            <p className="text-sm font-semibold text-foreground">Project API key</p>
            <p className="text-[11px] text-muted-foreground">
              Created {new Date(project.createdAt).toLocaleDateString()}
            </p>
          </div>
          <span className="ml-auto inline-flex items-center gap-1.5 rounded bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 text-[11px] font-mono text-emerald-600 dark:text-emerald-400">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            Active
          </span>
        </div>

        <MaskedKey apiKey={project.apiKey} />

        {rotated && (
          <div className="flex items-center gap-2 rounded-lg border border-emerald-500/20 bg-emerald-500/5 px-4 py-3 text-xs text-emerald-600 dark:text-emerald-400">
            <Check className="h-4 w-4 shrink-0" />
            Key rotated successfully. Update your SDK configuration with the new key above.
          </div>
        )}

        {confirmRotate && (
          <div className="rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3 text-xs text-destructive space-y-3">
            <p className="font-medium">Are you sure you want to rotate this key?</p>
            <p>All existing integrations using the current key will stop working immediately. You'll need to update them with the new key.</p>
            <div className="flex gap-3">
              <Button
                size="sm"
                onClick={handleRotate}
                className="bg-destructive text-destructive-foreground hover:bg-destructive/90 gap-1.5"
                disabled={rotateKey.isPending}
              >
                <RefreshCw className={`h-3.5 w-3.5 ${rotateKey.isPending ? 'motion-safe:animate-spin' : ''}`} />
                {rotateKey.isPending ? 'Rotating…' : 'Confirm rotate'}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setConfirmRotate(false)}>
                Cancel
              </Button>
            </div>
          </div>
        )}

        {!confirmRotate && (
          <div className="flex items-center gap-3 pt-1">
            <Button
              variant="secondary"
              size="sm"
              onClick={handleRotate}
              disabled={rotateKey.isPending}
              className="gap-1.5"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              Rotate key
            </Button>
            <span className="text-[11px] text-muted-foreground">
              Generates a new key and immediately invalidates the current one.
            </span>
          </div>
        )}
      </div>

      {/* SDK usage */}
      <div className="rounded-xl border border-border bg-card overflow-hidden">
        <div className="border-b border-border px-5 py-3.5 bg-paper-2 flex items-center gap-2">
          <ShieldCheck className="h-4 w-4 text-primary" />
          <span className="text-xs font-semibold text-foreground">SDK usage</span>
        </div>
        <pre className="p-5 bg-paper-3 font-mono text-xs text-foreground overflow-x-auto leading-relaxed">
          <code>{`import { AuraClient } from '@aura/sdk';

const aura = new AuraClient({
  endpoint: 'https://api.aura.dev/api/v1/evaluate',
  apiKey: '${project.apiKey.slice(0, 16)}…',
  environment: 'production',
});`}</code>
        </pre>
      </div>

      {/* Security guidance */}
      <div className="rounded-xl border border-border bg-paper-2 p-5 space-y-2">
        <p className="text-xs font-semibold text-foreground">Security guidance</p>
        <ul className="space-y-1.5 text-xs text-muted-foreground list-disc list-inside">
          <li>Never commit API keys to source control — use environment variables.</li>
          <li>Rotate keys immediately if you suspect a compromise.</li>
          <li>Use separate projects (and keys) for production vs. staging environments.</li>
          <li>Keys are project-scoped — they cannot access other projects.</li>
        </ul>
      </div>
    </div>
  );
}

