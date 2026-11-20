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

  // The names of the values a skipped row hands to the client, which writes the sentence of its
  // reason in its own language.
  static final String PARAM_MAX_LENGTH = "max_length";
  // The position of the earlier row of the file a conflict is with, absent for an existing marking.
  static final String PARAM_EARLIER_ROW = "earlier_row";
  static final String PARAM_COLOR = "color";
  static final String PARAM_KNOWN_COLOR = "known_color";
  static final String PARAM_ORDER = "order";
  static final String PARAM_KNOWN_ORDER = "known_order";
  // Absent when the caller holds no marking of the type.
  static final String PARAM_HIGHEST_ORDER = "highest_order";

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
          new KnownMarking(marking.getColor(), marking.getOrder(), null));
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
      return skipped(row, MarkingDefinitionImportRowReason.MISSING_FIELD, Map.of());
    }
    if (type.length() > MAX_TEXT_LENGTH || definition.length() > MAX_TEXT_LENGTH) {
      return skipped(
          row,
          MarkingDefinitionImportRowReason.VALUE_TOO_LONG,
          Map.of(PARAM_MAX_LENGTH, String.valueOf(MAX_TEXT_LENGTH)));
    }
    if (!HEX_COLOR.matcher(row.color()).matches()) {
      return skipped(row, MarkingDefinitionImportRowReason.INVALID_COLOR, Map.of());
    }
    Integer order = parseOrder(row.order());
    if (order == null) {
      return skipped(row, MarkingDefinitionImportRowReason.INVALID_ORDER, Map.of());
    }
    MarkingKey key = new MarkingKey(type, definition);
    KnownMarking known = inTenant.getOrDefault(key, inFile.get(key));
    if (known != null) {
      return sameKey(row, known, order);
    }
    if (types.contains(type) && !isWithinClearance(type, order)) {
      return skipped(
          row, MarkingDefinitionImportRowReason.INSUFFICIENT_ACCESS, notClearedParams(type, order));
    }
    created.add(buildEntity(tenantId, type, definition, row.color(), order));
    types.add(type);
    inFile.put(key, new KnownMarking(row.color(), order, row.position()));
    return new ImportResultRow(
        row.position(), ImportRowStatus.CREATED, null, Map.of(), valuesOf(row));
  }

  // Never overwritten: identical is ignored, anything else is a conflict.
  private ImportResultRow sameKey(MarkingRow row, KnownMarking known, int order) {
    Map<String, String> params = new LinkedHashMap<>();
    if (known.fileRow() != null) {
      params.put(PARAM_EARLIER_ROW, String.valueOf(known.fileRow()));
    }
    boolean sameColor = known.color().equalsIgnoreCase(row.color());
    if (!sameColor) {
      params.put(PARAM_COLOR, row.color());
      params.put(PARAM_KNOWN_COLOR, known.color());
    }
    boolean sameOrder = known.order() == order;
    if (!sameOrder) {
      params.put(PARAM_ORDER, String.valueOf(order));
      params.put(PARAM_KNOWN_ORDER, String.valueOf(known.order()));
    }
    if (sameColor && sameOrder) {
      return new ImportResultRow(
          row.position(), ImportRowStatus.IGNORED, null, Map.of(), valuesOf(row));
    }
    return skipped(row, MarkingDefinitionImportRowReason.CONFLICT, params);
  }

  private boolean isWithinClearance(String type, int order) {
    return held == null || MarkingEscalationValidator.isWithinClearance(held, type, order);
  }

  private Map<String, String> notClearedParams(String type, int order) {
    OptionalInt highest = MarkingEscalationValidator.highestHeldOrder(held, type);
    return highest.isPresent()
        ? Map.of(
            PARAM_ORDER,
            String.valueOf(order),
            PARAM_HIGHEST_ORDER,
            String.valueOf(highest.getAsInt()))
        : Map.of();
  }

  private static ImportResultRow skipped(
      MarkingRow row, MarkingDefinitionImportRowReason reason, Map<String, String> params) {
    return new ImportResultRow(
        row.position(), ImportRowStatus.SKIPPED, reason.name(), params, valuesOf(row));
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
   * @param fileRow the position of the row of the file that created it, {@code null} for a marking
   *     the tenant already holds
   */
  private record KnownMarking(String color, int order, Integer fileRow) {}
}
