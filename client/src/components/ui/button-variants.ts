import { cn } from '@/lib/utils';

export const buttonVariants = ({
  variant = 'default',
  size = 'default',
  className,
}: {
  variant?: 'default' | 'secondary' | 'outline' | 'ghost' | 'destructive' | 'glitch';
  size?: 'default' | 'sm' | 'lg' | 'icon';
  className?: string;
} = {}) =>
  cn(
    'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md font-sans text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 active:translate-y-[0.5px]',
    {
      default:
        'bg-primary text-primary-foreground hover:opacity-95 shadow-sm',
      secondary:
        'bg-paper-2 border border-border text-foreground hover:bg-paper-3',
      outline:
        'border border-border bg-transparent text-foreground hover:bg-paper-2',
      ghost:
        'text-muted-foreground hover:bg-paper-2 hover:text-foreground',
      destructive:
        'bg-destructive text-destructive-foreground hover:opacity-90',
      glitch:
        'bg-primary text-primary-foreground hover:opacity-95 shadow-sm',
    }[variant || 'default'],
    {
      default: 'h-9 px-4 py-2',
      sm: 'h-8 px-3 text-xs',
      lg: 'h-10 px-6 text-sm',
      icon: 'h-9 w-9',
    }[size || 'default'],
    className,
  );
