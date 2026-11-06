package io.openaev.utils;

import io.openaev.database.model.NewsFeedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class NewsFeedUtils {

  /** Value stored in {@code User#unsubscribedNewsFeedTypes} when every type is unsubscribed. */
  public static final String ALL_TYPES = "*";

  private NewsFeedUtils() {}

  /** Resolves the type string sent by XTM Hub, empty when OpenAEV does not know it. */
  public static Optional<NewsFeedType> fromHubValue(String value) {
    return Arrays.stream(NewsFeedType.values())
        .filter(type -> type.name().equals(value))
        .findFirst();
  }

  /** Whether a user who unsubscribed from {@code unsubscribedTypes} must not receive this type. */
  public static boolean isUnsubscribed(NewsFeedType type, String[] unsubscribedTypes) {
    if (unsubscribedTypes == null) {
      return false;
    }
    List<String> unsubscribed = Arrays.asList(unsubscribedTypes);
    return unsubscribed.contains(ALL_TYPES) || unsubscribed.contains(type.name());
  }

  /** The known types a user who unsubscribed from {@code unsubscribedTypes} still receives. */
  public static List<NewsFeedType> subscribedTypes(String[] unsubscribedTypes) {
    return Arrays.stream(NewsFeedType.values())
        .filter(type -> !isUnsubscribed(type, unsubscribedTypes))
        .toList();
  }

  /**
   * The value to store in {@code User#unsubscribedNewsFeedTypes} for a user who only subscribes to
   * {@code subscribedTypes}: {@link #ALL_TYPES} when there is none, so that a type added later is
   * not delivered to someone who opted out of everything, else the known types left out.
   */
  public static String[] toUnsubscribedTypes(Collection<NewsFeedType> subscribedTypes) {
    if (subscribedTypes.isEmpty()) {
      return new String[] {ALL_TYPES};
    }
    return Arrays.stream(NewsFeedType.values())
        .filter(type -> !subscribedTypes.contains(type))
        .map(NewsFeedType::name)
        .toArray(String[]::new);
  }
}
