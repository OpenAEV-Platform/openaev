package io.openaev.datapack.packs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.DataPackService;
import io.openaev.service.TagRuleService;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.TagRuleFixture;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Tags and Tag rules process tests")
@Transactional
public class TagRulesPackTest extends IntegrationTest {
  @Autowired private DataPackService dataPackService;
  @Autowired private TagService tagService;
  @Autowired private TagRuleService tagRuleService;
  @Autowired private AssetGroupService assetGroupService;

  @Autowired private TagRepository tagRepository;

  private Optional<TagRule> getExpectedTagRulePerPlatform(Endpoint.PLATFORM_TYPE platformType) {
    return switch (platformType) {
      case Linux -> tagRuleService.findByTagName(Tag.SECURITY_COVERAGE_LINUX_TAG_NAME);
      case Windows -> tagRuleService.findByTagName(Tag.SECURITY_COVERAGE_WINDOWS_TAG_NAME);
      case MacOS -> tagRuleService.findByTagName(Tag.SECURITY_COVERAGE_MACOS_TAG_NAME);
      default -> throw new IllegalArgumentException();
    };
  }

  @Test
  @DisplayName("Processing pack inserts expected data")
  public void processingPackInsertsExpectedData() {
    V20260107_Tags_and_tagrules_and_assetgroups datapack =
        new V20260107_Tags_and_tagrules_and_assetgroups(
            dataPackService, tagService, tagRuleService, assetGroupService);

    // act
    datapack.process();

    // assert

    // all necessary tags
    assertThat(tagRepository.findAll())
        .containsExactlyElementsOf(
            Tag.WellKnown.entrySet().stream()
                .map(entry -> TagFixture.getTagWithTextAndColour(entry.getKey(), entry.getValue()))
                .toList());

    // all necessary tag rules
    assertThat(tagRuleService.findAll())
        .usingElementComparator(Comparator.comparing(left -> left.getTag().getName()))
        .containsExactlyElementsOf(
            TagRule.RESERVED_TAG_NAMES.stream()
                .map(
                    tagName -> {
                      TagRule tr = TagRuleFixture.createDefaultTagRule();
                      tr.setTag(TagFixture.getTagWithText(tagName));
                      return tr;
                    })
                .toList());

    // all necessary asset groups exist
    List<AssetGroup> assetGroups = assetGroupService.assetGroups();
    for (Endpoint.PLATFORM_TYPE platformType :
        List.of(
            Endpoint.PLATFORM_TYPE.Linux,
            Endpoint.PLATFORM_TYPE.Windows,
            Endpoint.PLATFORM_TYPE.MacOS)) {
      for (Endpoint.PLATFORM_ARCH arch :
          List.of(Endpoint.PLATFORM_ARCH.x86_64, Endpoint.PLATFORM_ARCH.arm64)) {
        assertThat(assetGroups)
            // only one single group in all groups will validate the following
            .satisfiesOnlyOnce(
                assetGroup ->
                    assertThat(assetGroup)
                        // check that the asset group was assigned to the correct tag rule
                        .satisfies(
                            ag ->
                                assertThat(
                                        getExpectedTagRulePerPlatform(platformType)
                                            .get()
                                            .getAssetGroups())
                                    .contains(ag))
                        .satisfies(
                            ag ->
                                assertThat(ag.getName())
                                    .isEqualTo(
                                        "All %s %s"
                                            .formatted(platformType.toString(), arch.toString())))
                        .satisfies(ag -> assertThat(assetGroup.getDynamicFilter()).isNotNull())
                        .satisfies(
                            ag ->
                                assertThat(assetGroup.getDynamicFilter().getFilters()).isNotEmpty())
                        .satisfies(
                            ag ->
                                assertThat(assetGroup.getDynamicFilter().getFilters())
                                    // only one single filter in the group will validate the
                                    // following
                                    .satisfiesOnlyOnce(
                                        filter ->
                                            assertThat(filter)
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getKey())
                                                            .isEqualTo("endpoint_platform"))
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getMode())
                                                            .isEqualTo(Filters.FilterMode.or))
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getOperator())
                                                            .isEqualTo(Filters.FilterOperator.eq))
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getValues())
                                                            .containsExactly(
                                                                platformType.toString()))))
                        .satisfies(
                            ag ->
                                assertThat(assetGroup.getDynamicFilter().getFilters())
                                    .satisfiesOnlyOnce(
                                        filter ->
                                            assertThat(filter)
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getKey())
                                                            .isEqualTo("endpoint_arch"))
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getMode())
                                                            .isEqualTo(Filters.FilterMode.or))
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getOperator())
                                                            .isEqualTo(Filters.FilterOperator.eq))
                                                .satisfies(
                                                    f ->
                                                        assertThat(f.getValues())
                                                            .containsExactly(arch.toString())))));
      }
    }
  }
}
