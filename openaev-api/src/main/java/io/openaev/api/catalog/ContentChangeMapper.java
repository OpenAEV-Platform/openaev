package io.openaev.api.catalog;

import io.openaev.api.catalog.dto.ContentChangeOutput;
import io.openaev.database.model.User;
import io.openaev.database.model.catalog.ContentChange;
import java.util.Optional;

public class ContentChangeMapper {

  private ContentChangeMapper() {}

  public static ContentChangeOutput toOutput(ContentChange history) {
    return new ContentChangeOutput(
        history.getId(),
        history.getAction(),
        history.getMode(),
        history.getVersion(),
        Optional.ofNullable(history.getUser()).map(User::getNameOrEmail).orElse(null),
        history.getCreatedAt());
  }
}
