package io.openaev.service;

import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.rest.exception.BadRequestException;

/**
 * Enforces the {@code category -> subcategories} taxonomy of {@link AssetSubCategory} on every
 * user-facing asset write (single create / update, bulk update), so an asset can never carry a
 * subcategory of another category - the frontend mirrors the same mapping, but the API is the
 * boundary.
 */
public final class AssetCategorizationValidator {

  private AssetCategorizationValidator() {}

  /**
   * Refuses a subcategory that does not belong to the category it will sit under. A null
   * subcategory is always valid (unclassified within its category).
   *
   * @param category the category the asset will have after the write
   * @param subcategory the subcategory the asset will have after the write
   */
  public static void validate(final AssetCategory category, final AssetSubCategory subcategory) {
    if (subcategory == null) {
      return;
    }
    if (category == null || !AssetSubCategory.belongsTo(subcategory, category)) {
      throw new BadRequestException(
          "Subcategory "
              + subcategory.name()
              + " does not belong to category "
              + (category == null ? "(none)" : category.name()));
    }
  }
}
