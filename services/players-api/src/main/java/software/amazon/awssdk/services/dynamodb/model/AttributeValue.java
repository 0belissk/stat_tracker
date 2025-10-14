package software.amazon.awssdk.services.dynamodb.model;

import java.util.Collections;
import java.util.Map;

public final class AttributeValue {
  private final String s;
  private final Map<String, AttributeValue> m;

  private AttributeValue(Builder builder) {
    this.s = builder.s;
    this.m = builder.m == null ? null : Collections.unmodifiableMap(builder.m);
  }

  public String s() {
    return s;
  }

  public Map<String, AttributeValue> m() {
    return m;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String s;
    private Map<String, AttributeValue> m;

    private Builder() {}

    public Builder s(String value) {
      this.s = value;
      return this;
    }

    public Builder m(Map<String, AttributeValue> value) {
      this.m = value;
      return this;
    }

    public AttributeValue build() {
      return new AttributeValue(this);
    }
  }
}
