import { EventBridgeEvent } from 'aws-lambda';
import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';
import { SESv2Client, SendEmailCommand } from '@aws-sdk/client-sesv2';
import { S3Client, GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';

export interface ReportCreatedDetail {
  reportId: string;
  reportName?: string;
  reportType?: string;
  recipientEmail: string;
  recipientName?: string;
  s3Bucket: string;
  s3Key: string;
}

interface HandlerConfig {
  senderEmail: string;
  emailSubject: string;
  emailTemplate: string;
  linkExpirySeconds: number;
}

const resolveEndpoint = (serviceEnvKey: string): string | undefined => {
  const specific = process.env[`${serviceEnvKey}_ENDPOINT_URL`]?.trim();
  if (specific) return specific;
  const shared = process.env.AWS_ENDPOINT_URL?.trim();
  return shared || undefined;
};

const resolveRegion = (): string => process.env.AWS_REGION ?? process.env.AWS_DEFAULT_REGION ?? 'us-east-1';

const region = resolveRegion();
const secretsManagerEndpoint = resolveEndpoint('SECRETSMANAGER');
const sesEndpoint = resolveEndpoint('SES');
const s3Endpoint = resolveEndpoint('S3');
const auditBucket = process.env.EMAIL_AUDIT_BUCKET?.trim();
const auditPrefix = process.env.EMAIL_AUDIT_PREFIX?.trim();

const secretsManagerClient = new SecretsManagerClient({
  region,
  ...(secretsManagerEndpoint ? { endpoint: secretsManagerEndpoint } : {}),
});
const sesClient = new SESv2Client({ region, ...(sesEndpoint ? { endpoint: sesEndpoint } : {}) });
const s3Client = new S3Client({
  region,
  ...(s3Endpoint ? { endpoint: s3Endpoint, forcePathStyle: true } : {}),
});

let cachedConfig: HandlerConfig | undefined;

const ensureConfigSecretArn = (): string => {
  const secretArn = process.env.CONFIG_SECRET_ARN?.trim();
  if (!secretArn) {
    throw new Error('CONFIG_SECRET_ARN environment variable is required');
  }
  return secretArn;
};

const fetchConfig = async (): Promise<HandlerConfig> => {
  if (cachedConfig) {
    return cachedConfig;
  }

  const secretArn = ensureConfigSecretArn();
  const response = await secretsManagerClient.send(
    new GetSecretValueCommand({ SecretId: secretArn }),
  );

  const secretPayload =
    response.SecretString ??
    (response.SecretBinary ? Buffer.from(response.SecretBinary).toString('utf-8') : undefined);

  if (!secretPayload) {
    throw new Error('Configuration secret did not include a value');
  }

  let parsed: Partial<HandlerConfig & { linkExpirySeconds: number }>;
  try {
    parsed = JSON.parse(secretPayload);
  } catch (error) {
    throw new Error('Configuration secret is not valid JSON');
  }

  const { senderEmail, emailSubject, emailTemplate, linkExpirySeconds } = parsed;

  if (!senderEmail || !emailSubject || !emailTemplate) {
    throw new Error('Configuration secret is missing required fields');
  }

  const expirySeconds = Number(linkExpirySeconds);
  if (!Number.isFinite(expirySeconds) || expirySeconds <= 0) {
    throw new Error('Configuration secret linkExpirySeconds must be a positive number');
  }

  cachedConfig = {
    senderEmail,
    emailSubject,
    emailTemplate,
    linkExpirySeconds: expirySeconds,
  };

  return cachedConfig;
};

const renderTemplate = (template: string, variables: Record<string, string>): string =>
  template.replace(/{{\s*([^}\s]+)\s*}}/g, (match, key: string) => {
    if (!(key in variables)) {
      throw new Error(`Missing template variable: ${key}`);
    }
    return variables[key];
  });

export const handler = async (
  event: EventBridgeEvent<'report.created', ReportCreatedDetail>,
): Promise<void> => {
  if (!event.detail) {
    throw new Error('Event detail is missing');
  }

  const {
    reportId,
    reportName,
    reportType,
    recipientEmail,
    recipientName,
    s3Bucket,
    s3Key,
  } = event.detail;

  if (!recipientEmail) {
    throw new Error('recipientEmail is required in event detail');
  }

  if (!s3Bucket || !s3Key) {
    throw new Error('s3Bucket and s3Key are required in event detail');
  }

  const config = await fetchConfig();

  const signedUrl = await getSignedUrl(
    s3Client,
    new GetObjectCommand({ Bucket: s3Bucket, Key: s3Key }),
    { expiresIn: config.linkExpirySeconds },
  );

  const templateVariables: Record<string, string> = {
    reportId,
    reportName: reportName ?? reportId,
    reportType: reportType ?? 'report',
    recipientEmail,
    recipientName: recipientName ?? 'there',
    downloadUrl: signedUrl,
  };

  const subject = renderTemplate(config.emailSubject, templateVariables);
  const body = renderTemplate(config.emailTemplate, templateVariables);

  await sesClient.send(
    new SendEmailCommand({
      FromEmailAddress: config.senderEmail,
      Destination: { ToAddresses: [recipientEmail] },
      Content: {
        Simple: {
          Subject: { Data: subject },
          Body: { Text: { Data: body } },
        },
      },
    }),
  );

  if (sesEndpoint && auditBucket) {
    const keyPrefix = auditPrefix ? (auditPrefix.endsWith('/') ? auditPrefix : `${auditPrefix}/`) : '';
    const auditKey = `${keyPrefix}${reportId}.json`;
    const auditPayload = JSON.stringify(
      {
        reportId,
        recipientEmail,
        subject,
        body,
        downloadUrl: signedUrl,
      },
      null,
      2,
    );

    await s3Client.send(
      new PutObjectCommand({
        Bucket: auditBucket,
        Key: auditKey,
        Body: auditPayload,
        ContentType: 'application/json',
      }),
    );
  }
};

export const __internal = {
  resetConfigCache: (): void => {
    cachedConfig = undefined;
  },
};
