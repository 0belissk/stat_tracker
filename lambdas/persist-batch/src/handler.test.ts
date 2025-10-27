import type { TransactWriteCommandInput } from '@aws-sdk/lib-dynamodb';
import type { PersistBatchEvent, PersistBatchReport } from './handler';

let handler: typeof import('./handler').handler;
let PersistBatchError: typeof import('./handler').PersistBatchError;

jest.mock('@aws-sdk/client-dynamodb', () => ({
  DynamoDBClient: jest.fn(() => ({})),
}));

jest.mock('@aws-sdk/lib-dynamodb', () => {
  const mockSend = jest.fn();

  class TransactWriteCommand {
    public readonly input: TransactWriteCommandInput;

    constructor(input: TransactWriteCommandInput) {
      this.input = input;
    }
  }

  return {
    DynamoDBDocumentClient: { from: jest.fn(() => ({ send: mockSend })) },
    TransactWriteCommand,
    __mockSend: mockSend,
  };
});

jest.mock('aws-xray-sdk-core');
jest.mock('@aws-sdk/client-cloudwatch');

const baseReport = (overrides: Partial<PersistBatchReport> = {}): PersistBatchReport => ({
  reportId: 'report-1',
  playerId: 'player-1',
  coachId: 'coach-1',
  reportTimestamp: '2024-05-19T10:00:00Z',
  createdAt: '2024-05-19T10:05:00Z',
  categories: { serving: 'Great job', passing: 'Consistent' },
  teamId: 'team-1',
  playerEmail: 'player@example.com',
  playerName: 'Alex',
  ...overrides,
});

const event = (
  reports: PersistBatchReport[],
  overrides: Partial<PersistBatchEvent> = {},
): PersistBatchEvent => ({
  ingestionId: 'ing-1',
  sourceBucket: 'raw-bucket',
  sourceKey: 'coach/upload.csv',
  reports,
  ...overrides,
});

describe('persist-batch handler', () => {
  let mockSend: jest.Mock;
  let mockMetricSend: jest.Mock;

  beforeEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
    process.env.REPORTS_TABLE_NAME = 'reports-table';
    process.env.CUSTOM_METRICS_NAMESPACE = 'custom/ns';
    process.env.CUSTOM_METRICS_SERVICE_NAME = 'csv-pipeline';
    process.env.CUSTOM_METRICS_STAGE = 'dev';

    mockSend = (jest.requireMock('@aws-sdk/lib-dynamodb') as { __mockSend: jest.Mock }).__mockSend;
    mockMetricSend = (
      jest.requireMock('@aws-sdk/client-cloudwatch') as { __mockSend: jest.Mock }
    ).__mockSend;

    const module = require('./handler') as typeof import('./handler');
    handler = module.handler;
    PersistBatchError = module.PersistBatchError;
  });

  it('persists reports, updates aggregates, and returns a summary', async () => {
    mockSend.mockResolvedValue({});

    const result = await handler(
      event([
        baseReport(),
        baseReport({
          reportId: 'report-2',
          reportTimestamp: '2024-05-20T12:34:56Z',
          playerId: 'player-2',
          coachId: 'coach-2',
          teamId: 'team-2',
          playerEmail: 'player2@example.com',
          playerName: 'Jamie',
        }),
      ]),
    );

    expect(result).toEqual({
      tableName: 'reports-table',
      total: 2,
      processed: 2,
      skipped: 0,
      correlationId: undefined,
      ingestDurationMs: undefined,
    });

    expect(mockSend).toHaveBeenCalledTimes(1);
    const command = mockSend.mock.calls[0][0] as {
      input: TransactWriteCommandInput & { ReturnCancellationReasons?: boolean };
    };
    expect(command.input.ReturnCancellationReasons).toBe(true);
    expect(command.input.TransactItems).toHaveLength(4);

    const putItem = command.input.TransactItems?.[0]?.Put;
    expect(putItem?.TableName).toBe('reports-table');
    expect(putItem?.ConditionExpression).toBe('attribute_not_exists(#pk) AND attribute_not_exists(#sk)');
    expect(putItem?.ExpressionAttributeNames).toEqual({ '#pk': 'PK', '#sk': 'SK' });
    expect(putItem?.Item).toMatchObject({
      PK: 'PLAYER#player-1',
      SK: 'REPORT#20240519T100000#report-1',
      reportId: 'report-1',
      reportTimestamp: '2024-05-19T10:00:00.000Z',
      reportTimestampKey: '20240519T100000',
      GSI1PK: 'REPORT#report-1',
      GSI1SK: 'REPORT#report-1',
      GSI2PK: 'TEAM#team-1',
      GSI2SK: 'CREATED#20240519T100000#report-1',
      categories: { serving: 'Great job', passing: 'Consistent' },
      playerEmail: 'player@example.com',
      playerName: 'Alex',
      ingestionId: 'ing-1',
      sourceBucket: 'raw-bucket',
      sourceKey: 'coach/upload.csv',
      entityType: 'REPORT',
    });

    expect(mockMetricSend).not.toHaveBeenCalled();
  });

  it('records ingest duration metric when start time is provided', async () => {
    mockSend.mockResolvedValue({});
    const start = new Date(Date.now() - 1000).toISOString();

    await handler(
      event(
        [baseReport()],
        {
          ingestStartedAt: start,
          correlationId: 'corr-1',
        },
      ),
    );

    expect(mockMetricSend).toHaveBeenCalled();
    const metricCommand = mockMetricSend.mock.calls[0][0] as {
      input: {
        Namespace?: string;
        MetricData?: Array<{
          MetricName?: string;
          Dimensions?: Array<{ Name: string; Value: string }>;
        }>;
      };
    };

    expect(metricCommand.input.Namespace).toBe('custom/ns');
    expect(metricCommand.input.MetricData).toHaveLength(2);

    const [primary, detailed] = metricCommand.input.MetricData ?? [];
    expect(primary?.MetricName).toBe('ingest_duration');
    expect(primary?.Dimensions).toEqual([
      { Name: 'Service', Value: 'csv-pipeline' },
      { Name: 'Stage', Value: 'dev' },
    ]);

    expect(detailed?.MetricName).toBe('ingest_duration_by_outcome');
    expect(detailed?.Dimensions).toEqual([
      { Name: 'Service', Value: 'csv-pipeline' },
      { Name: 'Stage', Value: 'dev' },
      { Name: 'Outcome', Value: 'success' },
      { Name: 'CorrelationId', Value: 'corr-1' },
    ]);
  });

  it('skips duplicates reported via conditional failures and continues processing', async () => {
    const conditionalError = Object.assign(new Error('duplicate'), {
      name: 'TransactionCanceledException',
      CancellationReasons: [{ Code: 'ConditionalCheckFailed' }],
    });

    mockSend
      .mockRejectedValueOnce(conditionalError)
      .mockRejectedValueOnce(conditionalError)
      .mockResolvedValueOnce({});

    const result = await handler(
      event([
        baseReport(),
        baseReport({ reportId: 'report-2', reportTimestamp: '2024-05-20T12:34:56Z' }),
      ]),
    );

    expect(result).toEqual({
      tableName: 'reports-table',
      total: 2,
      processed: 1,
      skipped: 1,
      correlationId: undefined,
      ingestDurationMs: undefined,
    });

    expect(mockSend).toHaveBeenCalledTimes(3);
    expect(mockMetricSend).not.toHaveBeenCalled();
  });

  it('throws a PersistBatchError when a non-conditional failure occurs', async () => {
    mockSend.mockRejectedValueOnce(
      Object.assign(new Error('throttle'), { name: 'ProvisionedThroughputExceededException' }),
    );

    await expect(handler(event([baseReport()]))).rejects.toThrow(PersistBatchError);
  });

  it('returns early when no reports are provided', async () => {
    const result = await handler(event([]));
    expect(result).toEqual({
      tableName: 'reports-table',
      total: 0,
      processed: 0,
      skipped: 0,
      correlationId: undefined,
      ingestDurationMs: undefined,
    });
    expect(mockSend).not.toHaveBeenCalled();
    expect(mockMetricSend).not.toHaveBeenCalled();
  });

  it('splits transactions when multiple reports for the same player are provided', async () => {
    mockSend.mockResolvedValue({});

    const result = await handler(
      event([
        baseReport(),
        baseReport({ reportId: 'report-2', reportTimestamp: '2024-05-20T12:34:56Z' }),
      ]),
    );

    expect(result).toEqual({
      tableName: 'reports-table',
      total: 2,
      processed: 2,
      skipped: 0,
      correlationId: undefined,
      ingestDurationMs: undefined,
    });

    expect(mockSend).toHaveBeenCalledTimes(2);
    const firstCommand = mockSend.mock.calls[0][0] as { input: TransactWriteCommandInput };
    const secondCommand = mockSend.mock.calls[1][0] as { input: TransactWriteCommandInput };

    expect(firstCommand.input.TransactItems).toHaveLength(2);
    expect(secondCommand.input.TransactItems).toHaveLength(2);
  });
});
