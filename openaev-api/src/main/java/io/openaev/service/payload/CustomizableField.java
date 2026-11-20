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

/**
 * What a customized copy may change on its Filigran version: its key, its property and its block.
 */
@Getter
@RequiredArgsConstructor
public enum CustomizableField {
  COMMAND_EXECUTOR("command_executor", "executor", CustomizableBlock.EXECUTION),
  COMMAND_CONTENT("command_content", "content", CustomizableBlock.EXECUTION),
  CLEANUP_EXECUTOR("payload_cleanup_executor", "cleanupExecutor", CustomizableBlock.EXECUTION),
  CLEANUP_COMMAND("payload_cleanup_command", "cleanupCommand", CustomizableBlock.EXECUTION),
  PLATFORMS("payload_platforms", "platforms", CustomizableBlock.PLATFORMS),
  ARGUMENTS("payload_arguments", "arguments", CustomizableBlock.EXECUTION),
  PREREQUISITES("payload_prerequisites", "prerequisites", CustomizableBlock.EXECUTION);

  private final String key;
  private final String property;
  private final CustomizableBlock block;

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
