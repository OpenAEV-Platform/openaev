package io.openaev.utils;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.ExtendedBounds;
import co.elastic.clients.elasticsearch._types.aggregations.FieldDateMath;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import io.openaev.engine.api.HistogramInterval;
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
    return DateRangeQuery.of(d -> d.field(field).gt(String.valueOf(start)).lt(String.valueOf(end)))
        ._toRangeQuery()
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
