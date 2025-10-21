import { S3Event } from 'aws-lambda';
import { S3Client, GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import { parse } from 'csv-parse/sync';
import { Readable } from 'stream';
import { z, ZodIssue } from 'zod';

const s3Client = new S3Client({});

type CsvRow = Record<string, string>;

interface RowValidationError {
  row: number;
  messages: string[];
}

interface ErrorReport {
  sourceBucket: string;
  sourceKey: string;
  generatedAt: string;
  errorCount: number;
  errors: RowValidationError[];
}

const REQUIRED_COLUMNS = new Set(['playerId', 'playerEmail']);
const OPTIONAL_COLUMNS = new Set(['playerName']);

const rowSchema = z
  .object({
    playerId: z.string().min(1, 'playerId is required'),
    playerEmail: z.string().email('playerEmail must be a valid email address'),
  })
  .passthrough()
  .superRefine((row, ctx) => {
    const categories = Object.entries(row).filter(
      ([key]) => !REQUIRED_COLUMNS.has(key) && !OPTIONAL_COLUMNS.has(key),
    );

    if (categories.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [],
        message: 'At least one category column is required',
      });
      return;
    }

    const nonEmptyCategories = categories.filter(([, value]) => value.trim().length > 0);

    if (nonEmptyCategories.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [],
        message: 'Category columns must include feedback',
      });
    }

    categories.forEach(([key, value]) => {
      if (value.trim().length === 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: [key],
          message: 'Category feedback cannot be blank',
        });
      }
    });
  });

const normalizeRow = (row: Record<string, unknown>): CsvRow => {
  const normalized: CsvRow = {};
  Object.entries(row).forEach(([key, value]) => {
    const trimmedKey = key.trim();
    if (!trimmedKey) {
      return;
    }

    if (typeof value === 'string') {
      normalized[trimmedKey] = value.trim();
    } else if (value === null || value === undefined) {
      normalized[trimmedKey] = '';
    } else {
      normalized[trimmedKey] = String(value).trim();
    }
  });
  return normalized;
};

const formatIssue = (issue: ZodIssue): string => {
  const path = issue.path.join('.');
  return path ? `${path}: ${issue.message}` : issue.message;
};

const decodeS3Key = (key: string): string => decodeURIComponent(key.replace(/\+/g, ' '));

const ensurePrefix = (prefix: string | undefined): string => {
  if (!prefix) {
    return '';
  }
  return prefix.endsWith('/') ? prefix : `${prefix}/`;
};

const ensureSuffix = (suffix: string | undefined): string => {
  if (!suffix) {
    return '.errors.json';
  }
  return suffix.startsWith('.') ? suffix : `.${suffix}`;
};

const shouldSkipKey = (key: string, errorPrefix: string, errorSuffix: string): boolean => {
  if (errorPrefix && key.startsWith(errorPrefix)) {
    return true;
  }
  return key.endsWith(errorSuffix);
};

const readObjectBody = async (bucket: string, key: string): Promise<string> => {
  const response = await s3Client.send(new GetObjectCommand({ Bucket: bucket, Key: key }));
  const body = response.Body;
  if (!body) {
    throw new Error('S3 object body was empty');
  }

  if (typeof body === 'string') {
    return body;
  }

  if (Buffer.isBuffer(body)) {
    return body.toString('utf-8');
  }

  if (body instanceof Uint8Array) {
    return Buffer.from(body).toString('utf-8');
  }

  if (typeof (body as Readable).pipe === 'function') {
    return streamToString(body as Readable);
  }

  if (typeof (body as { transformToString?: () => Promise<string> }).transformToString === 'function') {
    return await (body as { transformToString: () => Promise<string> }).transformToString();
  }

  throw new Error('Unsupported S3 body type');
};

const streamToString = async (stream: Readable): Promise<string> =>
  new Promise<string>((resolve, reject) => {
    const chunks: Buffer[] = [];
    stream.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
    stream.on('error', (err) => reject(err));
    stream.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
  });

const buildErrorReport = (
  bucket: string,
  key: string,
  errors: RowValidationError[],
): ErrorReport => ({
  sourceBucket: bucket,
  sourceKey: key,
  generatedAt: new Date().toISOString(),
  errorCount: errors.length,
  errors,
});

const writeErrorReport = async (
  bucket: string,
  key: string,
  errors: RowValidationError[],
): Promise<void> => {
  const errorBucket = process.env.ERROR_REPORT_BUCKET ?? bucket;
  const prefix = ensurePrefix(process.env.ERROR_REPORT_PREFIX ?? 'errors/');
  const suffix = ensureSuffix(process.env.ERROR_REPORT_SUFFIX);
  const errorKey = `${prefix}${key}${suffix}`;

  const report = buildErrorReport(bucket, key, errors);

  await s3Client.send(
    new PutObjectCommand({
      Bucket: errorBucket,
      Key: errorKey,
      Body: JSON.stringify(report, null, 2),
      ContentType: 'application/json',
    }),
  );
};

const validateRows = (rows: Record<string, unknown>[]): RowValidationError[] => {
  const errors: RowValidationError[] = [];

  rows.forEach((rawRow, index) => {
    const rowNumber = index + 2; // header is row 1
    const normalized = normalizeRow(rawRow);
    const result = rowSchema.safeParse(normalized);

    if (!result.success) {
      const messages = result.error.issues.map(formatIssue);
      errors.push({ row: rowNumber, messages });
    }
  });

  return errors;
};

const parseCsv = (csv: string): Record<string, unknown>[] =>
  parse(csv, {
    bom: true,
    columns: true,
    skip_empty_lines: true,
    trim: true,
  });

export const handler = async (event: S3Event): Promise<void> => {
  const records = event.Records ?? [];
  const prefix = ensurePrefix(process.env.ERROR_REPORT_PREFIX ?? 'errors/');
  const suffix = ensureSuffix(process.env.ERROR_REPORT_SUFFIX);

  for (const record of records) {
    const bucket = record.s3?.bucket?.name;
    const keyEncoded = record.s3?.object?.key;
    if (!bucket || !keyEncoded) {
      continue;
    }

    const key = decodeS3Key(keyEncoded);

    if (shouldSkipKey(key, prefix, suffix)) {
      continue;
    }

    try {
      const csvContent = await readObjectBody(bucket, key);
      const rows = parseCsv(csvContent);

      if (rows.length === 0) {
        await writeErrorReport(bucket, key, [
          { row: 1, messages: ['CSV file does not contain any data rows'] },
        ]);
        continue;
      }

      const errors = validateRows(rows);
      if (errors.length > 0) {
        await writeErrorReport(bucket, key, errors);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      await writeErrorReport(bucket, key, [
        { row: 0, messages: [`Failed to validate CSV: ${message}`] },
      ]);
    }
  }
};

export const __internal = {
  normalizeRow,
  validateRows,
};
