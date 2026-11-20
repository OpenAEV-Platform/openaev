package io.openaev.api.catalog;

import io.openaev.api.catalog.dto.CatalogHistoryOutput;
import io.openaev.database.model.User;
import io.openaev.database.model.catalog.CatalogHistory;
import java.util.Optional;

public class CatalogHistoryMapper {

  private CatalogHistoryMapper() {}

  public static CatalogHistoryOutput toOutput(CatalogHistory history) {
    return new CatalogHistoryOutput(
        history.getId(),
        history.getAction(),
        history.getMode(),
        history.getVersion(),
        Optional.ofNullable(history.getUser()).map(User::getNameOrEmail).orElse(null),
        history.getCreatedAt());
  }
}
