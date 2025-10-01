package io.openaev.utils;

import io.openaev.engine.api.HistogramInterval;
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
    return RangeQuery.of(d -> d.field(field).gt(JsonData.of(start)).lt(JsonData.of(end))).toQuery();
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
