package io.openaev.utils;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.openaev.service.utils.CustomColumnPositionStrategy;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * A {@link CustomColumnPositionStrategy} variant for CSV *writing* only.
 *
 * <p>opencsv's {@code ColumnPositionMappingStrategy} (which {@code CustomColumnPositionStrategy}
 * extends) always generates the header row from the raw Java field name (see {@code
 * FieldMapByPosition#generateHeader}), completely ignoring any {@code @CsvBindByName(column = ...)}
 * value. That's invisible for DTOs where the field name and the desired column label happen to
 * match (e.g. {@code InjectorContractExport}), but it silently produces the wrong header (raw
 * camelCase field names) for DTOs that use human-readable column labels, like the Attack Chaining
 * CSV exports.
 *
 * <p>This strategy fixes that by reading each bound field's {@link CsvBindByName#column()} value,
 * ordered by {@link CsvBindByPosition#position()}, and using that as the header row instead.
 */
public class PrettyHeaderColumnPositionStrategy<T> extends CustomColumnPositionStrategy<T> {

  @Override
  public String[] generateHeader(T bean) throws CsvRequiredFieldEmptyException {
    // Populate columnIndexForWriting/headerIndex as usual (needed for the actual field writes),
    // but discard opencsv's field-name-based header in favor of the @CsvBindByName column labels.
    super.generateHeader(bean);

    Field[] fields = getType().getDeclaredFields();
    String[] header = new String[fields.length];
    for (Field field : fields) {
      CsvBindByPosition positionAnnotation = field.getAnnotation(CsvBindByPosition.class);
      if (positionAnnotation == null) {
        continue;
      }
      int position = positionAnnotation.position();
      CsvBindByName nameAnnotation = field.getAnnotation(CsvBindByName.class);
      String label =
          nameAnnotation != null && !nameAnnotation.column().isBlank()
              ? nameAnnotation.column()
              : field.getName();
      if (position >= 0 && position < header.length) {
        header[position] = label;
      }
    }
    return Arrays.stream(header).map(h -> h == null ? "" : h).toArray(String[]::new);
  }
}
