package io.openaev.engine;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;

public interface EngineService {

  List<String> BASE_FIELDS = List.of("base_id", "base_entity", "base_representative");

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
   * Bulk delete
   *
   * @param ids the list of ids to delete
   */
  void bulkDelete(List<String> ids);

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
   * Paginated search on a specific ES index.
   *
   * @param indexName the name of the index (without the prefix — it will be prepended
   *     automatically)
   * @param search the search string (nullable)
   * @param filter a filter group (nullable)
   * @param page zero-based page number
   * @param size page size (number of results per page)
   * @param clazz the class of the documents to deserialize
   * @param <T> the document type
   * @return a page of results
   */
  <T> Page<T> paginatedSearch(
      String indexName,
      String search,
      Filters.FilterGroup filter,
      int page,
      int size,
      Class<T> clazz);

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
}
