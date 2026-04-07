package io.openaev.api.scenario.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.inject.output.InjectOutput;
import io.openaev.database.model.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportTestSummary {

  @JsonProperty("import_message")
  private List<ImportMessage> importMessage = new ArrayList<>();

  @JsonProperty("total_injects")
  public int totalNumberOfInjects;

  @JsonProperty("total_rows_analysed")
  public int totalRowsAnalysed;

  @JsonIgnore private List<Inject> injects = new ArrayList<>();

  @JsonProperty("injects")
  public List<InjectOutput> injectOutputs;
}
