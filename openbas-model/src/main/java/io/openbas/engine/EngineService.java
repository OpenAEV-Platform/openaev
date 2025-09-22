package io.openbas.engine;

import io.openbas.database.model.CustomDashboardParameters;
import io.openbas.database.model.Filters;
import io.openbas.database.raw.RawUserAuth;
import io.openbas.engine.api.*;
import io.openbas.engine.model.EsBase;
import io.openbas.engine.model.EsSearch;
import io.openbas.engine.query.EsSeries;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
   * @return a count
   */
  long count(RawUserAuth user, CountRuntime runtime);

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
      WidgetConfiguration.Series config,
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
      WidgetConfiguration.Series config,
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
   * @return a list of series
   */
  List<EsBase> entities(RawUserAuth user, ListRuntime runtime);

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
   * Get engine version of the engine
   *
   * @return the version of the engine
   */
  String getEngineVersion();
}
