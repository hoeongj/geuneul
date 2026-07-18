export const DEFAULT_RUN_SEED = 20260718;
export const MAX_RANDOM_SEED = 2147483646;

export function parsePositiveInteger(raw, name, maximum = Number.MAX_SAFE_INTEGER) {
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0 || value > maximum) {
    throw new Error(`${name} must be a positive integer <= ${maximum}: ${raw}`);
  }
  return value;
}

/** 같은 seed/VU/iteration은 스케줄링 순서와 무관하게 같은 입력 좌표·필터 시퀀스를 만든다. */
export function iterationSeed(baseSeed, vu, iteration) {
  const modulus = MAX_RANDOM_SEED + 1;
  const mixed = (baseSeed + vu * 1000003 + iteration * 9176) % modulus;
  return mixed === 0 ? 1 : mixed;
}

/** 기본은 loopback 전용. 명시적 opt-in이 없으면 실수로 원격 대상에 부하를 걸 수 없다. */
export function targetMetadata(rawBaseUrl, allowRemote) {
  const match = /^([a-z][a-z0-9+.-]*):\/\/([^/?#]+)(?:[/?#]|$)/i.exec(rawBaseUrl);
  if (!match) {
    throw new Error(`BASE_URL must be an absolute URL: ${rawBaseUrl}`);
  }
  const protocol = match[1].toLowerCase();
  if (protocol !== 'http' && protocol !== 'https') {
    throw new Error(`BASE_URL protocol must be http or https: ${protocol}`);
  }
  // userinfo는 summary에 절대 복제하지 않는다. authority의 마지막 @ 뒤 host:port만 사용한다.
  const authority = match[2];
  const hostPort = authority.slice(authority.lastIndexOf('@') + 1);
  const hostname = hostPort.startsWith('[')
    ? hostPort.slice(1, hostPort.indexOf(']')).toLowerCase()
    : hostPort.split(':')[0].toLowerCase();
  const loopback = ['localhost', '127.0.0.1', '::1'].includes(hostname);
  if (!loopback && !allowRemote) {
    throw new Error('Remote load target blocked. Use a local BASE_URL or set ALLOW_REMOTE_LOAD=true explicitly.');
  }
  return { kind: loopback ? 'local' : 'remote', origin: `${protocol}://${hostPort}` };
}

const METRIC_NAMES = [
  'http_reqs',
  'http_req_failed',
  'iterations',
  'checks',
  'geo_radius_duration',
  'geo_nearest_duration',
  'geo_bounds_duration',
  'geo_reco_duration',
];

const VALUE_NAMES = ['count', 'rate', 'passes', 'fails', 'avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'];

function selectedValues(metric) {
  const selected = {};
  for (const name of VALUE_NAMES) {
    if (metric.values && metric.values[name] !== undefined) {
      selected[name] = metric.values[name];
    }
  }
  return selected;
}

function allChecksPassed(metric) {
  const values = metric && metric.values;
  return Boolean(
    values
      && Number.isFinite(values.rate)
      && Number.isFinite(values.passes)
      && Number.isFinite(values.fails)
      && values.rate === 1
      && values.passes > 0
      && values.fails === 0,
  );
}

/** k6 handleSummary의 큰 내부 객체를 안정적인 schema-versioned JSON으로 축약한다. */
export function buildMachineSummary(data, configuration, generatedAt = new Date().toISOString()) {
  const metrics = {};
  const thresholds = {};
  // checks가 없거나, 한 건도 실행되지 않았거나, 한 건이라도 실패하면 요약을 fail-closed 처리한다.
  let passed = allChecksPassed(data.metrics && data.metrics.checks);

  for (const name of METRIC_NAMES) {
    const metric = data.metrics && data.metrics[name];
    if (!metric) {
      continue;
    }
    metrics[name] = {
      type: metric.type,
      contains: metric.contains,
      values: selectedValues(metric),
    };
    if (metric.thresholds) {
      thresholds[name] = {};
      for (const expression of Object.keys(metric.thresholds).sort()) {
        const ok = metric.thresholds[expression].ok === true;
        thresholds[name][expression] = ok;
        passed = passed && ok;
      }
    }
  }

  return {
    schemaVersion: 1,
    generatedAt,
    passed,
    testRunDurationMs: data.state && data.state.testRunDurationMs !== undefined
      ? data.state.testRunDurationMs
      : null,
    configuration,
    metrics,
    thresholds,
  };
}

export function summaryLine(summary, outputPath) {
  const failed = summary.metrics.http_req_failed && summary.metrics.http_req_failed.values.rate;
  const requests = summary.metrics.http_reqs && summary.metrics.http_reqs.values.count;
  return `spatial-load passed=${summary.passed} requests=${requests ?? 0} failedRate=${failed ?? 0} `
    + `seed=${summary.configuration.runSeed} summary=${outputPath}\n`;
}
