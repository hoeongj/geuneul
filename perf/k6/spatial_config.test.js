import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildMachineSummary,
  iterationSeed,
  parsePositiveInteger,
  targetMetadata,
} from './spatial_config.js';

test('same seed, VU and iteration always produce the same random seed', () => {
  assert.equal(iterationSeed(20260718, 3, 9), iterationSeed(20260718, 3, 9));
  assert.notEqual(iterationSeed(20260718, 3, 9), iterationSeed(20260718, 3, 10));
  assert.ok(iterationSeed(20260718, 3, 9) > 0);
});

test('remote targets are blocked unless the caller explicitly opts in', () => {
  assert.deepEqual(targetMetadata('http://localhost:8080', false), {
    kind: 'local',
    origin: 'http://localhost:8080',
  });
  assert.throws(() => targetMetadata('https://example.com/api', false), /Remote load target blocked/);
  assert.deepEqual(targetMetadata('https://user:password@example.com/api', true), {
    kind: 'remote',
    origin: 'https://example.com',
  });
});

test('machine summary keeps stable metrics and threshold outcomes', () => {
  const data = {
    state: { testRunDurationMs: 1234 },
    metrics: {
      http_reqs: { type: 'counter', contains: 'default', values: { count: 40, rate: 20 } },
      http_req_failed: {
        type: 'rate',
        contains: 'default',
        values: { rate: 0, passes: 0, fails: 40 },
        thresholds: { 'rate<0.01': { ok: true } },
      },
      checks: {
        type: 'rate',
        contains: 'default',
        values: { rate: 1, passes: 160, fails: 0 },
        thresholds: { 'rate==1': { ok: true } },
      },
      geo_radius_duration: {
        type: 'trend',
        contains: 'time',
        values: { avg: 80, med: 75, 'p(95)': 120, 'p(99)': 180, ignored: 999 },
        thresholds: { 'p(95)<2000': { ok: true }, 'p(99)<3200': { ok: true } },
      },
    },
  };

  const summary = buildMachineSummary(data, { runSeed: 20260718 }, '2026-07-18T00:00:00Z');

  assert.equal(summary.schemaVersion, 1);
  assert.equal(summary.generatedAt, '2026-07-18T00:00:00Z');
  assert.equal(summary.passed, true);
  assert.equal(summary.metrics.geo_radius_duration.values['p(95)'], 120);
  assert.equal(summary.metrics.geo_radius_duration.values.ignored, undefined);
  assert.deepEqual(summary.thresholds.http_req_failed, { 'rate<0.01': true });
});

test('machine summary fails closed when checks are missing or any check failed', () => {
  const greenThresholds = {
    http_req_failed: {
      type: 'rate',
      contains: 'default',
      values: { rate: 0, passes: 0, fails: 4 },
      thresholds: { 'rate<0.01': { ok: true } },
    },
  };

  const missingChecks = buildMachineSummary({ metrics: greenThresholds }, { runSeed: 20260718 });
  assert.equal(missingChecks.passed, false);

  const failedChecks = buildMachineSummary({
    metrics: {
      ...greenThresholds,
      checks: {
        type: 'rate',
        contains: 'default',
        values: { rate: 0.75, passes: 3, fails: 1 },
      },
    },
  }, { runSeed: 20260718 });
  assert.equal(failedChecks.passed, false);
});

test('invalid numeric configuration is rejected before a run starts', () => {
  assert.equal(parsePositiveInteger('4', 'PEAK_VUS', 100), 4);
  assert.throws(() => parsePositiveInteger('0', 'PEAK_VUS', 100), /positive integer/);
  assert.throws(() => parsePositiveInteger('2.5', 'PEAK_VUS', 100), /positive integer/);
});
