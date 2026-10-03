import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const repositoryRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '../..');
export const serverDirectory = join(repositoryRoot, 'apps/server');

function hasCompiler(javaHome) {
  return javaHome !== undefined && existsSync(join(javaHome, 'bin', 'javac'));
}

export function javaEnvironment() {
  const userLocalJdk = join(homedir(), '.local/share/jdks/temurin-21');
  const env = { ...process.env };
  if (!hasCompiler(env.JAVA_HOME) && hasCompiler(userLocalJdk)) {
    env.JAVA_HOME = userLocalJdk;
    env.PATH = `${join(userLocalJdk, 'bin')}:${env.PATH}`;
  }
  return env;
}

export function runMaven(args, options = {}) {
  return spawnSync(join(serverDirectory, 'mvnw'), ['-B', '-ntp', ...args], {
    cwd: serverDirectory,
    env: javaEnvironment(),
    encoding: 'utf8',
    ...options,
  });
}
