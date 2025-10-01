package io.openaev.rest.asset_group;

import static io.openaev.utils.JpaUtils.createJoinArrayAggOnId;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Filters.FilterGroup;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AssetGroupQueryHelper {

  private AssetGroupQueryHelper() {}

  // -- SELECT --

  public static void select(
      CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<AssetGroup> assetGroupRoot) {
    // Array aggregations
    Expression<String[]> assetIdsExpression = createJoinArrayAggOnId(cb, assetGroupRoot, "assets");
    Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, assetGroupRoot, "tags");

    // Multiselect
    cq.multiselect(
            assetGroupRoot.get("id").alias("asset_group_id"),
            assetGroupRoot.get("name").alias("asset_group_name"),
            assetGroupRoot.get("description").alias("asset_group_description"),
            assetGroupRoot
                .get("dynamicFilter")
                .as(String.class)
                .alias("asset_group_dynamic_filter"),
            assetIdsExpression.alias("asset_group_assets"),
            tagIdsExpression.alias("asset_group_tags"))
        .distinct(true);

    // Group by
    cq.groupBy(Collections.singletonList(assetGroupRoot.get("id")));
  }

  // -- EXECUTION --

  public static List<AssetGroupOutput> execution(TypedQuery<Tuple> query, ObjectMapper mapper) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              FilterGroup filterGroup;
              try {
                filterGroup =
                    mapper.readValue(
                        tuple.get("asset_group_dynamic_filter", String.class), FilterGroup.class);
              } catch (Exception e) {
                filterGroup = null;
              }
              return AssetGroupOutput.builder()
                  .id(tuple.get("asset_group_id", String.class))
                  .name(tuple.get("asset_group_name", String.class))
                  .description(tuple.get("asset_group_description", String.class))
                  .dynamicFilter(filterGroup)
                  .assets(
                      Arrays.stream(tuple.get("asset_group_assets", String[].class))
                          .collect(Collectors.toSet()))
                  .tags(
                      Arrays.stream(tuple.get("asset_group_tags", String[].class))
                          .collect(Collectors.toSet()))
                  .build();
            })
        .toList();
  }
}
