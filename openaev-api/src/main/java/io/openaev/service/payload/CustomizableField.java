package io.openaev.service.payload;

import io.openaev.database.model.Command;
import io.openaev.database.model.Payload;
import io.openaev.rest.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.hibernate.Hibernate;

/** What a customized copy may change on its Filigran version: its key, its block and its copy. */
@Getter
public enum CustomizableField {
  COMMAND_EXECUTOR(
      "command_executor",
      CustomizableBlock.EXECUTION,
      (from, to) -> command(to).setExecutor(command(from).getExecutor())),
  COMMAND_CONTENT(
      "command_content",
      CustomizableBlock.EXECUTION,
      (from, to) -> command(to).setContent(command(from).getContent())),
  CLEANUP_EXECUTOR(
      "payload_cleanup_executor",
      CustomizableBlock.EXECUTION,
      (from, to) -> to.setCleanupExecutor(from.getCleanupExecutor())),
  CLEANUP_COMMAND(
      "payload_cleanup_command",
      CustomizableBlock.EXECUTION,
      (from, to) -> to.setCleanupCommand(from.getCleanupCommand())),
  PLATFORMS(
      "payload_platforms",
      CustomizableBlock.PLATFORMS,
      (from, to) -> to.setPlatforms(ArrayUtils.clone(from.getPlatforms()))),
  ARGUMENTS(
      "payload_arguments",
      CustomizableBlock.EXECUTION,
      (from, to) -> to.setArguments(new ArrayList<>(from.getArguments()))),
  PREREQUISITES(
      "payload_prerequisites",
      CustomizableBlock.EXECUTION,
      (from, to) -> to.setPrerequisites(new ArrayList<>(from.getPrerequisites())));

  public static final String LOCKED_ON_CUSTOMIZED_COPY =
      "A customized copy only changes its commands, arguments, prerequisites and platforms";

  private final String key;
  private final CustomizableBlock block;

  @Getter(AccessLevel.NONE)
  private final BiConsumer<Payload, Payload> copier;

  CustomizableField(String key, CustomizableBlock block, BiConsumer<Payload, Payload> copier) {
    this.key = key;
    this.block = block;
    this.copier = copier;
  }

  public static Optional<CustomizableField> ofKey(String key) {
    return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
  }

  /** A customized copy takes everything else from its Filigran version. */
  public static void refuseLockedChanges(Payload current, PayloadChange change) {
    if (current.isCustomized() && change.keys().stream().anyMatch(key -> ofKey(key).isEmpty())) {
      throw new BadRequestException(LOCKED_ON_CUSTOMIZED_COPY);
    }
  }

  public void copy(Payload from, Payload to) {
    copier.accept((Payload) Hibernate.unproxy(from), (Payload) Hibernate.unproxy(to));
  }

  private static Command command(Payload payload) {
    if (payload instanceof Command command) {
      return command;
    }
    throw new IllegalStateException("Only a command payload has a command");
  }
}
