package software.amazon.awssdk.services.dynamodb;

import java.net.URI;

public class DynamoDbClientBuilder {

  public DynamoDbClientBuilder region(Object ignored) {
    return this;
  }

  public DynamoDbClientBuilder endpointOverride(URI ignored) {
    return this;
  }

  public DynamoDbClient build() {
    return request -> {
      throw new UnsupportedOperationException(
          "No DynamoDB client implementation is available in this offline test environment.");
    };
  }
}
