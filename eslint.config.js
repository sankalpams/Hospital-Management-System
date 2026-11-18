import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['**/node_modules/**', '**/dist/**', '**/build/**', '**/coverage/**', 'graphify-out/**', 'apps/client/public/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
);
