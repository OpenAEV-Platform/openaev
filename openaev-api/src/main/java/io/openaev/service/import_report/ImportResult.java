package io.openaev.service.import_report;

import java.util.List;

/**
 * What a partial, row by row import did. Knows nothing about the imported entity, so every import
 * of the platform can return it.
 *
 * @param columns the field names of a row, in display order
 */
public record ImportResult(
    int created, int skipped, int ignored, List<String> columns, List<ImportResultRow> rows) {

  public static ImportResult of(List<String> columns, List<ImportResultRow> rows) {
    return new ImportResult(
        count(rows, ImportRowStatus.CREATED),
        count(rows, ImportRowStatus.SKIPPED),
        count(rows, ImportRowStatus.IGNORED),
        columns,
        rows);
  }

  private static int count(List<ImportResultRow> rows, ImportRowStatus status) {
    return (int) rows.stream().filter(row -> row.status() == status).count();
  }
}
