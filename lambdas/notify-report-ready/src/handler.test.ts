import { EventBridgeEvent } from 'aws-lambda';
import type { ReportCreatedDetail } from './handler';
import { handler, __internal } from './handler';

jest.mock('@aws-sdk/client-ssm', () => {
  const mockSend = jest.fn();
  return {
    SSMClient: jest.fn(() => ({ send: mockSend })),
    GetParametersCommand: jest.fn().mockImplementation((input) => ({ input })),
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

const { __mockSend: mockSsmSend } = jest.requireMock('@aws-sdk/client-ssm') as {
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
    process.env.CONFIG_SSM_PARAMETER_PATH = '/stat-tracker/notify-report-ready/';

    mockSsmSend.mockResolvedValue({
      Parameters: [
        {
          Name: '/stat-tracker/notify-report-ready/sender-email',
          Value: 'reports@stat-tracker.dev',
        },
        {
          Name: '/stat-tracker/notify-report-ready/email-subject',
          Value: 'Report ready: {{reportName}}',
        },
        {
          Name: '/stat-tracker/notify-report-ready/email-template',
          Value:
            'Hello {{recipientName}}, your {{reportType}} report ({{reportName}}) is ready. Download it here: {{downloadUrl}}',
        },
        {
          Name: '/stat-tracker/notify-report-ready/link-expiry-seconds',
          Value: '3600',
        },
      ],
    });

    mockGetSignedUrl.mockResolvedValue('https://signed-url.example.com/report');
    mockSesSend.mockResolvedValue({});
  });

  it('builds and sends an email with a signed download link', async () => {
    await handler(event());

    expect(mockSsmSend).toHaveBeenCalledWith(
      expect.objectContaining({
        input: expect.objectContaining({
          Names: expect.arrayContaining([
            '/stat-tracker/notify-report-ready/sender-email',
            '/stat-tracker/notify-report-ready/email-subject',
            '/stat-tracker/notify-report-ready/email-template',
            '/stat-tracker/notify-report-ready/link-expiry-seconds',
          ]),
          WithDecryption: true,
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

  it('reuses cached configuration to avoid repeated SSM lookups', async () => {
    await handler(event());
    await handler(event({ reportId: 'xyz-987', s3Key: 'reports/xyz-987.pdf' }));

    expect(mockSsmSend).toHaveBeenCalledTimes(1);
    expect(mockGetSignedUrl).toHaveBeenCalledTimes(2);
  });

  it('throws when required configuration is missing', async () => {
    mockSsmSend.mockResolvedValueOnce({ Parameters: [] });

    await expect(handler(event())).rejects.toThrow(
      'Missing SSM parameters: /stat-tracker/notify-report-ready/sender-email, /stat-tracker/notify-report-ready/email-subject, /stat-tracker/notify-report-ready/email-template, /stat-tracker/notify-report-ready/link-expiry-seconds',
    );
  });
});
