import { defineConfig } from 'vitest/config';

export default defineConfig({
  root: import.meta.dirname,
  test: {
    environment: 'node',
    include: ['integration/**/*.spec.ts'],
    testTimeout: 20_000,
  },
});
