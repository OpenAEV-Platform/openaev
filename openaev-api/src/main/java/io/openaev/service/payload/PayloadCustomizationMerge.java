package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Payload;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.merge.MergeChoice;
import io.openaev.service.catalog.merge.MergeStatus;
import io.openaev.utils.StringUtils;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Brings a newer Filigran version into a customized copy: what the copy changed stays, the rest
 * follows Filigran, and a block both changed differently is a conflict the user settles.
 */
@Component
@RequiredArgsConstructor
public class PayloadCustomizationMerge {

  private final PayloadChangeKind payloadChangeKind;
  private final PayloadService payloadService;

  public record FieldUpdate(
      CustomizableField field,
      JsonNode base,
      JsonNode applied,
      JsonNode local,
      MergeStatus status) {}

  /** The fields either side changed. */
  public record Update(Payload current, Payload target, List<FieldUpdate> fields) {

    Set<CustomizableBlock> conflicts() {
      return fields.stream()
          .filter(field -> field.status() == MergeStatus.CONFLICT)
          .map(field -> field.field().getBlock())
          .collect(Collectors.toSet());
    }
  }

  /** Empty when Filigran has no newer version that runs. */
  public Optional<Update> update(Payload current) {
    Payload base = current.getBase();
    return newerVersion(base).map(target -> compare(current, base, target));
  }

  /** A local payload linked to the Filigran version it copies, named as a customized copy. */
  public Payload customizedCopy(Payload base) {
    Payload copy = payloadService.generateDuplicatedPayload(base);
    copy.setName(StringUtils.customizedString(base.getName()));
    copy.setBase(base);
    return copy;
  }

  /** The new content of the copy: the newer version, with the changes the copy keeps. */
  public Payload merged(Update update, Map<CustomizableBlock, MergeChoice> choices) {
    if (update.conflicts().stream()
        .map(choices::get)
        .anyMatch(choice -> choice == null || !choice.isSide())) {
      throw new BadRequestException(MergeChoice.MISSING);
    }
    Payload current = update.current();
    Payload merged = customizedCopy(update.target());
    merged.setAuthor(current.getAuthor() == null ? null : current.getAuthor().copy());
    update.fields().stream()
        .filter(field -> field.status().keepsMine(choices.get(field.field().getBlock())))
        .forEach(field -> field.field().copy(current, merged));
    return merged;
  }

  private Optional<Payload> newerVersion(Payload base) {
    return Optional.ofNullable(base.getIdentity().getLatestVersion())
        .map(latest -> (Payload) Hibernate.unproxy(latest))
        .filter(latest -> SemanticVersion.ORDER.compare(latest.getVersion(), base.getVersion()) > 0)
        .filter(latest -> !latest.isRevoked());
  }

  private Update compare(Payload current, Payload base, Payload target) {
    List<PayloadChange> changes = payloadChangeKind.since(base, List.of(target, current));
    PayloadChange applied = changes.get(0);
    PayloadChange local = changes.get(1);
    List<Diff> diffs =
        Arrays.stream(CustomizableField.values())
            .map(
                field ->
                    new Diff(
                        field,
                        applied.before().get(field.getKey()),
                        applied.after().get(field.getKey()),
                        local.after().get(field.getKey())))
            .filter(Diff::changed)
            .toList();
    Set<CustomizableBlock> conflicts = conflicts(diffs);
    List<FieldUpdate> fields =
        diffs.stream().map(diff -> diff.update(conflicts.contains(diff.block()))).toList();
    return new Update(current, target, fields);
  }

  /** The blocks both changed, to different values. */
  private static Set<CustomizableBlock> conflicts(List<Diff> diffs) {
    return diffs.stream().collect(Collectors.groupingBy(Diff::block)).entrySet().stream()
        .filter(
            block ->
                block.getValue().stream().anyMatch(Diff::changedApplied)
                    && block.getValue().stream().anyMatch(Diff::changedLocally)
                    && block.getValue().stream().anyMatch(Diff::differs))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private record Diff(CustomizableField field, JsonNode base, JsonNode applied, JsonNode local) {

    boolean changed() {
      return changedApplied() || changedLocally();
    }

    boolean changedApplied() {
      return !Objects.equals(base, applied);
    }

    boolean changedLocally() {
      return !Objects.equals(base, local);
    }

    boolean differs() {
      return !Objects.equals(applied, local);
    }

    CustomizableBlock block() {
      return field.getBlock();
    }

    FieldUpdate update(boolean conflict) {
      MergeStatus status =
          conflict
              ? MergeStatus.CONFLICT
              : changedLocally() ? MergeStatus.KEEP_MINE : MergeStatus.TAKE_APPLIED;
      return new FieldUpdate(field, base, applied, local, status);
    }
  }
}
