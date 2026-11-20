package io.openaev.service.import_report;

import java.util.Map;

/**
 * The outcome of one data row of an imported file.
 *
 * @param position 1-based position of the data row, the first row under the header being 1
 * @param reason code owned by the importing feature, {@code null} unless the row is skipped
 * @param params the values the client puts in the sentence it writes for the reason, in its own
 *     language; empty unless the row is skipped, and for a reason that has none
 * @param values the fields of the row as read, keyed by field name
 */
public record ImportResultRow(
    int position,
    ImportRowStatus status,
    String reason,
    Map<String, String> params,
    Map<String, String> values) {}
