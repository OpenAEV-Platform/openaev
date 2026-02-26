package io.openaev.engine.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.engine.model.EsBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EsEntities {
  @Schema(description = "List of data from elasticSearch")
  @JsonProperty("es_datas")
  @NotNull
  List<EsBase> esDatas = new ArrayList<>();

  @Schema(description = "Total datas")
  @JsonProperty("total")
  @NotNull
  private long total;

  @Schema(description = "Total datas per pages")
  @JsonProperty("page_size")
  @NotNull
  private long pageSize;

  @Schema(description = "Current page number")
  @JsonProperty("page_number")
  @NotNull
  private long pageNumber;

  @Schema(description = "Current page number")
  @JsonProperty("total_pages")
  @NotNull
  private long totalPages;
}
