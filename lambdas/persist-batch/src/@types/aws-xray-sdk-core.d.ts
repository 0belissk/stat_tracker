declare module 'aws-xray-sdk-core' {
  export function captureAWSv3Client<T>(client: T): T;
  export function getSegment():
    | {
        addAnnotation(key: string, value: unknown): void;
        addMetadata(key: string, value: unknown): void;
      }
    | undefined;
}
