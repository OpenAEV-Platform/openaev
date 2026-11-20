package io.openaev.service.payload;

import io.openaev.database.model.Payload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

/** What a customized copy may change on its Filigran version: its key and its property. */
@Getter
@RequiredArgsConstructor
public enum CustomizableField {
  COMMAND_EXECUTOR("command_executor", "executor"),
  COMMAND_CONTENT("command_content", "content"),
  CLEANUP_EXECUTOR("payload_cleanup_executor", "cleanupExecutor"),
  CLEANUP_COMMAND("payload_cleanup_command", "cleanupCommand"),
  PLATFORMS("payload_platforms", "platforms"),
  ARGUMENTS("payload_arguments", "arguments"),
  PREREQUISITES("payload_prerequisites", "prerequisites");

  private final String key;
  private final String property;

  public static Optional<CustomizableField> ofKey(String key) {
    return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
  }

  /** Copies the value; a property the payload type does not have is left alone. */
  public void copy(Payload from, Payload to) {
    BeanWrapper source = new BeanWrapperImpl(Hibernate.unproxy(from));
    BeanWrapper target = new BeanWrapperImpl(Hibernate.unproxy(to));
    if (source.isReadableProperty(property) && target.isWritableProperty(property)) {
      target.setPropertyValue(property, copyOf(source.getPropertyValue(property)));
    }
  }

  private static Object copyOf(Object value) {
    if (value instanceof Collection<?> items) {
      return new ArrayList<>(items);
    }
    return value instanceof Object[] array ? array.clone() : value;
  }
}
