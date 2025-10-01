package io.openaev.engine.api;

import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;

public enum HistogramInterval {
  year(
      CalendarInterval.Year,
      org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Year,
      "YYYY"),
  month(
      CalendarInterval.Month,
      org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Month,
      "YYYY-MM"),
  week(
      CalendarInterval.Week,
      org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Week,
      "YYYY-MM-DD"),
  day(
      CalendarInterval.Day,
      org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Day,
      "YYYY-MM-DD"),
  hour(
      CalendarInterval.Hour,
      org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Hour,
      "YYYY-MM-DD HH:mm:ss"),
  quarter(
      CalendarInterval.Quarter,
      org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Quarter,
      "YYYY-MM");

  public final CalendarInterval esType;
  public final org.opensearch.client.opensearch._types.aggregations.CalendarInterval openType;
  public final String format;

  HistogramInterval(
      CalendarInterval esType,
      org.opensearch.client.opensearch._types.aggregations.CalendarInterval openType,
      String format) {
    this.esType = esType;
    this.openType = openType;
    this.format = format;
  }
}
