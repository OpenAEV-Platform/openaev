package io.openaev.service;

import io.openaev.database.model.Inject;
import io.openaev.rest.scenario.response.ImportMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportRow {
  private InjectTime injectTime;
  private List<ImportMessage> importMessages = new ArrayList<>();
  private Inject inject;
}
