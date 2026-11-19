package io.openaev.service.marking_definition;

import static io.openaev.config.AppConfig.HEX_COLOR_REGEXP;
import static io.openaev.service.marking_definition.MarkingDefinitionFactory.buildEntity;
import static io.openaev.service.marking_definition.MarkingDefinitionFactory.normalize;

import io.openaev.database.model.MarkingDefinition;
import io.openaev.service.import_report.ImportResult;
import io.openaev.service.import_report.ImportResultRow;
import io.openaev.service.import_report.ImportRowStatus;
import io.openaev.service.marking.MarkingEscalationValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the rows of one imported file against what the tenant already holds and against the
 * caller's clearance, and builds the markings to create. It reads and writes nothing: the service
 * loads what it needs and saves what {@link #created()} gives back.
 *
 * <p>A bad row never throws: it is skipped and listed in the result. Nothing is ever overwritten.
 */
final class MarkingImportBatch {

  private static final List<String> COLUMNS =
      List.of(
          MarkingCsvParser.COLUMN_TYPE,
          MarkingCsvParser.COLUMN_DEFINITION,
          MarkingCsvParser.COLUMN_COLOR,
          MarkingCsvParser.COLUMN_ORDER);
  private static final Pattern HEX_COLOR = Pattern.compile(HEX_COLOR_REGEXP);
  private static final Pattern DIGITS = Pattern.compile("[0-9]+");
  // The width of the type and definition columns.
  private static final int MAX_TEXT_LENGTH = 255;

  private final String tenantId;
  private final Map<MarkingKey, KnownMarking> inTenant = new HashMap<>();
  private final Map<MarkingKey, KnownMarking> inFile = new HashMap<>();
  // The types the tenant has, or the file created so far: the first definition of a type is not
  // above any clearance.
  private final Set<String> types = new HashSet<>();
  // Null when the caller holds every level.
  private final List<MarkingDefinition> held;
  private final List<MarkingDefinition> created = new ArrayList<>();

  /**
   * @param existing the markings of the tenant
   * @param held the markings the caller's clearance covers, {@code null} when they hold every level
   */
  MarkingImportBatch(
      String tenantId, List<MarkingDefinition> existing, List<MarkingDefinition> held) {
    this.tenantId = tenantId;
    this.held = held;
    for (MarkingDefinition marking : existing) {
      String type = normalize(marking.getType());
      types.add(type);
      inTenant.put(
          new MarkingKey(type, normalize(marking.getDefinition())),
          new KnownMarking(
              marking.getColor(),
              marking.getOrder(),
              "an existing marking",
              "the existing marking"));
    }
  }

  ImportResult importRows(List<MarkingRow> rows) {
    return ImportResult.of(COLUMNS, rows.stream().map(this::importRow).toList());
  }

  List<MarkingDefinition> created() {
    return created;
  }

  // The checks run in this order and the first failure wins.
  private ImportResultRow importRow(MarkingRow row) {
    String type = normalize(row.type());
    String definition = normalize(row.definition());
    if (type.isEmpty() || definition.isEmpty() || row.color().isEmpty() || row.order().isEmpty()) {
      return skipped(
          row,
          MarkingDefinitionImportRowReason.MISSING_FIELD,
          "A value is missing: type, definition, color and order are all required");
    }
    if (type.length() > MAX_TEXT_LENGTH || definition.length() > MAX_TEXT_LENGTH) {
      return skipped(
          row,
          MarkingDefinitionImportRowReason.VALUE_TOO_LONG,
          "The type and the definition must have " + MAX_TEXT_LENGTH + " characters at most");
    }
    if (!HEX_COLOR.matcher(row.color()).matches()) {
      return skipped(
          row,
          MarkingDefinitionImportRowReason.INVALID_COLOR,
          "The color must be a hex value such as #4CAF50");
    }
    Integer order = parseOrder(row.order());
    if (order == null) {
      return skipped(
          row,
          MarkingDefinitionImportRowReason.INVALID_ORDER,
          "The order must be a whole number, 0 or more");
    }
    MarkingKey key = new MarkingKey(type, definition);
    KnownMarking known = inTenant.getOrDefault(key, inFile.get(key));
    if (known != null) {
      return sameKey(row, known, order);
    }
    if (types.contains(type) && !isWithinClearance(type, order)) {
      return skipped(
          row,
          MarkingDefinitionImportRowReason.INSUFFICIENT_ACCESS,
          notClearedMessage(type, order));
    }
    created.add(buildEntity(tenantId, type, definition, row.color(), order));
    types.add(type);
    inFile.put(
        key,
        new KnownMarking(
            row.color(), order, "row " + row.position() + " of the file", "row " + row.position()));
    return new ImportResultRow(row.position(), ImportRowStatus.CREATED, null, null, valuesOf(row));
  }

  // Never overwritten: identical is ignored, anything else is a conflict.
  private ImportResultRow sameKey(MarkingRow row, KnownMarking known, int order) {
    List<String> differences = new ArrayList<>();
    if (!known.color().equalsIgnoreCase(row.color())) {
      differences.add(
          "color differs (this row %s, %s has %s)"
              .formatted(row.color(), known.subject(), known.color()));
    }
    if (known.order() != order) {
      differences.add(
          "order differs (this row %d, %s has %d)"
              .formatted(order, known.subject(), known.order()));
    }
    if (differences.isEmpty()) {
      return new ImportResultRow(
          row.position(), ImportRowStatus.IGNORED, null, null, valuesOf(row));
    }
    return skipped(
        row,
        MarkingDefinitionImportRowReason.CONFLICT,
        "Conflicts with %s: %s".formatted(known.place(), String.join(" and ", differences)));
  }

  private boolean isWithinClearance(String type, int order) {
    return held == null || MarkingEscalationValidator.isWithinClearance(held, type, order);
  }

  private String notClearedMessage(String type, int order) {
    OptionalInt highest = MarkingEscalationValidator.highestHeldOrder(held, type);
    return highest.isPresent()
        ? "Its order (%d) is above the highest order you hold for its type (%d)"
            .formatted(order, highest.getAsInt())
        : "You hold no marking of its type";
  }

  private static ImportResultRow skipped(
      MarkingRow row, MarkingDefinitionImportRowReason reason, String message) {
    return new ImportResultRow(
        row.position(), ImportRowStatus.SKIPPED, reason.name(), message, valuesOf(row));
  }

  private static Map<String, String> valuesOf(MarkingRow row) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(MarkingCsvParser.COLUMN_TYPE, row.type());
    values.put(MarkingCsvParser.COLUMN_DEFINITION, row.definition());
    values.put(MarkingCsvParser.COLUMN_COLOR, row.color());
    values.put(MarkingCsvParser.COLUMN_ORDER, row.order());
    return values;
  }

  // Digits only, and one an int can hold.
  private static Integer parseOrder(String order) {
    if (!DIGITS.matcher(order).matches()) {
      return null;
    }
    try {
      return Integer.parseInt(order);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private record MarkingKey(String type, String definition) {}

  /**
   * A marking a row can clash with.
   *
   * @param place where it is, for the start of a message
   * @param subject how it is called when comparing a value of it
   */
  private record KnownMarking(String color, int order, String place, String subject) {}
}
