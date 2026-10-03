import { WebSocket } from 'k6/websockets';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const servers = (__ENV.SERVERS || 'server-a:8080,server-b:8080').split(',');
const sdkKey = __ENV.SDK_KEY || 'fws_demo_dev_sdk_0000000000000';
const adminKey = __ENV.ADMIN_KEY || 'fwa_demo_dev_admin_000000000000';
const project = __ENV.PROJECT || 'demo';
const flagKey = __ENV.FLAG_KEY || 'load-flag';
const virtualUsers = Number(__ENV.VUS || 50);
const socketsPerUser = Number(__ENV.SOCKETS_PER_VU || 100);
const rounds = Number(__ENV.ROUNDS || 30);
const connectSpacingMillis = Number(__ENV.CONNECT_SPACING_MS || 25);
const triggerStartSeconds = Number(__ENV.TRIGGER_START_S || 30);
const roundPeriodSeconds = Number(__ENV.ROUND_PERIOD_S || 1);
const holdMillis = (triggerStartSeconds + rounds * roundPeriodSeconds + 10) * 1000;

let deadline = 0;

const commitToReceipt = new Trend('commit_to_receipt_ms', true);
const framesReceived = new Counter('delta_frames_received');
const socketsConnected = new Counter('sockets_connected');
const socketsFailed = new Counter('sockets_failed');
const socketsClosedEarly = new Counter('sockets_closed_early');

export const options = {
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    clients: {
      executor: 'per-vu-iterations',
      vus: virtualUsers,
      iterations: 1,
      maxDuration: `${Math.ceil(holdMillis / 1000) + 60}s`,
      exec: 'holdSockets',
    },
    trigger: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: rounds,
      startTime: `${triggerStartSeconds}s`,
      maxDuration: `${rounds * roundPeriodSeconds + 60}s`,
      exec: 'changeFlag',
    },
  },
  thresholds: {
    commit_to_receipt_ms: ['p(95)<300'],
    sockets_failed: ['count==0'],
  },
};

const jsonHeaders = (key) => ({
  headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
});

function urlOf(server, path) {
  return `http://${server}${path}`;
}

export function setup() {
  const body = JSON.stringify({
    key: flagKey,
    description: 'load test flag',
    type: 'boolean',
    variants: [
      { key: 'on', value: true },
      { key: 'off', value: false },
    ],
    offVariant: 'off',
    fallthroughVariant: 'on',
  });
  const created = http.post(
    urlOf(servers[0], `/api/v1/projects/${project}/flags`),
    body,
    jsonHeaders(adminKey),
  );
  check(created, { 'load flag exists': (response) => response.status === 201 || response.status === 409 });
}

function openSocket(server, clientId) {
  const socket = new WebSocket(`ws://${server}/sdk/v1/stream`);
  let connected = false;
  socket.addEventListener('open', () => {
    connected = true;
    socketsConnected.add(1);
    socket.send(JSON.stringify({ t: 'hello', sdkKey, clientId, sdk: 'k6' }));
  });
  socket.addEventListener('message', (event) => {
    const frame = JSON.parse(event.data);
    if (frame.t === 'snapshot') {
      socket.send(JSON.stringify({ t: 'ack', v: frame.v }));
      return;
    }
    if (frame.t !== 'deltas' || frame.entries.length === 0) {
      return;
    }
    const newest = frame.entries[frame.entries.length - 1];
    commitToReceipt.add(Date.now() - newest.committedAtMs);
    framesReceived.add(1);
    socket.send(JSON.stringify({ t: 'ack', v: frame.to }));
  });
  socket.addEventListener('error', () => {
    if (!connected) {
      socketsFailed.add(1);
    }
  });
  socket.addEventListener('close', () => {
    if (connected && Date.now() < deadline) {
      socketsClosedEarly.add(1);
    }
  });
  return socket;
}

export function holdSockets() {
  deadline = Date.now() + holdMillis - 2000;
  const sockets = [];
  for (let index = 0; index < socketsPerUser; index++) {
    const server = servers[(__VU + index) % servers.length];
    const clientId = `k6-${__VU}-${index}`;
    setTimeout(() => sockets.push(openSocket(server, clientId)), index * connectSpacingMillis);
  }
  setTimeout(() => sockets.forEach((socket) => socket.close()), holdMillis);
}

export function changeFlag() {
  const server = servers[__ITER % servers.length];
  const enabled = __ITER % 2 === 0;
  const response = http.put(
    urlOf(server, `/api/v1/projects/${project}/flags/${flagKey}/environments/dev/enabled`),
    JSON.stringify({ enabled }),
    jsonHeaders(adminKey),
  );
  check(response, { 'flag changed': (r) => r.status === 200 });
  sleep(roundPeriodSeconds);
}

export function handleSummary(data) {
  return { '/results/k6-summary.json': JSON.stringify(data, null, 2) };
}
