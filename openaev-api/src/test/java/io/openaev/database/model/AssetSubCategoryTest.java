package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Guards the asset taxonomy mapping: every subcategory belongs to exactly one category, and the
 * {@link AssetCategory#SOURCE_CODE} category carries its five subcategories, readable on the wire
 * under their enum names.
 */
@DisplayName("Asset taxonomy: category to subcategory mapping")
class AssetSubCategoryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("Source code groups repositories, pipelines, registries, packages and other")
  void given_sourceCode_should_listItsFiveSubcategories() {
    assertThat(AssetSubCategory.forCategory(AssetCategory.SOURCE_CODE))
        .containsExactly(
            AssetSubCategory.CODE_REPOSITORY,
            AssetSubCategory.CI_CD_PIPELINE,
            AssetSubCategory.PACKAGE_REGISTRY,
            AssetSubCategory.SOFTWARE_PACKAGE,
            AssetSubCategory.CODE_OTHER);
  }

  @Test
  @DisplayName("Source code subcategories are refused under another category")
  void given_sourceCodeSubcategory_should_notBelongToAnotherCategory() {
    assertThat(
            AssetSubCategory.belongsTo(AssetSubCategory.CODE_REPOSITORY, AssetCategory.SOURCE_CODE))
        .isTrue();
    assertThat(
            AssetSubCategory.belongsTo(
                AssetSubCategory.CODE_REPOSITORY, AssetCategory.GENERIC_ASSET))
        .isFalse();
    assertThat(AssetSubCategory.belongsTo(AssetSubCategory.CLOUD_OTHER, AssetCategory.SOURCE_CODE))
        .isFalse();
  }

  @Test
  @DisplayName("Source code is not aligned with a preset attack domain yet")
  void given_sourceCode_should_stayToClassify() {
    assertThat(AssetCategory.SOURCE_CODE.getDomain()).isEqualTo("To classify");
  }

  @ParameterizedTest
  @EnumSource(AssetSubCategory.class)
  @DisplayName("Every subcategory belongs to its own category only")
  void given_anySubcategory_should_belongToExactlyOneCategory(AssetSubCategory subCategory) {
    long owners = 0;
    for (AssetCategory category : AssetCategory.values()) {
      if (AssetSubCategory.belongsTo(subCategory, category)) {
        owners++;
      }
    }
    assertThat(owners).isEqualTo(1);
    assertThat(AssetSubCategory.forCategory(subCategory.getCategory())).contains(subCategory);
  }

  @Test
  @DisplayName("The new values round-trip on the wire under their enum names")
  void given_sourceCodeTokens_should_deserialize() throws Exception {
    assertThat(MAPPER.readValue("\"SOURCE_CODE\"", AssetCategory.class))
        .isEqualTo(AssetCategory.SOURCE_CODE);
    assertThat(MAPPER.readValue("\"CI_CD_PIPELINE\"", AssetSubCategory.class))
        .isEqualTo(AssetSubCategory.CI_CD_PIPELINE);
    assertThat(MAPPER.writeValueAsString(AssetSubCategory.CODE_OTHER)).isEqualTo("\"CODE_OTHER\"");
  }
}
