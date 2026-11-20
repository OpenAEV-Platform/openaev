package io.openaev.api.import_report;

import io.openaev.api.import_report.dto.ImportReport;
import io.openaev.api.import_report.dto.ImportReportRow;
import io.openaev.service.import_report.ImportResult;

public final class ImportReportMapper {

  private ImportReportMapper() {}

  public static ImportReport toReport(ImportResult result) {
    return new ImportReport(
        result.created(),
        result.skipped(),
        result.ignored(),
        result.columns(),
        result.rows().stream()
            .map(
                row ->
                    new ImportReportRow(
                        row.position(), row.status(), row.reason(), row.params(), row.values()))
            .toList());
  }
}
