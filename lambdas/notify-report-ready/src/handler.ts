import { EventBridgeEvent } from 'aws-lambda';
import { SSMClient, GetParametersCommand } from '@aws-sdk/client-ssm';
import { SESv2Client, SendEmailCommand } from '@aws-sdk/client-sesv2';
import { S3Client, GetObjectCommand } from '@aws-sdk/client-s3';
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

const PARAMETER_KEYS = {
  senderEmail: 'sender-email',
  emailSubject: 'email-subject',
  emailTemplate: 'email-template',
  linkExpirySeconds: 'link-expiry-seconds',
} as const;

const ssmClient = new SSMClient({});
const sesClient = new SESv2Client({});
const s3Client = new S3Client({});

let cachedConfig: HandlerConfig | undefined;

const ensureConfigPath = (): string => {
  const path = process.env.CONFIG_SSM_PARAMETER_PATH;
  if (!path) {
    throw new Error('CONFIG_SSM_PARAMETER_PATH environment variable is required');
  }
  return path.endsWith('/') ? path : `${path}/`;
};

const fetchConfig = async (): Promise<HandlerConfig> => {
  if (cachedConfig) {
    return cachedConfig;
  }

  const parameterPath = ensureConfigPath();
  const parameterNames = Object.values(PARAMETER_KEYS).map((key) => `${parameterPath}${key}`);

  const response = await ssmClient.send(
    new GetParametersCommand({
      Names: parameterNames,
      WithDecryption: true,
    }),
  );

  const resolved = new Map<string, string>();
  (response.Parameters ?? []).forEach((parameter) => {
    if (parameter.Name && parameter.Value !== undefined) {
      resolved.set(parameter.Name, parameter.Value);
    }
  });

  const missing = parameterNames.filter((name) => !resolved.has(name));
  if (missing.length > 0) {
    throw new Error(`Missing SSM parameters: ${missing.join(', ')}`);
  }

  const linkExpirySeconds = Number(resolved.get(`${parameterPath}${PARAMETER_KEYS.linkExpirySeconds}`));
  if (!Number.isFinite(linkExpirySeconds) || linkExpirySeconds <= 0) {
    throw new Error('link-expiry-seconds must be a positive number');
  }

  cachedConfig = {
    senderEmail: resolved.get(`${parameterPath}${PARAMETER_KEYS.senderEmail}`)!,
    emailSubject: resolved.get(`${parameterPath}${PARAMETER_KEYS.emailSubject}`)!,
    emailTemplate: resolved.get(`${parameterPath}${PARAMETER_KEYS.emailTemplate}`)!,
    linkExpirySeconds,
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
};

export const __internal = {
  resetConfigCache: (): void => {
    cachedConfig = undefined;
  },
};
