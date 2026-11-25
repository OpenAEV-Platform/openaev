package io.openaev.api.import_report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.import_report.ImportRowReason;
import io.openaev.service.import_report.ImportRowStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ImportReportRow(
    @JsonProperty("import_row_position") @NotNull int position,
    @JsonProperty("import_row_status") @NotNull ImportRowStatus status,
    @JsonProperty("import_row_reason") ImportRowReason reason,
    @JsonProperty("import_row_params") @NotNull Map<String, String> params,
    @JsonProperty("import_row_values") @NotNull Map<String, String> values) {}
