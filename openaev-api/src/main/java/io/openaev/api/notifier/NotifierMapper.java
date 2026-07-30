package io.openaev.api.notifier;

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

  /**
   * Maps a notifier to its output DTO. The configuration (webhook URLs, headers, templates...) can
   * contain sensitive values, so it is only included when the caller is allowed to see it
   * (capability-gated, see NotifierApi); plain users picking notifiers for their triggers only get
   * id/name/type metadata.
   */
  public NotifierOutput toNotifierOutput(
      final Notifier notifier, final boolean includeConfiguration) {
    return NotifierOutput.builder()
        .id(notifier.getId())
        .name(notifier.getName())
        .description(notifier.getDescription())
        .type(notifier.getType())
        .configuration(includeConfiguration ? notifier.getConfiguration() : null)
        .builtIn(notifier.isBuiltIn())
        .createdAt(notifier.getCreatedAt())
        .updatedAt(notifier.getUpdatedAt())
        .build();
  }
}
