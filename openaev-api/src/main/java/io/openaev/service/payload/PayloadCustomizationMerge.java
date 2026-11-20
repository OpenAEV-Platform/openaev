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
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Brings a newer Filigran version into a customized copy: what the copy changed stays, the rest
 * follows Filigran, and a field both changed differently is a conflict the user settles.
 */
@Component
@RequiredArgsConstructor
public class PayloadCustomizationMerge {

  public static final String CONFLICT_WITHOUT_CHOICE =
      "Choose Filigran's or yours for every conflicting field";

  private final PayloadChangeKind payloadChangeKind;
  private final PayloadService payloadService;

  public enum Choice {
    YOURS,
    FILIGRAN
  }

  public record FieldUpdate(
      CustomizableField field, JsonNode base, JsonNode filigran, JsonNode yours) {

    public boolean conflict() {
      return changedByFiligran() && changedByYou() && !Objects.equals(filigran, yours);
    }

    boolean changed() {
      return changedByFiligran() || changedByYou();
    }

    boolean keepsYours(Choice choice) {
      return changedByYou() && !(conflict() && choice == Choice.FILIGRAN);
    }

    private boolean changedByFiligran() {
      return !Objects.equals(base, filigran);
    }

    private boolean changedByYou() {
      return !Objects.equals(base, yours);
    }
  }

  /** The fields either side changed, and the other keys Filigran changed. */
  public record Update(
      Payload current, Payload target, List<FieldUpdate> fields, List<String> otherChanges) {}

  /** Empty when Filigran has no newer version that runs. */
  public Optional<Update> update(Payload current) {
    Payload base = current.getBase();
    return newerVersion(base).map(target -> compare(current, base, target));
  }

  /** The new content of the copy: the newer version, with the changes the copy keeps. */
  public Payload merged(Update update, Map<CustomizableField, Choice> choices) {
    if (update.fields().stream()
        .anyMatch(field -> field.conflict() && choices.get(field.field()) == null)) {
      throw new BadRequestException(CONFLICT_WITHOUT_CHOICE);
    }
    Payload target = update.target();
    Payload current = update.current();
    Payload merged = payloadService.generateDuplicatedPayload(target);
    merged.setName(StringUtils.customizedString(target.getName()));
    merged.setBase(target);
    merged.setAuthor(current.getAuthor() == null ? null : current.getAuthor().copy());
    update.fields().stream()
        .filter(field -> field.keepsYours(choices.get(field.field())))
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
