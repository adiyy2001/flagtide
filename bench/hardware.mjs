import { execFileSync } from 'node:child_process';
import { cpus, platform, release, totalmem } from 'node:os';
import { pathToFileURL } from 'node:url';

function dockerVersion() {
  try {
    return execFileSync('docker', ['version', '--format', '{{.Server.Version}}'], {
      encoding: 'utf8',
    }).trim();
  } catch {
    return 'unavailable';
  }
}

export function describeHardware() {
  const processors = cpus();
  return {
    cpuModel: processors[0]?.model.trim() ?? 'unknown',
    logicalCores: processors.length,
    memoryGiB: Math.round((totalmem() / 1024 ** 3) * 10) / 10,
    os: `${platform()} ${release()}`,
    docker: dockerVersion(),
  };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.stdout.write(`${JSON.stringify(describeHardware(), null, 2)}\n`);
}
