import type { TransactWriteCommandInput } from '@aws-sdk/lib-dynamodb';
import { handler, PersistBatchError, PersistBatchEvent, PersistBatchReport } from './handler';

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

const { __mockSend: mockSend } = jest.requireMock('@aws-sdk/lib-dynamodb') as {
  __mockSend: jest.Mock;
};

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

const event = (reports: PersistBatchReport[], overrides: Partial<PersistBatchEvent> = {}): PersistBatchEvent => ({
  ingestionId: 'ing-1',
  sourceBucket: 'raw-bucket',
  sourceKey: 'coach/upload.csv',
  reports,
  ...overrides,
});

describe('persist-batch handler', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.REPORTS_TABLE_NAME = 'reports-table';
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

    const updateItem = command.input.TransactItems?.[1]?.Update;
    expect(updateItem?.TableName).toBe('reports-table');
    expect(updateItem?.Key).toEqual({
      PK: 'PLAYER#player-1',
      SK: 'PROFILE#player-1',
    });
    expect(updateItem?.UpdateExpression).toContain('reportCount = if_not_exists(reportCount, :zero) + :one');
    expect(updateItem?.UpdateExpression).toContain('lastReportSk = :lastSk');
    expect(updateItem?.ExpressionAttributeValues).toMatchObject({
      ':ts': '2024-05-19T10:00:00.000Z',
      ':tsKey': '20240519T100000',
      ':reportId': 'report-1',
      ':coachId': 'coach-1',
      ':playerEmail': 'player@example.com',
      ':playerName': 'Alex',
      ':teamId': 'team-1',
      ':ingestionId': 'ing-1',
      ':sourceBucket': 'raw-bucket',
      ':sourceKey': 'coach/upload.csv',
    });
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
    });

    expect(mockSend).toHaveBeenCalledTimes(3);
  });

  it('throws a PersistBatchError when a non-conditional failure occurs', async () => {
    mockSend.mockRejectedValueOnce(Object.assign(new Error('throttle'), { name: 'ProvisionedThroughputExceededException' }));

    await expect(handler(event([baseReport()]))).rejects.toThrow(PersistBatchError);
  });

  it('returns early when no reports are provided', async () => {
    const result = await handler(event([]));
    expect(result).toEqual({
      tableName: 'reports-table',
      total: 0,
      processed: 0,
      skipped: 0,
    });
    expect(mockSend).not.toHaveBeenCalled();
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
    });

    expect(mockSend).toHaveBeenCalledTimes(2);
    const firstCommand = mockSend.mock.calls[0][0] as { input: TransactWriteCommandInput };
    const secondCommand = mockSend.mock.calls[1][0] as { input: TransactWriteCommandInput };

    expect(firstCommand.input.TransactItems).toHaveLength(2);
    expect(secondCommand.input.TransactItems).toHaveLength(2);
  });
});
