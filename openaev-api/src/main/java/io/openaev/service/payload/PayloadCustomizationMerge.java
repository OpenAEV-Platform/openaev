package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Payload;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.payload.service.PayloadService;
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

  public static final String CONFLICT_WITHOUT_CHOICE =
      "Choose Filigran's or yours for every conflicting block";

  private final PayloadChangeKind payloadChangeKind;
  private final PayloadService payloadService;

  public enum Choice {
    KEEP_MINE,
    TAKE_APPLIED
  }

  /** What the update does with a field, named as the scenario update names it. */
  public enum Status {
    TAKE_APPLIED,
    KEEP_MINE,
    CONFLICT;

    boolean keepsMine(Choice choice) {
      return this == CONFLICT ? choice == Choice.KEEP_MINE : this == KEEP_MINE;
    }
  }

  public record FieldUpdate(
      CustomizableField field, JsonNode base, JsonNode filigran, JsonNode yours, Status status) {}

  /** The fields either side changed, and the other keys Filigran changed. */
  public record Update(
      Payload current, Payload target, List<FieldUpdate> fields, List<String> otherChanges) {

    Set<CustomizableBlock> conflicts() {
      return fields.stream()
          .filter(field -> field.status() == Status.CONFLICT)
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
  public Payload merged(Update update, Map<CustomizableBlock, Choice> choices) {
    if (!choices.keySet().containsAll(update.conflicts())) {
      throw new BadRequestException(CONFLICT_WITHOUT_CHOICE);
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
    PayloadChange filigran = changes.get(0);
    PayloadChange yours = changes.get(1);
    List<Diff> diffs =
        Arrays.stream(CustomizableField.values())
            .map(
                field ->
                    new Diff(
                        field,
                        filigran.before().get(field.getKey()),
                        filigran.after().get(field.getKey()),
                        yours.after().get(field.getKey())))
            .filter(Diff::changed)
            .toList();
    Set<CustomizableBlock> conflicts = conflicts(diffs);
    List<FieldUpdate> fields =
        diffs.stream().map(diff -> diff.update(conflicts.contains(diff.block()))).toList();
    List<String> otherChanges =
        filigran.keys().stream()
            .filter(key -> CustomizableField.ofKey(key).isEmpty())
            .sorted()
            .toList();
    return new Update(current, target, fields, otherChanges);
  }

  /** The blocks both changed, to different values. */
  private static Set<CustomizableBlock> conflicts(List<Diff> diffs) {
    return diffs.stream().collect(Collectors.groupingBy(Diff::block)).entrySet().stream()
        .filter(
            block ->
                block.getValue().stream().anyMatch(Diff::changedByFiligran)
                    && block.getValue().stream().anyMatch(Diff::changedByYou)
                    && block.getValue().stream().anyMatch(Diff::differs))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private record Diff(CustomizableField field, JsonNode base, JsonNode filigran, JsonNode yours) {

    boolean changed() {
      return changedByFiligran() || changedByYou();
    }

    boolean changedByFiligran() {
      return !Objects.equals(base, filigran);
    }

    boolean changedByYou() {
      return !Objects.equals(base, yours);
    }

    boolean differs() {
      return !Objects.equals(filigran, yours);
    }

    CustomizableBlock block() {
      return field.getBlock();
    }

    FieldUpdate update(boolean conflict) {
      Status status =
          conflict ? Status.CONFLICT : changedByYou() ? Status.KEEP_MINE : Status.TAKE_APPLIED;
      return new FieldUpdate(field, base, filigran, yours, status);
    }
  }
}
