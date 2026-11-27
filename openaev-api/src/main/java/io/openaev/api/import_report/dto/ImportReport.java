package io.openaev.api.import_report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ImportReport(
    @JsonProperty("import_created") @NotNull int created,
    @JsonProperty("import_skipped") @NotNull int skipped,
    @JsonProperty("import_ignored") @NotNull int ignored,
    @JsonProperty("import_columns") @NotNull List<String> columns,
    @JsonProperty("import_rows") @NotNull List<ImportReportRow> rows) {}
