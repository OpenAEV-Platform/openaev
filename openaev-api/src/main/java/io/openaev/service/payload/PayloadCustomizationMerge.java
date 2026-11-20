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
    YOURS,
    FILIGRAN
  }

  public record FieldUpdate(
      CustomizableField field, JsonNode base, JsonNode filigran, JsonNode yours) {

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
  }

  /** The fields either side changed, and the other keys Filigran changed. */
  public record Update(
      Payload current, Payload target, List<FieldUpdate> fields, List<String> otherChanges) {

    /** The blocks both changed, to different values. */
    public Set<CustomizableBlock> conflicts() {
      Map<CustomizableBlock, List<FieldUpdate>> byBlock =
          fields.stream().collect(Collectors.groupingBy(FieldUpdate::block));
      return byBlock.entrySet().stream()
          .filter(
              block ->
                  block.getValue().stream().anyMatch(FieldUpdate::changedByFiligran)
                      && block.getValue().stream().anyMatch(FieldUpdate::changedByYou)
                      && block.getValue().stream().anyMatch(FieldUpdate::differs))
          .map(Map.Entry::getKey)
          .collect(Collectors.toSet());
    }

    /**
     * A conflicting block is taken whole from the side chosen; elsewhere, what you changed stays.
     */
    private boolean keepsYours(
        FieldUpdate field,
        Set<CustomizableBlock> conflicts,
        Map<CustomizableBlock, Choice> choices) {
      return conflicts.contains(field.block())
          ? choices.get(field.block()) == Choice.YOURS
          : field.changedByYou();
    }
  }

  /** Empty when Filigran has no newer version that runs. */
  public Optional<Update> update(Payload current) {
    Payload base = current.getBase();
    return newerVersion(base).map(target -> compare(current, base, target));
  }

  /** The new content of the copy: the newer version, with the changes the copy keeps. */
  public Payload merged(Update update, Map<CustomizableBlock, Choice> choices) {
    Set<CustomizableBlock> conflicts = update.conflicts();
    if (!choices.keySet().containsAll(conflicts)) {
      throw new BadRequestException(CONFLICT_WITHOUT_CHOICE);
    }
    Payload target = update.target();
    Payload current = update.current();
    Payload merged = payloadService.generateDuplicatedPayload(target);
    merged.setName(StringUtils.customizedString(target.getName()));
    merged.setBase(target);
    merged.setAuthor(current.getAuthor() == null ? null : current.getAuthor().copy());
    update.fields().stream()
        .filter(field -> update.keepsYours(field, conflicts, choices))
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
    PayloadChange filigran = payloadChangeKind.between(base, target);
    PayloadChange yours = payloadChangeKind.between(base, current);
    List<FieldUpdate> fields =
        Arrays.stream(CustomizableField.values())
            .map(
                field ->
                    new FieldUpdate(
                        field,
                        filigran.before().get(field.getKey()),
                        filigran.after().get(field.getKey()),
                        yours.after().get(field.getKey())))
            .filter(FieldUpdate::changed)
            .toList();
    List<String> otherChanges =
        filigran.keys().stream()
            .filter(key -> CustomizableField.ofKey(key).isEmpty())
            .sorted()
            .toList();
    return new Update(current, target, fields, otherChanges);
  }
}
