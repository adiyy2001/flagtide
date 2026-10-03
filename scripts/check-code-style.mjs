import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const DASHES = /[\u2013\u2014]/u;
const SKIPPED_FILES = new Set(['BRIEF.md', 'pnpm-lock.yaml']);
const SCRIPT_EXTENSIONS = new Set(['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx', '.mjs', '.cjs']);
const TEXT_EXTENSIONS = new Set([
  ...SCRIPT_EXTENSIONS,
  '.java',
  '.py',
  '.md',
  '.json',
  '.yml',
  '.yaml',
  '.xml',
  '.html',
  '.css',
  '.scss',
  '.properties',
  '.txt',
  '.sh',
  '.sql',
  '.toml',
]);

function listFiles() {
  const result = spawnSync('git', ['ls-files', '-co', '--exclude-standard', '-z'], {
    cwd: root,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(`git ls-files failed: ${result.stderr}`);
  }
  return result.stdout
    .split('\0')
    .filter((file) => file !== '' && !SKIPPED_FILES.has(file) && TEXT_EXTENSIONS.has(extname(file)));
}

function lineOf(text, offset) {
  let line = 1;
  for (let index = 0; index < offset; index += 1) {
    if (text.charCodeAt(index) === 10) {
      line += 1;
    }
  }
  return line;
}

function scriptKind(extension) {
  return extension === '.tsx' || extension === '.jsx' ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
}

function checkScript(file, text, report) {
  const extension = extname(file);
  const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, scriptKind(extension));
  const visit = (node) => {
    if (node.kind === ts.SyntaxKind.AnyKeyword) {
      report(file, source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1, 'any type');
    }
    if (node.kind === ts.SyntaxKind.NonNullExpression) {
      report(
        file,
        source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1,
        'non-null assertion',
      );
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  scanComments(file, text, report);
}

const PUBLISHED_SOURCE = /^libs\/(core|angular)\/(overrides\/)?src\/(?!.*\.spec\.ts$)/u;

function isTsdoc(file, text, range) {
  return (
    PUBLISHED_SOURCE.test(file) && text.startsWith('/**', range.pos) && !text.startsWith('/**/', range.pos)
  );
}

function scanComments(file, text, report) {
  const seen = new Set();
  const walk = (node) => {
    const full = node.getFullStart();
    for (const ranges of [
      ts.getLeadingCommentRanges(text, full),
      ts.getTrailingCommentRanges(text, node.getEnd()),
    ]) {
      for (const range of ranges ?? []) {
        if (!seen.has(range.pos)) {
          seen.add(range.pos);
          if (!(range.pos === 0 && text.startsWith('#!')) && !isTsdoc(file, text, range)) {
            report(file, lineOf(text, range.pos), 'code comment');
          }
        }
      }
    }
    node.forEachChild(walk);
  };
  const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, scriptKind(extname(file)));
  walk(source);
  const eof = source.endOfFileToken;
  for (const range of ts.getLeadingCommentRanges(text, eof.getFullStart()) ?? []) {
    if (!seen.has(range.pos) && !isTsdoc(file, text, range)) {
      report(file, lineOf(text, range.pos), 'code comment');
    }
  }
}

function checkJava(file, text, report) {
  let index = 0;
  const length = text.length;
  while (index < length) {
    const current = text[index];
    const next = text[index + 1];
    if (current === '/' && next === '/') {
      report(file, lineOf(text, index), 'code comment');
      while (index < length && text[index] !== '\n') {
        index += 1;
      }
    } else if (current === '/' && next === '*') {
      report(file, lineOf(text, index), 'code comment');
      const end = text.indexOf('*/', index + 2);
      index = end < 0 ? length : end + 2;
    } else if (current === '"' && text.startsWith('"""', index)) {
      let cursor = index + 3;
      while (cursor < length && !text.startsWith('"""', cursor)) {
        cursor += text[cursor] === '\\' ? 2 : 1;
      }
      index = cursor + 3;
    } else if (current === '"' || current === "'") {
      let cursor = index + 1;
      while (cursor < length && text[cursor] !== current && text[cursor] !== '\n') {
        cursor += text[cursor] === '\\' ? 2 : 1;
      }
      index = cursor + 1;
    } else {
      index += 1;
    }
  }
}

function checkPython(files, report) {
  if (files.length === 0) {
    return;
  }
  const program = [
    'import sys, tokenize',
    'for path in sys.argv[1:]:',
    '    with open(path, "rb") as handle:',
    '        for token in tokenize.tokenize(handle.readline):',
    '            if token.type == tokenize.COMMENT and not token.string.startswith("#!"):',
    '                print(path + ":" + str(token.start[0]))',
  ].join('\n');
  const result = spawnSync('python3', ['-c', program, ...files], { cwd: root, encoding: 'utf8' });
  if (result.status !== 0) {
    throw new Error(`python tokenize failed: ${result.stderr}`);
  }
  for (const entry of result.stdout.split('\n').filter((line) => line !== '')) {
    const separator = entry.lastIndexOf(':');
    report(entry.slice(0, separator), Number(entry.slice(separator + 1)), 'code comment');
  }
}

const problems = [];
const report = (file, line, message) => problems.push(`${file}:${line}: ${message}`);
const pythonFiles = [];

for (const file of listFiles()) {
  const text = readFileSync(resolve(root, file), 'utf8');
  const extension = extname(file);
  if (DASHES.test(text)) {
    text.split('\n').forEach((content, offset) => {
      if (DASHES.test(content)) {
        report(file, offset + 1, 'em or en dash');
      }
    });
  }
  if (SCRIPT_EXTENSIONS.has(extension)) {
    checkScript(file, text, report);
  } else if (extension === '.java') {
    checkJava(file, text, report);
  } else if (extension === '.py') {
    pythonFiles.push(file);
  }
}
checkPython(pythonFiles, report);

if (problems.length > 0) {
  console.error(problems.join('\n'));
  console.error(`check:text found ${problems.length} problem(s)`);
  process.exit(1);
}
console.log('check:text ok');
