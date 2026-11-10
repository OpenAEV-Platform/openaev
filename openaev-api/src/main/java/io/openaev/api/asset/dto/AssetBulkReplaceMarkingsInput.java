package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.asset.form.AssetBulkProcessingInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Bulk selection scope ({@link AssetBulkProcessingInput}) plus the markings to replace it with. */
@Getter
@Setter
public class AssetBulkReplaceMarkingsInput extends AssetBulkProcessingInput {

  @JsonProperty("marking_ids")
  @NotNull
  private List<String> markingIds;
}
