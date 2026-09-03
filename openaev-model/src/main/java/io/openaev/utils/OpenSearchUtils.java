package io.openaev.utils;

import io.openaev.database.model.Filters;
import io.openaev.engine.api.HistogramInterval;
import io.openaev.exception.InvalidDateRangeException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.ExtendedBounds;
import org.opensearch.client.opensearch._types.aggregations.FieldDateMath;
import org.opensearch.client.opensearch._types.query_dsl.*;

public class OpenSearchUtils {

  private OpenSearchUtils() {}

  /**
   * Get a query to check if field exists
   *
   * @param field the field
   * @return the resulting query
   */
  public static Query existsQuery(@NotBlank final String field) {
    return ExistsQuery.of(e -> e.field(field)).toQuery();
  }

  /**
   * Get a query to check if field does not exists
   *
   * @param field the field
   * @return the resulting query
   */
  public static Query notExistsQuery(@NotBlank final String field) {
    return BoolQuery.of(b -> b.mustNot(List.of(existsQuery(field)))).toQuery();
  }

  /**
   * Get a query to check if field is empty
   *
   * @param field the field
   * @return the resulting query
   */
  public static Query emptyFieldQuery(@NotBlank final String field) {
    return TermQuery.of(t -> t.field(field).value(FieldValue.of(""))).toQuery();
  }

  /**
   * Get a query to check if field is not empty
   *
   * @param field the field
   * @return the resulting query
   */
  public static Query notEmptyFieldQuery(@NotBlank final String field) {
    return BoolQuery.of(b -> b.mustNot(List.of(emptyFieldQuery(field)))).toQuery();
  }

  /**
   * Build a range query on a date field between a start and an end timestamp.
   *
   * @param field the date field name (must not be blank)
   * @param start the start instant (must not be null)
   * @param end the end instant (must not be null)
   */
  public static Query buildDateRangeQuery(
      @NotBlank final String field, @NotNull final Instant start, @NotNull final Instant end) {
    if (!start.isBefore(end)) {
      throw new InvalidDateRangeException("Start date must be before end date");
    }
    return RangeQuery.of(d -> d.field(field).gt(JsonData.of(start)).lt(JsonData.of(end))).toQuery();
  }

  /**
   * Build a single-bound comparison query (gt / gte / lt / lte) on a date field, as produced by the
   * filter UI for "instant" properties (e.g. a "created at &gt;= X" filter chip).
   *
   * @param field the date field name (must not be blank)
   * @param operator the comparison operator (must be gt, gte, lt or lte)
   * @param value the ISO-8601 date value (must not be blank)
   */
  public static Query buildDateCompareQuery(
      @NotBlank final String field,
      @NotNull final Filters.FilterOperator operator,
      @NotBlank final String value) {
    return RangeQuery.of(
            d -> {
              d.field(field);
              return switch (operator) {
                case gt -> d.gt(JsonData.of(value));
                case gte -> d.gte(JsonData.of(value));
                case lt -> d.lt(JsonData.of(value));
                case lte -> d.lte(JsonData.of(value));
                default ->
                    throw new UnsupportedOperationException(
                        "Not a comparison operator: " + operator);
              };
            })
        .toQuery();
  }

  /**
   * Builds the exclusive "resume after (ts, id)" keyset predicate used by cursor-paged search:
   * either strictly after {@code ts}, or equal to {@code ts} and strictly after {@code id}. The
   * equality branch is written as a double-bounded range (not a term) so it parses {@code ts}
   * through the same date path as the {@code gt} branch.
   *
   * @param tsField the date field to compare the timestamp on (must not be blank)
   * @param idField the sortable id field, e.g. {@code base_id.keyword} (must not be blank)
   * @param ts the resume timestamp, already truncated to milliseconds (must not be null)
   * @param id the resume id (must not be blank)
   */
  public static Query buildKeysetPredicate(
      @NotBlank final String tsField,
      @NotBlank final String idField,
      @NotNull final Instant ts,
      @NotBlank final String id) {
    Query strictlyAfterTs = RangeQuery.of(d -> d.field(tsField).gt(JsonData.of(ts))).toQuery();
    Query sameTsAfterId =
        BoolQuery.of(
                b ->
                    b.must(
                        RangeQuery.of(
                                d -> d.field(tsField).gte(JsonData.of(ts)).lte(JsonData.of(ts)))
                            .toQuery(),
                        RangeQuery.of(d -> d.field(idField).gt(JsonData.of(id))).toQuery()))
            .toQuery();
    return BoolQuery.of(b -> b.should(strictlyAfterTs, sameTsAfterId).minimumShouldMatch("1"))
        .toQuery();
  }

  /**
   * Builds a date histogram aggregation on the specified field.
   *
   * @param aggregation the {@link Aggregation.Builder} to configure (must not be null)
   * @param field the target field name for the histogram (must not be blank)
   * @param interval the histogram interval (calendar unit + format) (must not be null)
   * @return a {@link ContainerBuilder} representing the date histogram aggregation
   */
  public static ContainerBuilder buildDateHistogramAggregation(
      @NotNull final Aggregation.Builder aggregation,
      @NotBlank final String field,
      @NotNull final HistogramInterval interval,
      final ExtendedBounds<FieldDateMath> extendedBounds) {
    return aggregation.dateHistogram(
        h -> {
          DateHistogramAggregation.Builder builder =
              h.field(field)
                  .minDocCount(0)
                  .format(interval.format)
                  .calendarInterval(interval.openType)
                  .keyed(false);
          if (extendedBounds != null) {
            builder.extendedBounds(extendedBounds);
          }
          return builder;
        });
  }
}
