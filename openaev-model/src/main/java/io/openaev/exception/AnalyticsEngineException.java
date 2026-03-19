package io.openaev.exception;

import lombok.Getter;

/**
 * Exception thrown when an error occurs in the analytics/search engine layer.
 *
 * <p>This exception is used to wrap errors from Elasticsearch or OpenSearch operations, including
 * index operations, search queries, and aggregations. It extends {@link RuntimeException} to allow
 * for unchecked exception handling.
 *
 * <p>Common scenarios:
 *
 * <ul>
 *   <li>Connection failures to the search engine
 *   <li>Query execution errors
 *   <li>Index creation or update failures
 *   <li>Bulk operation errors
 * </ul>
 *
 * @see io.openaev.service.ElasticService
 * @see io.openaev.service.OpenSearchService
 */
@Getter
public class AnalyticsEngineException extends RuntimeException {

  /**
   * Constructs a new analytics engine exception with the specified message.
   *
   * @param message the detail message describing the error
   */
  public AnalyticsEngineException(String message) {
    super(message);
  }

  /**
   * Constructs a new analytics engine exception with the specified message and cause.
   *
   * @param message the detail message describing the error
   * @param e the underlying exception that caused this error
   */
  public AnalyticsEngineException(String message, Exception e) {
    super(message, e);
  }
}
