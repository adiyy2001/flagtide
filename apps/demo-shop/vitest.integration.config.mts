import { defineConfig } from 'vitest/config';

export default defineConfig({
  root: import.meta.dirname,
  test: {
    environment: 'node',
    include: ['integration/**/*.itest.ts'],
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
});
