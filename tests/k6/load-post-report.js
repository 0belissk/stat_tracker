import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { check, sleep } from 'k6';

const coachCreateDuration = new Trend('coach_report_create_duration', true);
const reportTextDuration = new Trend('report_text_fetch_duration', true);

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const COACH_REPORTS_URL = `${BASE_URL}/coach/reports`;
const REPORT_TEXT_TEMPLATE = __ENV.REPORT_TEXT_URL_TEMPLATE || `${BASE_URL}/reports/{reportId}/text`;

const COACH_BEARER = __ENV.COACH_JWT || 'TODO: supply a valid coach JWT bearer token for load testing';
const PLAYER_BEARER =
  __ENV.PLAYER_JWT || 'TODO: supply a valid player JWT bearer token for fetching report text';

const DEFAULT_PLAYER_ID = __ENV.REPORT_PLAYER_ID || 'TODO: set the target playerId used for generated reports';
const DEFAULT_PLAYER_EMAIL =
  __ENV.REPORT_PLAYER_EMAIL || 'TODO: set the target player email used for generated reports';

const CATEGORY_JSON = __ENV.REPORT_CATEGORIES_JSON ||
  '{"serving":"A","passing":"B","attack":"C"}';
let categories;
try {
  categories = JSON.parse(CATEGORY_JSON);
} catch (err) {
  throw new Error(`Failed to parse REPORT_CATEGORIES_JSON: ${err}`);
}

const PRESEEDED_REPORT_IDS = (__ENV.REPORT_IDS || '')
  .split(',')
  .map((value) => value.trim())
  .filter((value) => value.length > 0);

export const options = {
  scenarios: {
    createReports: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 25,
      maxVUs: 150,
      stages: [
        { duration: '1m', target: 5 },
        { duration: '2m', target: 15 },
        { duration: '1m', target: 0 },
      ],
      exec: 'createReportScenario',
    },
    fetchReportText: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 25,
      maxVUs: 150,
      startTime: '30s',
      stages: [
        { duration: '1m', target: 10 },
        { duration: '2m', target: 25 },
        { duration: '1m', target: 0 },
      ],
      exec: 'fetchReportTextScenario',
    },
  },
  thresholds: {
    coach_report_create_duration: ['p(95)<800', 'p(99)<1200'],
    report_text_fetch_duration: ['p(95)<400', 'p(99)<700'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  return {
    playerId: DEFAULT_PLAYER_ID,
    playerEmail: DEFAULT_PLAYER_EMAIL,
  };
}

export function createReportScenario(data) {
  const reportId = generateReportId();
  const payload = JSON.stringify({
    playerId: data.playerId,
    playerEmail: data.playerEmail,
    categories,
  });

  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${COACH_BEARER}`,
    reportId,
  };

  const response = http.post(COACH_REPORTS_URL, payload, { headers });
  coachCreateDuration.add(response.timings.duration);

  check(response, {
    'accepted coach report': (r) => r.status === 202,
  });

  sleep(randomPause());
}

export function fetchReportTextScenario() {
  if (PRESEEDED_REPORT_IDS.length === 0) {
    return;
  }
  const reportId = PRESEEDED_REPORT_IDS[Math.floor(Math.random() * PRESEEDED_REPORT_IDS.length)];
  const reportUrl = resolveReportTextUrl(reportId);
  const headers = {
    Authorization: `Bearer ${PLAYER_BEARER}`,
  };

  const response = http.get(reportUrl, { headers });
  reportTextDuration.add(response.timings.duration);

  check(response, {
    'fetched report text': (r) => r.status === 200,
  });

  sleep(randomPause());
}

function resolveReportTextUrl(reportId) {
  if (!reportId) {
    throw new Error('Report identifier cannot be empty when resolving the report text URL');
  }
  return REPORT_TEXT_TEMPLATE.replace('{reportId}', encodeURIComponent(reportId));
}

function generateReportId() {
  const now = new Date().toISOString();
  const random = Math.floor(Math.random() * 1000000)
    .toString()
    .padStart(6, '0');
  return `${now}-${random}`;
}

function randomPause() {
  return Math.random() * 2;
}

export function handleSummary(data) {
  logPercentiles('POST /coach/reports', data.metrics.coach_report_create_duration);
  logPercentiles('GET /reports/{id}/text', data.metrics.report_text_fetch_duration);

  return {
    'k6-summary.json': JSON.stringify(data, null, 2),
  };
}

function logPercentiles(label, metric) {
  if (!metric || !metric.percentiles) {
    return;
  }
  const p95 = metric.percentiles['p(95)'];
  const p99 = metric.percentiles['p(99)'];
  if (p95 !== undefined && p99 !== undefined) {
    console.log(`${label} p95=${p95.toFixed(2)}ms p99=${p99.toFixed(2)}ms`);
  }
}
