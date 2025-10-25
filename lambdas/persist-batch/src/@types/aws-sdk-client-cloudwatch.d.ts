declare module '@aws-sdk/client-cloudwatch' {
  export class CloudWatchClient {
    constructor(config: Record<string, unknown>);
    send(command: unknown): Promise<unknown>;
  }

  export class PutMetricDataCommand {
    constructor(input: Record<string, unknown>);
  }
}
