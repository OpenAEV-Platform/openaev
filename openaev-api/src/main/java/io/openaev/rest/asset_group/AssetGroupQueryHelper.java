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
    // asset_group_dynamic_filter is a json column, and json has no equality operator in
    // PostgreSQL, so it cannot appear in a GROUP BY as-is. The jsonb cast that the projection
    // already needs is groupable, so build it once and reuse it in both places.
    Expression<String> dynamicFilterAsJsonb =
        cb.function("to_jsonb", String.class, assetGroupRoot.get("dynamicFilter"));

    // Multiselect
    cq.multiselect(
            assetGroupRoot.get("id").alias("asset_group_id"),
            assetGroupRoot.get("name").alias("asset_group_name"),
            assetGroupRoot.get("description").alias("asset_group_description"),
            // FIXME : migrating to spring 3.5 upgraded hibernate from 6.4 to 6.6.
            // It now adds distinct query which cannot work with json fields so we have to cast it
            // as jsonb first
            // Correct fix would be to change field in the db to jsonb
            dynamicFilterAsJsonb.alias("asset_group_dynamic_filter"),
            assetIdsExpression.alias("asset_group_assets"),
            tagIdsExpression.alias("asset_group_tags"))
        .distinct(true);

    // Group by every non-aggregated column, not just the id.
    //
    // Grouping on the id alone relies on PostgreSQL's functional-dependency rule: selecting
    // ungrouped columns is legal when the GROUP BY covers the table's PRIMARY KEY. That rule
    // applies to BASE TABLES only. Once asset_groups is v2-active the tenant statement inspector
    // rewrites "FROM asset_groups ag" into "FROM (SELECT * FROM asset_groups ag WHERE
    // can_access_tenant(...)) AS ag", a DERIVED table, and PostgreSQL can no longer infer the
    // dependency: the query stops being valid SQL and the search endpoint returns 500.
    // Listing the columns explicitly is equivalent for the planner and does not depend on the
    // FROM item being a base table.
    cq.groupBy(
        List.of(
            assetGroupRoot.get("id"),
            assetGroupRoot.get("name"),
            assetGroupRoot.get("description"),
            dynamicFilterAsJsonb));
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
