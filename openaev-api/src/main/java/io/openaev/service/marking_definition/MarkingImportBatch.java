package io.openaev.service.marking_definition;

import static io.openaev.api.marking_definition.MarkingDefinitionMapper.toEntity;
import static io.openaev.service.marking_definition.MarkingDefinitionNormalizer.normalize;

import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.MarkingDefinitionStixIds;
import io.openaev.service.import_report.ImportResultRow;
import io.openaev.service.import_report.ImportRowReason;
import io.openaev.service.import_report.ImportRowStatus;
import io.openaev.service.import_report.RowImportResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the rows of one imported file against what the tenant already holds and against the
 * caller's clearance, and builds the markings to create. It reads and writes nothing: the service
 * loads what it needs and saves the markings of the {@link Outcome}.
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
  private static final Pattern DIGITS = Pattern.compile("[0-9]+");

  // The names of the values a skipped row hands to the client, which writes the sentence of its
  // reason in its own language.
  static final String PARAM_MAX_LENGTH = "max_length";
  // The column an invalid value is in.
  static final String PARAM_COLUMN = "column";
  // The position of the earlier row of the file a conflict is with, absent for an existing marking.
  static final String PARAM_EARLIER_ROW = "earlier_row";
  static final String PARAM_COLOR = "color";
  static final String PARAM_KNOWN_COLOR = "known_color";
  static final String PARAM_ORDER = "order";
  static final String PARAM_KNOWN_ORDER = "known_order";
  // Absent when the caller holds no marking of the type.
  static final String PARAM_HIGHEST_ORDER = "highest_order";

  private final String tenantId;
  private final Validator validator;
  private final Map<MarkingKey, KnownMarking> inTenant = new HashMap<>();
  private final Map<MarkingKey, KnownMarking> inFile = new HashMap<>();
  // The types the tenant has, or the file created so far: the first definition of a type is not
  // above any clearance.
  private final Set<String> types = new HashSet<>();
  // The STIX ids the tenant carries, or the file gave so far.
  private final Set<String> stixIds = new HashSet<>();
  private final Clearance clearance;
  private final List<MarkingDefinition> created = new ArrayList<>();

  /**
   * @param existing the markings of the tenant
   * @param clearance what the caller may place
   * @param validator checks a row against the rules of {@link MarkingDefinitionInput}
   */
  MarkingImportBatch(
      String tenantId, List<MarkingDefinition> existing, Clearance clearance, Validator validator) {
    this.tenantId = tenantId;
    this.clearance = clearance;
    this.validator = validator;
    for (MarkingDefinition marking : existing) {
      String type = normalize(marking.getType());
      types.add(type);
      if (marking.getStixId() != null) {
        stixIds.add(marking.getStixId());
      }
      inTenant.put(
          new MarkingKey(type, normalize(marking.getDefinition())),
          new KnownMarking(marking.getColor(), marking.getOrder(), null));
    }
  }

  /**
   * What an import gives back.
   *
   * @param result the report of each row
   * @param created the markings to save, in the order of the rows
   */
  record Outcome(RowImportResult result, List<MarkingDefinition> created) {}

  Outcome importRows(List<MarkingRow> rows) {
    RowImportResult result =
        RowImportResult.of(COLUMNS, rows.stream().map(this::importRow).toList());
    return new Outcome(result, List.copyOf(created));
  }

  // The rules of the form decide whether the values are valid. The first failure of the priority of
  // Refusal wins, then come the conflicts and the clearance.
  private ImportResultRow importRow(MarkingRow row) {
    String type = normalize(row.type());
    String definition = normalize(row.definition());
    Integer order = parseOrder(row.order());
    Optional<Refusal> invalid =
        invalidValue(row, new MarkingDefinitionInput(type, definition, row.color(), order));
    if (invalid.isPresent()) {
      return skipped(row, invalid.get().reason(), invalid.get().params());
    }
    MarkingKey key = new MarkingKey(type, definition);
    KnownMarking known = inTenant.getOrDefault(key, inFile.get(key));
    if (known != null) {
      return sameKey(row, known, order);
    }
    if (types.contains(type) && !clearance.allows(type, order)) {
      return skipped(row, ImportRowReason.INSUFFICIENT_ACCESS, notClearedParams(type, order));
    }
    created.add(
        toEntity(tenantId, type, definition, row.color(), order, freeStixId(type, definition)));
    types.add(type);
    inFile.put(key, new KnownMarking(row.color(), order, row.position()));
    return new ImportResultRow(
        row.position(), ImportRowStatus.CREATED, null, Map.of(), valuesOf(row));
  }

  // Derived as when a marking is created by hand: an id another marking of the tenant, or an
  // earlier row of the file, already carries is not repeated, the new marking gets none.
  private String freeStixId(String type, String definition) {
    String stixId = MarkingDefinitionStixIds.generate(type, definition);
    return stixIds.add(stixId) ? stixId : null;
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
    return skipped(row, ImportRowReason.CONFLICT, params);
  }

  // `order` is null when the cell is empty or not a number: the form cannot tell them apart, the
  // file can.
  private Optional<Refusal> invalidValue(MarkingRow row, MarkingDefinitionInput input) {
    List<Refusal> refusals =
        validator.validate(input).stream()
            .map(MarkingImportBatch::refusalOf)
            .collect(Collectors.toCollection(ArrayList::new));
    if (input.order() == null && !row.order().isEmpty()) {
      refusals.removeIf(refusal -> MarkingCsvParser.COLUMN_ORDER.equals(refusal.column()));
      refusals.add(
          new Refusal(
              ImportRowReason.INVALID_VALUE,
              MarkingCsvParser.COLUMN_ORDER,
              Map.of(PARAM_COLUMN, MarkingCsvParser.COLUMN_ORDER)));
    }
    return refusals.stream().min(Comparator.naturalOrder());
  }

  // The properties of MarkingDefinitionInput are named like the columns of the file. A constraint
  // this does not know is an invalid value of its column.
  private static Refusal refusalOf(ConstraintViolation<MarkingDefinitionInput> violation) {
    String column = violation.getPropertyPath().toString();
    Class<?> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
    if (constraint == NotBlank.class || constraint == NotNull.class) {
      return new Refusal(ImportRowReason.MISSING_FIELD, column, Map.of());
    }
    if (constraint == Size.class) {
      Object max = violation.getConstraintDescriptor().getAttributes().get("max");
      return new Refusal(
          ImportRowReason.VALUE_TOO_LONG, column, Map.of(PARAM_MAX_LENGTH, String.valueOf(max)));
    }
    return new Refusal(ImportRowReason.INVALID_VALUE, column, Map.of(PARAM_COLUMN, column));
  }

  /**
   * Why a row is refused. When a row has several problems the one reported is the first by reason
   * (missing, too long, invalid), then by the order of the columns.
   */
  private record Refusal(ImportRowReason reason, String column, Map<String, String> params)
      implements Comparable<Refusal> {

    @Override
    public int compareTo(Refusal other) {
      int byReason = reason.compareTo(other.reason);
      return byReason != 0 ? byReason : COLUMNS.indexOf(column) - COLUMNS.indexOf(other.column);
    }
  }

  private Map<String, String> notClearedParams(String type, int order) {
    OptionalInt highest = clearance.highestHeldOrder(type);
    return highest.isPresent()
        ? Map.of(
            PARAM_ORDER,
            String.valueOf(order),
            PARAM_HIGHEST_ORDER,
            String.valueOf(highest.getAsInt()))
        : Map.of();
  }

  private static ImportResultRow skipped(
      MarkingRow row, ImportRowReason reason, Map<String, String> params) {
    return new ImportResultRow(
        row.position(), ImportRowStatus.SKIPPED, reason, params, valuesOf(row));
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
