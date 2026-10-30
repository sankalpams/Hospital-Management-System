import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: { border: 'hsl(var(--border))', input: 'hsl(var(--border))', background: 'hsl(var(--background))', foreground: 'hsl(var(--foreground))', primary: { DEFAULT: 'hsl(var(--primary))', foreground: 'hsl(var(--primary-foreground))' }, muted: { DEFAULT: 'hsl(var(--muted))', foreground: 'hsl(var(--muted-foreground))' }, surface: 'hsl(var(--surface))', danger: 'hsl(var(--danger))', success: 'hsl(var(--success))', warning: 'hsl(var(--warning))' },
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'] },
      boxShadow: { panel: '0 1px 2px rgb(15 23 42 / .06)' }
    }
  },
  plugins: []
} satisfies Config;
