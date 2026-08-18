import js from '@eslint/js';
import prettierConfig from 'eslint-config-prettier';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'dist-extension', 'coverage', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],

      // LinkSentry-specific guardrails. A submitted URL is hostile input: it must
      // never become a navigable target or be injected as markup.
      // See docs/SECURITY_BOUNDARY.md.
      'react/no-danger': 'off', // not installed; the restricted-syntax rule below covers it
      'no-restricted-syntax': [
        'error',
        {
          selector: 'JSXAttribute[name.name="dangerouslySetInnerHTML"]',
          message:
            'dangerouslySetInnerHTML is banned. Analysed URLs are untrusted input and must be rendered as text.',
        },
        {
          selector: 'CallExpression[callee.object.name="window"][callee.property.name="open"]',
          message: 'window.open must never be called with an analysed URL. See docs/SECURITY_BOUNDARY.md.',
        },
      ],
    },
  },
  {
    // Tests legitimately reach for non-null assertions on fixtures.
    files: ['**/*.{test,spec}.{ts,tsx}', 'src/test/**'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
    rules: {
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },
  {
    files: ['vite.config.ts', 'vite.extension.config.ts', 'eslint.config.js'],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    // The MV3 popup runs in an extension page, not a plain web page: it needs
    // the chrome.* globals on top of the standard browser set.
    files: ['src/extension/**/*.{ts,tsx}'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.webextensions },
    },
  },
  // Must stay last so formatting-related rules are switched off.
  prettierConfig,
);
