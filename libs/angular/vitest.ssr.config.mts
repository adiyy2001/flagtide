import { defineConfig } from 'vitest/config';

export default defineConfig({
  root: import.meta.dirname,
  oxc: { decorator: { legacy: true }, transform: { useDefineForClassFields: false } } as never,
  resolve: {
    alias: {
      '@flagwire/core': new URL('../core/src/index.ts', import.meta.url).pathname,
      '@flagwire/angular': new URL('./src/index.ts', import.meta.url).pathname,
    },
  },
  test: {
    environment: 'node',
    include: ['ssr/**/*.ssr-test.ts'],
  },
});
