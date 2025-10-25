import { EventBridgeEvent } from 'aws-lambda';
import type { ReportCreatedDetail } from './handler';
import { handler, __internal } from './handler';

jest.mock('@aws-sdk/client-secrets-manager', () => {
  const mockSend = jest.fn();
  return {
    SecretsManagerClient: jest.fn(() => ({ send: mockSend })),
    GetSecretValueCommand: jest.fn().mockImplementation((input) => ({ input })),
    __mockSend: mockSend,
  };
});

jest.mock('@aws-sdk/client-sesv2', () => {
  const mockSend = jest.fn();
  return {
    SESv2Client: jest.fn(() => ({ send: mockSend })),
    SendEmailCommand: jest.fn().mockImplementation((input) => input),
    __mockSend: mockSend,
  };
});

jest.mock('@aws-sdk/client-s3', () => ({
  S3Client: jest.fn(() => ({})),
  GetObjectCommand: jest.fn().mockImplementation((input) => ({ input })),
}));

jest.mock('@aws-sdk/s3-request-presigner', () => ({
  getSignedUrl: jest.fn(),
}));

const { __mockSend: mockSecretsSend } = jest.requireMock('@aws-sdk/client-secrets-manager') as {
  __mockSend: jest.Mock;
};

const { __mockSend: mockSesSend } = jest.requireMock('@aws-sdk/client-sesv2') as {
  __mockSend: jest.Mock;
};

const { getSignedUrl: mockGetSignedUrl } = jest.requireMock('@aws-sdk/s3-request-presigner') as {
  getSignedUrl: jest.Mock;
};

describe('notify-report-ready handler', () => {
  const event = (detailOverrides: Partial<ReportCreatedDetail> = {}): EventBridgeEvent<
    'report.created',
    ReportCreatedDetail
  > => ({
    id: '1',
    version: '1',
    account: '123456789012',
    time: new Date().toISOString(),
    region: 'us-east-1',
    resources: [],
    source: 'stat.tracker.reports',
    'detail-type': 'report.created',
    detail: {
      reportId: 'abc-123',
      reportName: 'Quarterly Summary',
      reportType: 'summary',
      recipientEmail: 'coach@example.com',
      recipientName: 'Coach Carter',
      s3Bucket: 'reports-bucket',
      s3Key: 'reports/abc-123.pdf',
      ...detailOverrides,
    },
  });

  beforeEach(() => {
    jest.clearAllMocks();
    __internal.resetConfigCache();
    process.env.CONFIG_SECRET_ARN = 'arn:aws:secretsmanager:us-east-1:123456789012:secret:notify';

    mockSecretsSend.mockResolvedValue({
      SecretString: JSON.stringify({
        senderEmail: 'reports@stat-tracker.dev',
        emailSubject: 'Report ready: {{reportName}}',
        emailTemplate:
          'Hello {{recipientName}}, your {{reportType}} report ({{reportName}}) is ready. Download it here: {{downloadUrl}}',
        linkExpirySeconds: 3600,
      }),
    });

    mockGetSignedUrl.mockResolvedValue('https://signed-url.example.com/report');
    mockSesSend.mockResolvedValue({});
  });

  it('builds and sends an email with a signed download link', async () => {
    await handler(event());

    expect(mockSecretsSend).toHaveBeenCalledWith(
      expect.objectContaining({
        input: expect.objectContaining({
          SecretId: 'arn:aws:secretsmanager:us-east-1:123456789012:secret:notify',
        }),
      }),
    );

    expect(mockGetSignedUrl).toHaveBeenCalledWith(
      expect.any(Object),
      expect.objectContaining({
        input: { Bucket: 'reports-bucket', Key: 'reports/abc-123.pdf' },
      }),
      { expiresIn: 3600 },
    );

    expect(mockSesSend).toHaveBeenCalledWith(
      expect.objectContaining({
        FromEmailAddress: 'reports@stat-tracker.dev',
        Destination: { ToAddresses: ['coach@example.com'] },
        Content: {
          Simple: {
            Subject: { Data: 'Report ready: Quarterly Summary' },
            Body: {
              Text: {
                Data: expect.stringContaining('https://signed-url.example.com/report'),
              },
            },
          },
        },
      }),
    );
  });

  it('reuses cached configuration to avoid repeated secret lookups', async () => {
    await handler(event());
    await handler(event({ reportId: 'xyz-987', s3Key: 'reports/xyz-987.pdf' }));

    expect(mockSecretsSend).toHaveBeenCalledTimes(1);
    expect(mockGetSignedUrl).toHaveBeenCalledTimes(2);
  });

  it('throws when required configuration is missing', async () => {
    mockSecretsSend.mockResolvedValueOnce({ SecretString: '{}' });

    await expect(handler(event())).rejects.toThrow('Configuration secret is missing required fields');
  });
});
