/**
 * Tailwind is restricted to the HMS semantic tokens (design-system/hms/tokens.json).
 * `colors` REPLACES Tailwind's default palette, so classes like bg-blue-500 do not exist and
 * components cannot drift from the design system.
 * @type {import('tailwindcss').Config}
 */
const v = (name) => `var(--color-${name})`;

export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    colors: {
      transparent: 'transparent',
      current: 'currentColor',
      white: '#FFFFFF',
      background: v('background'),
      surface: { DEFAULT: v('surface'), muted: v('surface-muted') },
      foreground: { DEFAULT: v('foreground'), muted: v('foreground-muted'), subtle: v('foreground-subtle') },
      border: { DEFAULT: v('border'), strong: v('border-strong') },
      primary: {
        DEFAULT: v('primary'),
        hover: v('primary-hover'),
        foreground: v('primary-foreground'),
        soft: v('primary-soft'),
        'soft-foreground': v('primary-soft-foreground'),
      },
      accent: v('accent'),
      ring: v('ring'),
      sidebar: { DEFAULT: v('sidebar'), foreground: v('sidebar-foreground'), active: v('sidebar-active') },
      success: { DEFAULT: v('success'), soft: v('success-soft') },
      warning: { DEFAULT: v('warning'), soft: v('warning-soft') },
      danger: { DEFAULT: v('danger'), soft: v('danger-soft') },
      info: { DEFAULT: v('info'), soft: v('info-soft') },
      neutral: { DEFAULT: v('neutral'), soft: v('neutral-soft') },
      special: { DEFAULT: v('special'), soft: v('special-soft') },
      chart: {
        1: v('chart-1'), 2: v('chart-2'), 3: v('chart-3'), 4: v('chart-4'), 5: v('chart-5'), 6: v('chart-6'),
      },
    },
    fontFamily: {
      heading: ['var(--font-heading)'],
      sans: ['var(--font-body)'],
      mono: ['var(--font-mono)'],
    },
    extend: {
      borderRadius: {
        sm: 'var(--primitive-radius-sm)',
        md: 'var(--primitive-radius-md)',
        lg: 'var(--primitive-radius-lg)',
        xl: 'var(--primitive-radius-xl)',
      },
      boxShadow: {
        xs: 'var(--primitive-shadow-xs)',
        sm: 'var(--primitive-shadow-sm)',
        md: 'var(--primitive-shadow-md)',
        lg: 'var(--primitive-shadow-lg)',
      },
      spacing: {
        sidebar: 'var(--layout-sidebar-width)',
        topbar: 'var(--layout-topbar-height)',
      },
      transitionDuration: { fast: '150ms', normal: '200ms' },
    },
  },
  plugins: [],
};
