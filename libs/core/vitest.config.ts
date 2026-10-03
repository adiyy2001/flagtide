import { defineConfig } from 'vitest/config';

export default defineConfig({
  root: import.meta.dirname,
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts', 'test-support/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      include: ['src/lib/**/*.ts'],
      exclude: ['src/**/*.spec.ts'],
      reportsDirectory: '../../coverage/libs/core',
      reporter: ['text-summary', 'lcov'],
      thresholds: { lines: 90, functions: 90, branches: 85, statements: 90 },
    },
  },
});
