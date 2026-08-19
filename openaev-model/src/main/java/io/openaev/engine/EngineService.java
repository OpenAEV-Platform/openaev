package io.openaev.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CustomDashboardParameters;
import io.openaev.database.model.Filters;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.engine.api.*;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.EsSearch;
import io.openaev.engine.query.EsAvgs;
import io.openaev.engine.query.EsCountInterval;
import io.openaev.engine.query.EsEntities;
import io.openaev.engine.query.EsSeries;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface EngineService {

  List<String> BASE_FIELDS = List.of("base_id", "base_entity", "base_representative");

  /**
   * Upper bound on ids per engine call inside {@link #bulkDelete(List)}: keeps the terms clauses
   * and the side-cleanup script parameters bounded however large the deletion cascade is.
   */
  int BULK_DELETE_BATCH_SIZE = 1000;

  /** Upper bound on the page size of {@link #searchCursorPaged}, per FR28. */
  int CURSOR_PAGE_MAX_SIZE = 1000;

  /**
   * Process models in bulk
   *
   * @param models the models to insert
   * @param <T> the type of the models
   */
  <T extends EsBase> void bulkProcessing(Stream<EsModel<T>> models);

  /**
   * Clean up the index
   *
   * @param model the model to clean up
   * @throws IOException in case of issue communicating with the analytics engine
   */
  void cleanUpIndex(String model) throws IOException;

  /**
   * Deletes the documents for the given entity ids (and their cascade dependencies), then cleans
   * the deleted ids out of the denormalized {@code base_*_side} references. Ids are processed in
   * batches of {@link #BULK_DELETE_BATCH_SIZE}.
   *
   * <p>Failures propagate as {@link RuntimeException}: callers decide resilience. The after-commit
   * flush ({@code EngineListener}) swallows and relies on the deletion journal + replay job for
   * convergence; the replay job retries failed batches on its next pass.
   *
   * @param ids the list of ids to delete
   */
  void bulkDelete(List<String> ids);

  /**
   * Deletes every document belonging to the given tenants across all indexes, in a single
   * delete-by-query. Used when tenants are permanently purged: tenant data is removed via native
   * SQL (no JPA lifecycle events), so the engine must be cleaned explicitly by tenant id.
   *
   * @param tenantIds the tenants whose documents must be removed
   */
  void deleteByTenants(List<String> tenantIds);

  /**
   * Count using parameters
   *
   * @param user the user to use
   * @param runtime the count runtime to use
   * @return a count object, including the current and previous interval count and the difference
   *     between the two
   */
  EsCountInterval count(RawUserAuth user, CountRuntime runtime);

  /**
   * Calculates average using parameters
   *
   * @param user the user to use
   * @param averageRuntime the average runtime to use
   * @return an object label-average
   */
  EsAvgs average(RawUserAuth user, AverageRuntime averageRuntime);

  /**
   * Get the series in a Histogram model
   *
   * @param user the user to use
   * @param widgetConfig the config of the widget
   * @param config the config of the histogram series
   * @param parameters the parameters
   * @param definitionParameters the definition of the parameters
   * @return the resulting series
   */
  EsSeries termHistogram(
      RawUserAuth user,
      StructuralHistogramWidget widgetConfig,
      WidgetConfigurationWithSeries.Series config,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters);

  /**
   * Get a list of series in a Histogram model
   *
   * @param user the user to use
   * @param runtime the structural histogram runtime to use
   * @return a list of series
   */
  List<EsSeries> multiTermHistogram(RawUserAuth user, StructuralHistogramRuntime runtime);

  /**
   * Get the series in a date histogram model
   *
   * @param user the user to use
   * @param widgetConfig the config of the widget
   * @param config the config of the histogram series
   * @param parameters the parameters
   * @param definitionParameters the definition of the parameters
   * @return the resulting series
   */
  EsSeries dateHistogram(
      RawUserAuth user,
      DateHistogramWidget widgetConfig,
      WidgetConfigurationWithSeries.Series config,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters);

  /**
   * Get a list of series in a date histogram model
   *
   * @param user the user to use
   * @param runtime the structural histogram runtime to use
   * @return a list of series
   */
  List<EsSeries> multiDateHistogram(RawUserAuth user, DateHistogramRuntime runtime);

  /**
   * Get a list of entities
   *
   * @param user the user to use
   * @param runtime the list runtime to use
   * @return entities result containing data and total count
   */
  EsEntities entities(RawUserAuth user, ListRuntime runtime);

  /**
   * Reads one page of a single model index in {@code (base_updated_at, base_id)} order, resuming
   * from an arbitrary point in that order, without an offset.
   *
   * <p>The order is {@code (base_updated_at, base_id)} ascending on the engine-normalized values:
   * {@code base_updated_at} is a {@code date} field, compared at millisecond resolution, and {@code
   * base_id} is compared on its normalized {@code .keyword} subfield. Every {@link Instant} bound
   * carried by {@code query} is truncated to milliseconds by the implementation before the engine
   * query is built, so callers do not need to truncate themselves.
   *
   * <p>Results are restricted to the current tenant and to the grants of {@code user}; an admin
   * {@code user} bypasses the grant filter entirely.
   *
   * <p>There is no total: the page is exactly {@code query.size()} documents when more remain, by
   * design (see the story plan) — {@code has_more} is for the caller to derive.
   *
   * @param user the user to use; a non-admin user only sees documents its grants allow
   * @param model the model class to search; its handler bean must be registered
   * @param query the page bounds
   * @return the page of documents, in {@code (base_updated_at, base_id)} ascending order
   * @throws IllegalArgumentException if {@code query.size()} is out of {@code [1,
   *     CURSOR_PAGE_MAX_SIZE]}, or if {@code model} has no registered handler
   */
  <T extends EsBase> List<T> searchCursorPaged(
      RawUserAuth user, Class<T> model, CursorPageQuery query);

  /**
   * Create the list configuration using entities and filters
   *
   * @param entityName the name of the entity
   * @param filterValueMap the filters map
   * @return the ListConfiguration
   */
  ListConfiguration createListConfiguration(
      String entityName, Map<String, List<String>> filterValueMap);

  /**
   * Global search on ES
   *
   * @param user the user to use
   * @param search the search string
   * @param filter a list of filters
   * @return the list of results
   */
  List<EsSearch> search(RawUserAuth user, String search, Filters.FilterGroup filter);

  /**
   * Indexes a single document into the specified search engine index.
   *
   * <p>Used for real-time indexing of individual documents (e.g. audit log events) as opposed to
   * the periodic bulk-indexing pipeline ({@link #bulkProcessing}).
   *
   * @param index the full index name (including prefix, e.g. {@code openaev_audit-log})
   * @param id the document ID
   * @param document the document to index (must be serializable by the engine's JSON mapper)
   * @throws IOException if the indexing operation fails
   */
  default void indexDocument(String index, String id, Object document) throws IOException {
    throw new UnsupportedOperationException("indexDocument not supported by this engine");
  }

  /**
   * Get engine version of the engine
   *
   * @return the version of the engine
   */
  String getEngineVersion();

  /**
   * Returns the ObjectMapper used by the search engine client for document serialization. Other
   * components (e.g. audit log service) can reuse this to ensure consistent serialization between
   * the log appender and the search engine transport.
   *
   * @return the engine's ObjectMapper
   */
  ObjectMapper getObjectMapper();
}
