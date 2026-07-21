package io.openaev.rest.notifier.form;

import io.openaev.database.model.Notifier;
import java.util.HashMap;
import org.springframework.stereotype.Component;

@Component
public class NotifierMapper {

  public Notifier toNotifier(final NotifierInput input) {
    Notifier notifier = new Notifier();
    notifier.setName(input.getName());
    notifier.setDescription(input.getDescription());
    notifier.setType(input.getType());
    notifier.setConfiguration(
        input.getConfiguration() != null ? input.getConfiguration() : new HashMap<>());
    return notifier;
  }

  public NotifierOutput toNotifierOutput(final Notifier notifier) {
    return NotifierOutput.builder()
        .id(notifier.getId())
        .name(notifier.getName())
        .description(notifier.getDescription())
        .type(notifier.getType())
        .configuration(notifier.getConfiguration())
        .builtIn(notifier.isBuiltIn())
        .createdAt(notifier.getCreatedAt())
        .updatedAt(notifier.getUpdatedAt())
        .build();
  }
}
