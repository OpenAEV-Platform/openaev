package io.openaev.service.catalog.event;

import io.openaev.database.model.User;
import io.openaev.database.model.catalog.CatalogContent;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.model.catalog.ContentType;

/** A version of content installed, uninstalled or saved in a tenant, by a user or by itself. */
public record ContentChanged(
    String tenantId,
    ContentType type,
    String repository,
    String externalId,
    String contentId,
    String version,
    ContentChange.Action action,
    ContentChange.Mode mode,
    String userId) {

  public static ContentChanged of(
      CatalogContent content, ContentChange.Action action, ContentChange.Mode mode, User user) {
    return new ContentChanged(
        content.getTenant().getId(),
        content.getCatalogType(),
        content.getCatalogRepository(),
        content.getCatalogExternalId(),
        content.getCatalogContentId(),
        content.getCatalogVersion(),
        action,
        mode,
        user == null ? null : user.getId());
  }
}
