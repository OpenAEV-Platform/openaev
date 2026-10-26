package io.openaev.database.model;

import java.util.Arrays;
import java.util.Optional;

/** Kind of resource an XTM Hub news feed item announces. */
public enum NewsFeedType {
  RESOURCE_SCENARIO;

  /** Value stored in {@code User#unsubscribedNewsFeedTypes} when every type is unsubscribed. */
  public static final String ALL_TYPES = "*";

  /** Resolves the type string sent by XTM Hub, empty when OpenAEV does not know it. */
  public static Optional<NewsFeedType> fromHubValue(String value) {
    return Arrays.stream(values()).filter(type -> type.name().equals(value)).findFirst();
  }

  /** Whether a user who unsubscribed from {@code unsubscribedTypes} must not receive this type. */
  public boolean isUnsubscribed(String[] unsubscribedTypes) {
    return Arrays.asList(unsubscribedTypes).contains(ALL_TYPES)
        || Arrays.asList(unsubscribedTypes).contains(name());
  }
}
