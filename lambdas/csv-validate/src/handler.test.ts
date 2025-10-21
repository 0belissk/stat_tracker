import { S3Event } from 'aws-lambda';
import { handler } from './handler';

jest.mock('@aws-sdk/client-s3', () => {
  const mockSend = jest.fn();

  class GetObjectCommand {
    public readonly input: unknown;
    public readonly __type = 'GetObjectCommand';

    constructor(input: unknown) {
      this.input = input;
    }
  }

  class PutObjectCommand {
    public readonly input: unknown;
    public readonly __type = 'PutObjectCommand';

    constructor(input: unknown) {
      this.input = input;
    }
  }

  return {
    S3Client: jest.fn(() => ({ send: mockSend })),
    GetObjectCommand,
    PutObjectCommand,
    __mockSend: mockSend,
  };
});

const { __mockSend: mockSend } = jest.requireMock('@aws-sdk/client-s3') as {
  __mockSend: jest.Mock;
};

const event = (key: string): S3Event => ({
  Records: [
    {
      eventName: 'ObjectCreated:Put',
      eventVersion: '2.1',
      eventSource: 'aws:s3',
      awsRegion: 'us-east-1',
      s3: {
        bucket: { name: 'raw-bucket' },
        object: { key },
      },
    } as unknown as S3Event['Records'][number],
  ],
});

describe('csv-validate handler', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    delete process.env.ERROR_REPORT_BUCKET;
    delete process.env.ERROR_REPORT_PREFIX;
    delete process.env.ERROR_REPORT_SUFFIX;
  });

  it('writes an error report when validation fails', async () => {
    mockSend.mockImplementation(async (command: { __type: string; input: any }) => {
      if (command.__type === 'GetObjectCommand') {
        return {
          Body: {
            transformToString: async () => 'playerId,playerEmail,serving\nplayer-1,,Great job\n',
          },
        };
      }
      if (command.__type === 'PutObjectCommand') {
        return {};
      }
      throw new Error('Unexpected command');
    });

    await handler(event('coach-1/upload.csv'));

    const putCall = mockSend.mock.calls.find(([cmd]: [{ __type: string }]) => cmd.__type === 'PutObjectCommand');
    expect(putCall).toBeDefined();

    const putInput = (putCall![0] as { input: any }).input;
    expect(putInput.Bucket).toBe('raw-bucket');
    expect(putInput.Key).toBe('errors/coach-1/upload.csv.errors.json');

    const body = JSON.parse(putInput.Body as string);
    expect(body.sourceBucket).toBe('raw-bucket');
    expect(body.sourceKey).toBe('coach-1/upload.csv');
    expect(body.errorCount).toBe(1);
    expect(body.errors[0].messages[0]).toContain('playerEmail');
  });

  it('does not write an error report when CSV is valid', async () => {
    mockSend.mockImplementation(async (command: { __type: string; input: any }) => {
      if (command.__type === 'GetObjectCommand') {
        return {
          Body: {
            transformToString: async () =>
              'playerId,playerEmail,serving,passing\nplayer-1,player@example.com,Great,Consistent\n',
          },
        };
      }
      if (command.__type === 'PutObjectCommand') {
        throw new Error('Should not write error report for valid CSV');
      }
      throw new Error('Unexpected command');
    });

    await handler(event('coach-1/upload.csv'));

    expect(
      mockSend.mock.calls.filter(([cmd]: [{ __type: string }]) => cmd.__type === 'PutObjectCommand'),
    ).toHaveLength(0);
  });

  it('skips objects in the error prefix to avoid recursion', async () => {
    mockSend.mockResolvedValue({
      Body: { transformToString: async () => '' },
    });

    await handler(event('errors/coach-1/upload.csv.errors.json'));

    expect(mockSend).not.toHaveBeenCalled();
  });
});
