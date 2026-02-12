package io.openaev.engine.query;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EsDomainsAvgData {

  @NotBlank private String label;

  @NotBlank private List<EsSeries> data;
}
