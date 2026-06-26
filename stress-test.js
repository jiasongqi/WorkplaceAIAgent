// k6 Stress Test Script for Agent Product
// Usage: k6 run --vus 10 --duration 30s stress-test.js
//
// Prerequisites:
//   - Install k6: https://k6.io/docs/get-started/installation/
//   - Start agent_product server on port 8123
//
// Metrics collected:
//   - http_req_duration: Request latency
//   - http_req_failed: Failed requests
//   - agent_*: Custom metrics from /actuator/prometheus

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const agentRequestDuration = new Trend('agent_request_duration', true);

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8123';
const API_PREFIX = '/api';

// Test scenarios
export const options = {
  scenarios: {
    // Scenario 1: Gradual ramp-up
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 5 },   // Ramp up to 5 VUs
        { duration: '20s', target: 10 },  // Stay at 10 VUs
        { duration: '10s', target: 20 },  // Ramp up to 20 VUs
        { duration: '30s', target: 20 },  // Stay at 20 VUs
        { duration: '10s', target: 0 },   // Ramp down
      ],
      gracefulRampDown: '5s',
    },

    // Scenario 2: Constant load (uncomment to use)
    // constant_load: {
    //   executor: 'constant-vus',
    //   vus: 10,
    //   duration: '1m',
    // },
  },

  // Thresholds (pass/fail criteria)
  thresholds: {
    http_req_duration: ['p(95)<5000'],  // 95% of requests under 5s
    http_req_failed: ['rate<0.1'],      // Error rate under 10%
    errors: ['rate<0.1'],               // Custom error rate under 10%
  },
};

// Test data
const TEST_MESSAGES = [
  '你好，我想了解一下职业规划',
  '帮我分析一下我的技能优势',
  '推荐一些学习资源',
  '如何准备技术面试？',
  '我的职业发展方向是什么？',
];

// Helper: Get random test message
function getRandomMessage() {
  return TEST_MESSAGES[Math.floor(Math.random() * TEST_MESSAGES.length)];
}

// Helper: Generate unique user ID
function getUserId() {
  return `test_user_${__VU}_${__ITER}`;
}

// ─── Test Scenarios ──────────────────────────────────────────────────

// Health check
export function healthCheck() {
  const res = http.get(`${BASE_URL}${API_PREFIX}/actuator/health`);

  check(res, {
    'health check status is 200': (r) => r.status === 200,
    'health check has status UP': (r) => {
      try {
        return JSON.parse(r.body).status === 'UP';
      } catch {
        return false;
      }
    },
  });

  return res.status === 200;
}

// Get agent metrics
export function getMetrics() {
  const res = http.get(`${BASE_URL}${API_PREFIX}/actuator/agent-metrics`);

  check(res, {
    'metrics endpoint accessible': (r) => r.status === 200,
    'metrics has activeRequests': (r) => {
      try {
        return 'activeRequests' in JSON.parse(r.body);
      } catch {
        return false;
      }
    },
  });

  if (res.status === 200) {
    try {
      const metrics = JSON.parse(res.body);
      console.log(`Active requests: ${metrics.activeRequests}`);
      console.log(`Total requests: ${metrics.requests?.total || 0}`);
      console.log(`Avg duration: ${metrics.requests?.avgDurationMs || 0}ms`);
    } catch (e) {
      // Ignore parse errors
    }
  }

  return res;
}

// Get Prometheus metrics
export function getPrometheusMetrics() {
  const res = http.get(`${BASE_URL}${API_PREFIX}/actuator/prometheus`);

  check(res, {
    'prometheus endpoint accessible': (r) => r.status === 200,
  });

  if (res.status === 200) {
    // Parse and log key metrics
    const lines = res.body.split('\n');
    const agentMetrics = lines.filter(line =>
      line.startsWith('agent_') && !line.startsWith('#')
    );

    if (agentMetrics.length > 0) {
      console.log('Agent metrics found:');
      agentMetrics.slice(0, 5).forEach(line => console.log(`  ${line}`));
    }
  }

  return res;
}

// Chat request (main test)
export function chatRequest() {
  const userId = getUserId();
  const message = getRandomMessage();

  const payload = JSON.stringify({
    message: message,
    userId: userId,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '30s',
  };

  const startTime = Date.now();
  const res = http.post(`${BASE_URL}${API_PREFIX}/ai/chat`, payload, params);
  const duration = Date.now() - startTime;

  // Track custom metrics
  agentRequestDuration.add(duration);
  errorRate.add(res.status !== 200);

  // Validate response
  const success = check(res, {
    'chat status is 200': (r) => r.status === 200,
    'chat has response body': (r) => r.body && r.body.length > 0,
    'chat response time < 10s': (r) => r.timings.duration < 10000,
  });

  if (!success) {
    console.error(`Chat request failed: status=${res.status}, duration=${duration}ms`);
    if (res.body) {
      console.error(`Response body: ${res.body.substring(0, 200)}`);
    }
  }

  // Random sleep between requests (1-3 seconds)
  sleep(Math.random() * 2 + 1);

  return res;
}

// ─── Main Test Flow ──────────────────────────────────────────────────

export default function () {
  // 1. Health check (run once per VU)
  if (__ITER === 0) {
    const healthy = healthCheck();
    if (!healthy) {
      console.error('Health check failed, aborting test');
      return;
    }
  }

  // 2. Main chat request
  chatRequest();

  // 3. Occasionally check metrics (10% of iterations)
  if (Math.random() < 0.1) {
    getMetrics();
  }
}

// ─── Setup & Teardown ────────────────────────────────────────────────

export function setup() {
  console.log('Starting stress test...');
  console.log(`Base URL: ${BASE_URL}`);

  // Verify server is running
  const res = http.get(`${BASE_URL}${API_PREFIX}/actuator/health`);
  if (res.status !== 200) {
    throw new Error(`Server not ready at ${BASE_URL}. Status: ${res.status}`);
  }

  console.log('Server is healthy, starting test...');

  // Get initial metrics
  const metrics = getMetrics();
  return { startTime: Date.now() };
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\nTest completed in ${duration}s`);

  // Get final metrics
  console.log('\nFinal metrics:');
  getMetrics();
  getPrometheusMetrics();
}
