import type { Config } from 'tailwindcss';

// All values resolve to CSS variables from src/styles/tokens.css (design spec 8.2).
// Spacing keeps Tailwind's default 4px scale.
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    colors: {
      transparent: 'transparent',
      current: 'currentColor',
      primary: 'var(--color-primary)',
      'primary-hover': 'var(--color-primary-hover)',
      'primary-subtle': 'var(--color-primary-subtle)',
      'on-primary': 'var(--color-surface)',
      text: 'var(--color-text)',
      muted: 'var(--color-muted)',
      border: 'var(--color-border)',
      bg: 'var(--color-bg)',
      surface: 'var(--color-surface)',
      success: 'var(--color-success)',
      warning: 'var(--color-warning)',
      danger: 'var(--color-danger)',
      info: 'var(--color-info)',
      overlay: 'var(--color-overlay)',
    },
    borderRadius: {
      none: '0',
      control: 'var(--radius-control)',
      card: 'var(--radius-card)',
      full: '9999px',
    },
    boxShadow: {
      card: 'var(--shadow-card)',
      overlay: 'var(--shadow-overlay)',
      none: 'none',
    },
    fontFamily: { sans: ['var(--font-sans)'] },
    fontSize: {
      xs: ['12px', { lineHeight: '1.5' }],
      sm: ['14px', { lineHeight: '1.5' }],
      base: ['16px', { lineHeight: '1.5' }],
      lg: ['18px', { lineHeight: '1.5' }],
      xl: ['20px', { lineHeight: '1.4' }],
      '2xl': ['24px', { lineHeight: '1.3' }],
      '3xl': ['30px', { lineHeight: '1.2' }],
    },
    extend: {},
  },
  plugins: [],
} satisfies Config;
