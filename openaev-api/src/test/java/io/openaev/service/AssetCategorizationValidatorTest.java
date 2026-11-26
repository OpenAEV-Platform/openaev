package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.rest.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetCategorizationValidatorTest {

  @Test
  @DisplayName("a subcategory of the category, or none, is accepted")
  void given_consistentPair_should_pass() {
    assertDoesNotThrow(
        () ->
            AssetCategorizationValidator.validate(
                AssetCategory.WEB_APPLICATION, AssetSubCategory.WEBSITE));
    assertDoesNotThrow(() -> AssetCategorizationValidator.validate(AssetCategory.HOST, null));
    assertDoesNotThrow(() -> AssetCategorizationValidator.validate(null, null));
  }

  @Test
  @DisplayName("a subcategory of another category, or without category, is refused")
  void given_inconsistentPair_should_refuse() {
    assertThrows(
        BadRequestException.class,
        () ->
            AssetCategorizationValidator.validate(
                AssetCategory.WEB_APPLICATION, AssetSubCategory.SERVER));
    assertThrows(
        BadRequestException.class,
        () -> AssetCategorizationValidator.validate(null, AssetSubCategory.WEBSITE));
  }
}
