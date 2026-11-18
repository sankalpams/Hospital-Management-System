import { clsx, type ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

// Teach tailwind-merge our custom token class names so conflicting utilities merge correctly.
const COLORS = [
  'primary', 'primary-hover', 'primary-subtle', 'on-primary', 'text', 'muted', 'border', 'border-strong',
  'bg', 'surface', 'surface-raised', 'surface-sunken', 'success', 'success-subtle', 'warning',
  'warning-subtle', 'danger', 'danger-subtle', 'info', 'info-subtle', 'overlay',
  'chart-1', 'chart-2', 'chart-3', 'chart-4', 'chart-5', 'chart-6',
];

const twMerge = extendTailwindMerge({
  extend: {
    theme: { color: COLORS, radius: ['control', 'card', 'pill'], shadow: ['card', 'raised', 'overlay'] },
    classGroups: {
      'font-size': [{ text: ['xs', 'sm', 'base', 'lg', 'xl', '2xl', '3xl', '4xl'] }],
    },
  },
});

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
