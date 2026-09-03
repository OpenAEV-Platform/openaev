package io.openaev.utils;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.ExtendedBounds;
import co.elastic.clients.elasticsearch._types.aggregations.FieldDateMath;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import io.openaev.database.model.Filters;
import io.openaev.engine.api.HistogramInterval;
import io.openaev.exception.InvalidDateRangeException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public class ElasticUtils {

  private ElasticUtils() {}

  public static Query existsQuery(@NotBlank final String field) {
    return ExistsQuery.of(e -> e.field(field))._toQuery();
  }

  public static Query notExistsQuery(@NotBlank final String field) {
    return BoolQuery.of(b -> b.mustNot(List.of(existsQuery(field))))._toQuery();
  }

  public static Query emptyFieldQuery(@NotBlank final String field) {
    return TermQuery.of(t -> t.field(field).value(""))._toQuery();
  }

  public static Query notEmptyFieldQuery(@NotBlank final String field) {
    return BoolQuery.of(b -> b.mustNot(List.of(emptyFieldQuery(field))))._toQuery();
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
    return DateRangeQuery.of(d -> d.field(field).gt(String.valueOf(start)).lt(String.valueOf(end)))
        ._toRangeQuery()
        ._toQuery();
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
    return DateRangeQuery.of(
            d -> {
              d.field(field);
              return switch (operator) {
                case gt -> d.gt(value);
                case gte -> d.gte(value);
                case lt -> d.lt(value);
                case lte -> d.lte(value);
                default ->
                    throw new UnsupportedOperationException(
                        "Not a comparison operator: " + operator);
              };
            })
        ._toRangeQuery()
        ._toQuery();
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
    Query strictlyAfterTs =
        DateRangeQuery.of(d -> d.field(tsField).gt(String.valueOf(ts)))._toRangeQuery()._toQuery();
    Query sameTsAfterId =
        BoolQuery.of(
                b ->
                    b.must(
                        DateRangeQuery.of(
                                d ->
                                    d.field(tsField)
                                        .gte(String.valueOf(ts))
                                        .lte(String.valueOf(ts)))
                            ._toRangeQuery()
                            ._toQuery(),
                        TermRangeQuery.of(t -> t.field(idField).gt(id))._toRangeQuery()._toQuery()))
            ._toQuery();
    return BoolQuery.of(b -> b.should(strictlyAfterTs, sameTsAfterId).minimumShouldMatch("1"))
        ._toQuery();
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
                  .calendarInterval(interval.esType)
                  .keyed(false);
          if (extendedBounds != null) {
            builder.extendedBounds(extendedBounds);
          }
          return builder;
        });
  }
}
