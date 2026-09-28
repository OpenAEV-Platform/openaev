package io.openaev.api.asset.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of a marking write on an {@link Asset} (design step 3.3).
 *
 * <p>Deliberately narrow rather than the full {@code AssetOutput}: the endpoint's contract is "here
 * is the marking set on this asset now" (plus its name, for a readable confirmation), not a
 * general-purpose asset read — nothing else about the asset, lazy relations included, is part of
 * this response.
 */
@Getter
@Builder
@JsonInclude(NON_NULL)
public class AssetMarkingsOutput {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;

  @Schema(description = "Asset name")
  @JsonProperty("asset_name")
  @NotBlank
  private String name;

  @Schema(description = "Markings currently carried by the asset")
  @JsonProperty("asset_markings")
  private Set<String> markingIds;

  public static AssetMarkingsOutput from(Asset asset) {
    return AssetMarkingsOutput.builder()
        .id(asset.getId())
        .name(asset.getName())
        .markingIds(
            asset.getMarkingIds() == null
                ? Set.of()
                : Set.copyOf(Arrays.asList(asset.getMarkingIds())))
        .build();
  }
}
