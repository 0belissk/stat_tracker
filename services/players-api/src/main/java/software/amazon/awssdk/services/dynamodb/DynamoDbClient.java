package software.amazon.awssdk.services.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public interface DynamoDbClient {
  void putItem(PutItemRequest request);

  static DynamoDbClientBuilder builder() {
    return new DynamoDbClientBuilder();
  }
}
