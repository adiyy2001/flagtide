import { cpus, platform, release, totalmem } from 'node:os';

export interface Hardware {
  readonly cpuModel: string;
  readonly logicalCores: number;
  readonly memoryGiB: number;
  readonly os: string;
  readonly node: string;
  readonly v8: string;
}

export function describeHardware(): Hardware {
  const processors = cpus();
  return {
    cpuModel: processors[0]?.model.trim() ?? 'unknown',
    logicalCores: processors.length,
    memoryGiB: Math.round((totalmem() / 1024 ** 3) * 10) / 10,
    os: `${platform()} ${release()}`,
    node: process.version,
    v8: process.versions.v8,
  };
}
