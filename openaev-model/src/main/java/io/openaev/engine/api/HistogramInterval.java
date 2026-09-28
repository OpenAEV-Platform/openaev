package io.openaev.engine.api;

public enum HistogramInterval {
  year("YYYY"),
  month("YYYY-MM"),
  week("YYYY-MM-DD"),
  day("YYYY-MM-DD"),
  hour("YYYY-MM-DD HH:mm:ss"),
  quarter("YYYY-MM");

  public final String format;

  HistogramInterval(String format) {
    this.format = format;
  }
}
