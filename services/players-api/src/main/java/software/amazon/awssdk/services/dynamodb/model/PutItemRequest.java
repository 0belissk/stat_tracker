package software.amazon.awssdk.services.dynamodb.model;

import java.util.Collections;
import java.util.Map;

public final class PutItemRequest {
  private final String tableName;
  private final Map<String, AttributeValue> item;
  private final String conditionExpression;

  private PutItemRequest(Builder builder) {
    this.tableName = builder.tableName;
    this.item = builder.item == null ? null : Collections.unmodifiableMap(builder.item);
    this.conditionExpression = builder.conditionExpression;
  }

  public String tableName() {
    return tableName;
  }

  public Map<String, AttributeValue> item() {
    return item;
  }

  public String conditionExpression() {
    return conditionExpression;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String tableName;
    private Map<String, AttributeValue> item;
    private String conditionExpression;

    private Builder() {}

    public Builder tableName(String value) {
      this.tableName = value;
      return this;
    }

    public Builder item(Map<String, AttributeValue> value) {
      this.item = value;
      return this;
    }

    public Builder conditionExpression(String value) {
      this.conditionExpression = value;
      return this;
    }

    public PutItemRequest build() {
      return new PutItemRequest(this);
    }
  }
}
