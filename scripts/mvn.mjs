import { runMaven } from './lib/maven.mjs';

const result = runMaven(process.argv.slice(2), { stdio: 'inherit' });
process.exit(result.status ?? 1);
