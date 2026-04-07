package io.openaev.service;

import io.openaev.api.scenario.response.ImportMessage;
import io.openaev.database.model.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportRow {
  private InjectTime injectTime;
  private List<ImportMessage> importMessages = new ArrayList<>();
  private Inject inject;
}
