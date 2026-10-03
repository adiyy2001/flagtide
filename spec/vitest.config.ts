import { defineConfig } from 'vitest/config';

export default defineConfig({
  root: import.meta.dirname,
  test: {
    environment: 'node',
    include: ['tests/**/*.spec.ts'],
    testTimeout: 120000,
  },
});
