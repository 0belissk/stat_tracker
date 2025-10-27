const mockSend = jest.fn();

export class CloudWatchClient {
  public send = mockSend;
}

export class PutMetricDataCommand {
  public readonly input: unknown;

  constructor(input: unknown) {
    this.input = input;
  }
}

export const __mockSend = mockSend;
