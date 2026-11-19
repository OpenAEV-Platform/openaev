package io.openaev.rest.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.Filters;
import io.openaev.engine.api.FlatConfiguration;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.api.WidgetConfigurationWithSeries;
import io.openaev.engine.model.finding.EsFinding;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.CustomDashboardTimeRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("base_entity validation of widget analytics")
class BaseEntityValidatorTest {

  private static final Set<String> VALID = Set.of("asset", "finding", "scenario");

  private static Filters.Filter filter(String key, String... values) {
    return Filters.Filter.getNewDefaultEqualFilter(key, new ArrayList<>(List.of(values)));
  }

  private static FlatConfiguration flat(Filters.Filter... filters) {
    WidgetConfigurationWithSeries.Series series = new WidgetConfigurationWithSeries.Series();
    series.setName("Series");
    series.setFilter(Filters.FilterGroup.filterGroupWithFilters(new ArrayList<>(List.of(filters))));
    FlatConfiguration config = new FlatConfiguration();
    config.setSeries(new ArrayList<>(List.of(series)));
    return config;
  }

  @Test
  @DisplayName(
      "Given a retired base_entity, it is rejected with its successor and the valid values")
  void given_retiredBaseEntity_should_rejectNamingTheSuccessor() {
    FlatConfiguration config = flat(filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "endpoint"));

    assertThatThrownBy(() -> BaseEntityValidator.validate(config, VALID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid base_entity value(s): endpoint")
        .hasMessageContaining("'endpoint' was retired and replaced by 'asset'")
        .hasMessageContaining("Valid values: asset, finding, scenario");
  }

  @Test
  @DisplayName("Given an unknown base_entity, it is rejected naming it")
  void given_unknownBaseEntity_should_reject() {
    FlatConfiguration config =
        flat(filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "asset", "no-such-model"));

    assertThatThrownBy(() -> BaseEntityValidator.validate(config, VALID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid base_entity value(s): no-such-model.")
        .hasMessageNotContaining("retired");
  }

  @Test
  @DisplayName("Given valid base_entity values, the configuration passes with its defaults")
  void given_validBaseEntity_should_pass() {
    FlatConfiguration config =
        flat(
            filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "asset"),
            filter("finding_type", "endpoint"));

    assertThatCode(() -> BaseEntityValidator.validate(config, VALID)).doesNotThrowAnyException();
    // time_range and date_attribute were not set: the defaults stay in place.
    assertThat(config.getTimeRange()).isEqualTo(CustomDashboardTimeRange.DEFAULT);
    assertThat(config.getDateAttribute()).isEqualTo("base_created_at");
  }

  @Test
  @DisplayName("Given a list perspective with an unknown base_entity, it is rejected")
  void given_listPerspectiveWithUnknownBaseEntity_should_reject() {
    ListConfiguration config = new ListConfiguration();
    ListConfiguration.ListPerspective perspective = new ListConfiguration.ListPerspective();
    perspective.setFilter(
        Filters.FilterGroup.filterGroupWithFilters(
            new ArrayList<>(
                List.of(filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "endpoint")))));
    config.setPerspective(perspective);

    assertThatThrownBy(() -> BaseEntityValidator.validate(config, VALID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("replaced by 'asset'");
  }

  @Test
  @DisplayName("Given no base_entity filter or a null configuration, nothing is rejected")
  void given_noBaseEntityFilter_should_pass() {
    assertThatCode(() -> BaseEntityValidator.validate(flat(), VALID)).doesNotThrowAnyException();
    assertThatCode(() -> BaseEntityValidator.validate(null, VALID)).doesNotThrowAnyException();
  }

  // -- Group-by field of a structural histogram --

  private static final Map<String, Set<String>> FIELDS =
      Map.of(
          "finding",
          Set.of("base_created_at", "finding_type", "base_endpoint_side", "base_asset_groups_side"),
          "asset",
          Set.of("base_created_at", "endpoint_platform"));

  private static StructuralHistogramWidget structural(String field, Filters.Filter... filters) {
    WidgetConfigurationWithSeries.Series series = new WidgetConfigurationWithSeries.Series();
    series.setName("Series");
    series.setFilter(Filters.FilterGroup.filterGroupWithFilters(new ArrayList<>(List.of(filters))));
    StructuralHistogramWidget config = new StructuralHistogramWidget();
    config.setField(field);
    config.setSeries(new ArrayList<>(List.of(series)));
    return config;
  }

  @Test
  @DisplayName(
      "Given a group-by on a SQL-only finding key, it is rejected naming the indexed equivalent")
  void given_sqlOnlyFindingKey_should_rejectNamingTheIndexedEquivalent() {
    StructuralHistogramWidget config =
        structural("finding_assets", filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "finding"));

    assertThatThrownBy(() -> BaseEntityValidator.validateGroupBy(config, FIELDS))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid group-by field 'finding_assets'")
        .hasMessageContaining("base_entity 'finding' does not index it")
        .hasMessageContaining("Use 'base_endpoint_side' instead")
        .hasMessageContaining(
            "Valid group-by fields for 'finding': base_asset_groups_side, base_created_at,"
                + " base_endpoint_side, finding_type");
  }

  @Test
  @DisplayName("Given a group-by on an unknown field, it is rejected with no equivalent")
  void given_unknownField_should_rejectWithoutEquivalent() {
    StructuralHistogramWidget config =
        structural("no_such_field", filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "asset"));

    assertThatThrownBy(() -> BaseEntityValidator.validateGroupBy(config, FIELDS))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid group-by field 'no_such_field'")
        .hasMessageNotContaining("instead");
  }

  @Test
  @DisplayName("Given a group-by on an indexed field, the configuration passes")
  void given_indexedField_should_pass() {
    assertThatCode(
            () ->
                BaseEntityValidator.validateGroupBy(
                    structural(
                        "base_asset_groups_side",
                        filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "finding")),
                    FIELDS))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                BaseEntityValidator.validateGroupBy(
                    structural(
                        "base_created_at",
                        filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "finding", "asset")),
                    FIELDS))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Given a field valid for one series entity only, it is rejected for the other")
  void given_fieldOfOneEntityOnly_should_rejectForTheOther() {
    StructuralHistogramWidget config =
        structural(
            "endpoint_platform",
            filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "asset", "finding"));

    assertThatThrownBy(() -> BaseEntityValidator.validateGroupBy(config, FIELDS))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("base_entity 'finding' does not index it")
        .hasMessageNotContaining("base_entity 'asset'");
  }

  @Test
  @DisplayName(
      "Given no base_entity filter or no structural histogram, the group-by is not checked")
  void given_noBaseEntityOrNotStructural_should_pass() {
    assertThatCode(() -> BaseEntityValidator.validateGroupBy(structural("anything_goes"), FIELDS))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                BaseEntityValidator.validateGroupBy(
                    flat(filter(BaseEntityValidator.BASE_ENTITY_FILTER_KEY, "finding")), FIELDS))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("The finding document offers its asset groups as a group-by field, not its SQL keys")
  void findingDocument_should_offerAssetGroupsSide() {
    Set<String> fields = BaseEntityValidator.queryableFields(EsFinding.class);

    assertThat(fields)
        .contains("base_asset_groups_side", "base_endpoint_side", "base_created_at", "finding_type")
        .doesNotContain("finding_assets", "finding_asset_groups");
  }
}
