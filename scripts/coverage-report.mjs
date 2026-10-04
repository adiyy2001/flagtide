import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const outputPath = join(root, 'bench/results/coverage.json');

function percent(covered, total) {
  return total === 0 ? 100 : Math.round((covered / total) * 1000) / 10;
}

function istanbul(path) {
  const data = JSON.parse(readFileSync(path, 'utf8'));
  const total = { statements: [0, 0], branches: [0, 0], functions: [0, 0], lines: [0, 0] };
  Object.values(data).forEach((file) => {
    const lines = new Map();
    Object.entries(file.s).forEach(([id, hits]) => {
      total.statements[1] += 1;
      total.statements[0] += hits > 0 ? 1 : 0;
      const line = file.statementMap[id].start.line;
      lines.set(line, (lines.get(line) ?? false) || hits > 0);
    });
    Object.values(file.b).forEach((hits) =>
      hits.forEach((count) => {
        total.branches[1] += 1;
        total.branches[0] += count > 0 ? 1 : 0;
      }),
    );
    Object.values(file.f).forEach((hits) => {
      total.functions[1] += 1;
      total.functions[0] += hits > 0 ? 1 : 0;
    });
    total.lines[1] += lines.size;
    total.lines[0] += [...lines.values()].filter(Boolean).length;
  });
  return total;
}

function lcov(path) {
  const total = { statements: [0, 0], branches: [0, 0], functions: [0, 0], lines: [0, 0] };
  const sum = (key, hit, found) => {
    readFileSync(path, 'utf8')
      .split('\n')
      .forEach((line) => {
        if (line.startsWith(`${found}:`)) total[key][1] += Number(line.slice(found.length + 1));
        if (line.startsWith(`${hit}:`)) total[key][0] += Number(line.slice(hit.length + 1));
      });
  };
  sum('lines', 'LH', 'LF');
  sum('branches', 'BRH', 'BRF');
  sum('functions', 'FNH', 'FNF');
  total.statements = total.lines;
  return total;
}

function summarize(total) {
  return Object.fromEntries(
    Object.entries(total).map(([key, [covered, all]]) => [key, percent(covered, all)]),
  );
}

const webProjects = [
  ['libs/core', 'coverage/libs/core/lcov.info', lcov],
  ['libs/angular', 'libs/angular/coverage/angular/coverage-final.json', istanbul],
  ['apps/admin', 'apps/admin/coverage/admin/coverage-final.json', istanbul],
  ['apps/demo-shop', 'apps/demo-shop/coverage/demo-shop/coverage-final.json', istanbul],
];

const web = webProjects
  .filter(([, path]) => existsSync(join(root, path)))
  .map(([project, path, read]) => ({ project, ...summarize(read(join(root, path))) }));

const jacocoPath = join(root, 'apps/server/architecture/target/site/jacoco-merged/jacoco.xml');
const layers = [
  ['domain', 'dev/flagwire/domain/'],
  ['application', 'dev/flagwire/application/'],
  ['adapters', 'dev/flagwire/adapter/'],
  ['bootstrap', 'dev/flagwire/bootstrap/'],
];

function java() {
  if (!existsSync(jacocoPath)) {
    return [];
  }
  const xml = readFileSync(jacocoPath, 'utf8');
  const totals = new Map(layers.map(([name]) => [name, { lines: [0, 0], branches: [0, 0] }]));
  for (const packageMatch of xml.matchAll(/<package name="([^"]+)">([\s\S]*?)<\/package>/g)) {
    const layer = layers.find(([, prefix]) => `${packageMatch[1]}/`.startsWith(prefix));
    if (!layer) {
      continue;
    }
    const target = totals.get(layer[0]);
    for (const file of packageMatch[2].matchAll(/<sourcefile [^>]*>([\s\S]*?)<\/sourcefile>/g)) {
      for (const counter of file[1].matchAll(
        /<counter type="(LINE|BRANCH)" missed="(\d+)" covered="(\d+)"\/>/g,
      )) {
        const key = counter[1] === 'LINE' ? 'lines' : 'branches';
        target[key][0] += Number(counter[3]);
        target[key][1] += Number(counter[2]) + Number(counter[3]);
      }
    }
  }
  return [...totals].map(([layer, total]) => ({ layer, ...summarize(total) }));
}

const report = { generatedFrom: 'coverage output of the last test run', web, java: java() };
mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);
console.log('project'.padEnd(18), 'lines  statements  branches  functions');
web.forEach((entry) =>
  console.log(
    entry.project.padEnd(18),
    String(entry.lines).padStart(5),
    String(entry.statements).padStart(10),
    String(entry.branches).padStart(9),
    String(entry.functions).padStart(10),
  ),
);
console.log('\nserver layer'.padEnd(19), 'lines  branches');
report.java.forEach((entry) =>
  console.log(entry.layer.padEnd(18), String(entry.lines).padStart(5), String(entry.branches).padStart(8)),
);
