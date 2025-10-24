import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import {
  DynamoDBDocumentClient,
  TransactWriteCommand,
  TransactWriteCommandInput,
} from '@aws-sdk/lib-dynamodb';

const resolveEndpoint = (serviceEnvKey: string): string | undefined => {
  const specific = process.env[`${serviceEnvKey}_ENDPOINT_URL`]?.trim();
  if (specific) return specific;
  const shared = process.env.AWS_ENDPOINT_URL?.trim();
  return shared || undefined;
};

const resolveRegion = (): string => process.env.AWS_REGION ?? process.env.AWS_DEFAULT_REGION ?? 'us-east-1';

const dynamoEndpoint = resolveEndpoint('DYNAMODB');

const dynamoDb = DynamoDBDocumentClient.from(
  new DynamoDBClient({
    region: resolveRegion(),
    ...(dynamoEndpoint ? { endpoint: dynamoEndpoint } : {}),
  }),
  {
    marshallOptions: { removeUndefinedValues: true },
  },
);

const MAX_TRANSACTION_OPERATIONS = 25;
const OPERATIONS_PER_RECORD = 2;
const MAX_RECORDS_PER_TRANSACTION = Math.floor(
  MAX_TRANSACTION_OPERATIONS / OPERATIONS_PER_RECORD,
);

export interface PersistBatchReport {
  reportId: string;
  playerId: string;
  coachId: string;
  reportTimestamp: string;
  categories: Record<string, string>;
  createdAt?: string;
  teamId?: string;
  playerEmail?: string;
  playerName?: string;
}

export interface PersistBatchEvent {
  ingestionId?: string;
  sourceBucket?: string;
  sourceKey?: string;
  reports: PersistBatchReport[];
}

export interface PersistBatchResult {
  tableName: string;
  total: number;
  processed: number;
  skipped: number;
}

export interface PersistFailureDetail {
  reportId: string;
  message: string;
}

export class PersistBatchError extends Error {
  public readonly failures: PersistFailureDetail[];
  public readonly summary: PersistBatchResult;

  constructor(message: string, failures: PersistFailureDetail[], summary: PersistBatchResult) {
    super(message);
    this.name = 'PersistBatchError';
    this.failures = failures;
    this.summary = summary;
  }
}

interface NormalizedReportRecord {
  reportId: string;
  playerId: string;
  coachId: string;
  reportTimestamp: string;
  reportTimestampKey: string;
  createdAt: string;
  categories: Record<string, string>;
  teamId?: string;
  playerEmail?: string;
  playerName?: string;
  ingestionId?: string;
  sourceBucket?: string;
  sourceKey?: string;
  sortKey: string;
  gsi2SortKey?: string;
}

const ensureTableName = (): string => {
  const tableName = process.env.REPORTS_TABLE_NAME;
  if (!tableName || tableName.trim().length === 0) {
    throw new Error('REPORTS_TABLE_NAME environment variable is required');
  }
  return tableName;
};

const normalizeTimestamp = (value: unknown, field: string): string => {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`${field} must be an ISO-8601 string`);
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`${field} must be a valid ISO-8601 timestamp`);
  }
  return date.toISOString();
};

const toSortKeyTimestamp = (iso: string): string => {
  const date = new Date(iso);
  const pad = (n: number): string => n.toString().padStart(2, '0');
  return `${date.getUTCFullYear()}${pad(date.getUTCMonth() + 1)}${pad(date.getUTCDate())}T${pad(
    date.getUTCHours(),
  )}${pad(date.getUTCMinutes())}${pad(date.getUTCSeconds())}`;
};

const normalizeReports = (event: PersistBatchEvent): NormalizedReportRecord[] => {
  if (!event || !Array.isArray(event.reports)) {
    throw new Error('Event.reports must be an array');
  }

  const ingestionId = typeof event.ingestionId === 'string' ? event.ingestionId.trim() : undefined;
  const sourceBucket =
    typeof event.sourceBucket === 'string' ? event.sourceBucket.trim() || undefined : undefined;
  const sourceKey = typeof event.sourceKey === 'string' ? event.sourceKey.trim() || undefined : undefined;

  return event.reports.map((report, index) => {
    if (!report || typeof report !== 'object') {
      throw new Error(`reports[${index}] must be an object`);
    }

    const path = `reports[${index}]`;
    const ensureString = (value: unknown, name: string): string => {
      if (typeof value !== 'string' || value.trim().length === 0) {
        throw new Error(`${path}.${name} is required`);
      }
      return value.trim();
    };

    const reportId = ensureString(report.reportId, 'reportId');
    const playerId = ensureString(report.playerId, 'playerId');
    const coachId = ensureString(report.coachId, 'coachId');
    const reportTimestamp = normalizeTimestamp(report.reportTimestamp, `${path}.reportTimestamp`);
    const createdAt = report.createdAt
      ? normalizeTimestamp(report.createdAt, `${path}.createdAt`)
      : reportTimestamp;

    if (!report.categories || typeof report.categories !== 'object') {
      throw new Error(`${path}.categories is required`);
    }

    const categories: Record<string, string> = {};
    Object.entries(report.categories).forEach(([key, value]) => {
      const normalizedKey = key.trim();
      if (!normalizedKey) {
        return;
      }
      let stringValue: string;
      if (typeof value === 'string') {
        stringValue = value.trim();
      } else if (value === undefined || value === null) {
        stringValue = '';
      } else {
        stringValue = String(value).trim();
      }
      categories[normalizedKey] = stringValue;
    });

    if (Object.keys(categories).length === 0) {
      throw new Error(`${path}.categories must contain at least one entry`);
    }

    const reportTimestampKey = toSortKeyTimestamp(reportTimestamp);
    const sortKey = `REPORT#${reportTimestampKey}#${reportId}`;
    const trimmedTeamId =
      typeof report.teamId === 'string' ? report.teamId.trim() || undefined : undefined;
    const trimmedPlayerEmail =
      typeof report.playerEmail === 'string' ? report.playerEmail.trim() || undefined : undefined;
    const trimmedPlayerName =
      typeof report.playerName === 'string' ? report.playerName.trim() || undefined : undefined;

    return {
      reportId,
      playerId,
      coachId,
      reportTimestamp,
      reportTimestampKey,
      createdAt,
      categories,
      teamId: trimmedTeamId,
      playerEmail: trimmedPlayerEmail,
      playerName: trimmedPlayerName,
      ingestionId: ingestionId || undefined,
      sourceBucket,
      sourceKey,
      sortKey,
      gsi2SortKey: trimmedTeamId ? `CREATED#${reportTimestampKey}#${reportId}` : undefined,
    };
  });
};

const chunkRecords = <T>(records: T[], size: number): T[][] => {
  const chunks: T[][] = [];
  for (let i = 0; i < records.length; i += size) {
    chunks.push(records.slice(i, i + size));
  }
  return chunks;
};

const buildTransactItems = (
  record: NormalizedReportRecord,
  tableName: string,
): TransactWriteCommandInput['TransactItems'] => {
  const reportItem: Record<string, unknown> = {
    PK: `PLAYER#${record.playerId}`,
    SK: record.sortKey,
    reportId: record.reportId,
    playerId: record.playerId,
    coachId: record.coachId,
    reportTimestamp: record.reportTimestamp,
    reportTimestampKey: record.reportTimestampKey,
    createdAt: record.createdAt,
    categories: record.categories,
    entityType: 'REPORT',
    ingestionId: record.ingestionId,
    sourceBucket: record.sourceBucket,
    sourceKey: record.sourceKey,
    GSI1PK: `REPORT#${record.reportId}`,
    GSI1SK: `REPORT#${record.reportId}`,
  };

  if (record.teamId) {
    reportItem.teamId = record.teamId;
    reportItem.GSI2PK = `TEAM#${record.teamId}`;
    reportItem.GSI2SK = record.gsi2SortKey;
  }

  if (record.playerEmail) {
    reportItem.playerEmail = record.playerEmail;
  }

  if (record.playerName) {
    reportItem.playerName = record.playerName;
  }

  const updateValues: Record<string, unknown> = {
    ':zero': 0,
    ':one': 1,
    ':ts': record.reportTimestamp,
    ':tsKey': record.reportTimestampKey,
    ':reportId': record.reportId,
    ':coachId': record.coachId,
    ':lastSk': record.sortKey,
    ':updatedAt': record.createdAt,
    ':entityType': 'PLAYER_PROFILE',
    ':playerId': record.playerId,
  };

  const updateSegments: string[] = [
    'reportCount = if_not_exists(reportCount, :zero) + :one',
    'lastReportAt = :ts',
    'lastReportId = :reportId',
    'lastCoachId = :coachId',
    'lastReportSk = :lastSk',
    'lastReportTimestampKey = :tsKey',
    'updatedAt = :updatedAt',
    'entityType = if_not_exists(entityType, :entityType)',
    'playerId = if_not_exists(playerId, :playerId)',
  ];

  if (record.playerEmail) {
    updateSegments.push('playerEmail = if_not_exists(playerEmail, :playerEmail)');
    updateValues[':playerEmail'] = record.playerEmail;
  }

  if (record.playerName) {
    updateSegments.push('playerName = if_not_exists(playerName, :playerName)');
    updateValues[':playerName'] = record.playerName;
  }

  if (record.teamId) {
    updateSegments.push('teamId = if_not_exists(teamId, :teamId)');
    updateValues[':teamId'] = record.teamId;
  }

  if (record.ingestionId) {
    updateSegments.push('lastIngestionId = :ingestionId');
    updateValues[':ingestionId'] = record.ingestionId;
  }

  if (record.sourceBucket) {
    updateSegments.push('lastSourceBucket = :sourceBucket');
    updateValues[':sourceBucket'] = record.sourceBucket;
  }

  if (record.sourceKey) {
    updateSegments.push('lastSourceKey = :sourceKey');
    updateValues[':sourceKey'] = record.sourceKey;
  }

  const updateExpression = `SET ${updateSegments.join(', ')}`;

  return [
    {
      Put: {
        TableName: tableName,
        Item: reportItem,
        ConditionExpression: 'attribute_not_exists(#pk) AND attribute_not_exists(#sk)',
        ExpressionAttributeNames: {
          '#pk': 'PK',
          '#sk': 'SK',
        },
      },
    },
    {
      Update: {
        TableName: tableName,
        Key: {
          PK: `PLAYER#${record.playerId}`,
          SK: `PROFILE#${record.playerId}`,
        },
        UpdateExpression: updateExpression,
        ExpressionAttributeValues: updateValues,
      },
    },
  ];
};

const isConditionalFailure = (error: unknown): boolean => {
  if (!error || typeof error !== 'object') {
    return false;
  }

  const candidate = error as { name?: string; CancellationReasons?: Array<{ Code?: string | undefined }> };

  if (candidate.name === 'ConditionalCheckFailedException') {
    return true;
  }

  if (candidate.name === 'TransactionCanceledException' && Array.isArray(candidate.CancellationReasons)) {
    return candidate.CancellationReasons.some(
      (reason) => reason && reason.Code === 'ConditionalCheckFailed',
    );
  }

  return false;
};

const groupRecordsByUniquePlayer = (
  records: NormalizedReportRecord[],
): NormalizedReportRecord[][] => {
  const groups: NormalizedReportRecord[][] = [];

  for (const record of records) {
    let placed = false;
    for (const group of groups) {
      if (!group.some((existing) => existing.playerId === record.playerId) && group.length < MAX_RECORDS_PER_TRANSACTION) {
        group.push(record);
        placed = true;
        break;
      }
    }

    if (!placed) {
      groups.push([record]);
    }
  }

  return groups;
};

const executeTransactions = async (
  records: NormalizedReportRecord[],
  tableName: string,
): Promise<void> => {
  const groups = groupRecordsByUniquePlayer(records);
  for (const group of groups) {
    const command = new TransactWriteCommand({
      TransactItems: group.flatMap((record) => buildTransactItems(record, tableName)),
      ReturnCancellationReasons: true,
    });

    await dynamoDb.send(command);
  }
};

export const handler = async (event: PersistBatchEvent): Promise<PersistBatchResult> => {
  const tableName = ensureTableName();
  const normalizedReports = normalizeReports(event);

  if (normalizedReports.length === 0) {
    return { tableName, total: 0, processed: 0, skipped: 0 };
  }

  let processed = 0;
  let skipped = 0;
  const failures: PersistFailureDetail[] = [];

  const chunks = chunkRecords(normalizedReports, Math.max(1, MAX_RECORDS_PER_TRANSACTION));

  for (const chunk of chunks) {
    try {
      await executeTransactions(chunk, tableName);
      processed += chunk.length;
      continue;
    } catch (error) {
      if (!isConditionalFailure(error)) {
        const summary: PersistBatchResult = {
          tableName,
          total: normalizedReports.length,
          processed,
          skipped,
        };
        throw new PersistBatchError(
          (error as Error).message || 'Failed to persist batch',
          chunk.map((record) => ({ reportId: record.reportId, message: (error as Error).message || 'Unknown error' })),
          summary,
        );
      }

      for (const record of chunk) {
        try {
          await executeTransactions([record], tableName);
          processed += 1;
        } catch (innerError) {
          if (isConditionalFailure(innerError)) {
            skipped += 1;
          } else {
            failures.push({
              reportId: record.reportId,
              message: (innerError as Error).message || 'Unknown error',
            });
          }
        }
      }
    }
  }

  if (failures.length > 0) {
    const summary: PersistBatchResult = {
      tableName,
      total: normalizedReports.length,
      processed,
      skipped,
    };
    throw new PersistBatchError('Failed to persist one or more reports', failures, summary);
  }

  return {
    tableName,
    total: normalizedReports.length,
    processed,
    skipped,
  };
};

export const __internal = {
  normalizeReports,
  buildTransactItems,
  isConditionalFailure,
  toSortKeyTimestamp,
  groupRecordsByUniquePlayer,
};
