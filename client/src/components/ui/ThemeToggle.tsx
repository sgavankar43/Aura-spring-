import { Moon, Sun } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';

export function ThemeToggle({ className }: { className?: string }) {
  const [dark, setDark] = useState(() => {
    if (typeof document === 'undefined') return true;
    return document.documentElement.classList.contains('dark');
  });

  useEffect(() => {
    const update = () => {
      setDark(document.documentElement.classList.contains('dark'));
    };
    window.addEventListener('aura-theme-change', update);
    window.addEventListener('storage', update);
    return () => {
      window.removeEventListener('aura-theme-change', update);
      window.removeEventListener('storage', update);
    };
  }, []);

  const toggle = () => {
    const next = !document.documentElement.classList.contains('dark');
    document.documentElement.classList.toggle('dark', next);
    localStorage.setItem('aura-theme', next ? 'dark' : 'light');
    setDark(next);
    window.dispatchEvent(new Event('aura-theme-change'));
  };

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      data-testid="theme-toggle"
      className={className || 'h-8 w-8 text-muted-foreground hover:text-foreground'}
    >
      {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </Button>
  );
}

