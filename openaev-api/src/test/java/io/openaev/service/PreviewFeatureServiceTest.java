package io.openaev.service;

import static io.openaev.rest.settings.PreviewFeature.FEATURE_FLAG_ALL;
import static io.openaev.rest.settings.PreviewFeature.MULTI_TENANCY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.OpenAEVConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreviewFeatureServiceTest extends IntegrationTest {

  @Autowired private PreviewFeatureService previewFeatureService;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    if (cacheManager.getCache("global") != null) {
      cacheManager.getCache("global").clear();
    }
  }

  @Test
  void should_enable_feature_when_feature_flag_all_is_enabled() {
    // ARRANGE
    openAEVConfig.setEnabledDevFeatures(FEATURE_FLAG_ALL.getValue());

    // ACT & ASSERT
    assertTrue(previewFeatureService.isFeatureEnabled(MULTI_TENANCY));
  }

  @Test
  void should_enable_feature_when_feature_is_explicitly_enabled() {
    // ARRANGE
    openAEVConfig.setEnabledDevFeatures(MULTI_TENANCY.getValue());

    // ACT & ASSERT
    assertTrue(previewFeatureService.isFeatureEnabled(MULTI_TENANCY));
  }

  @Test
  void should_return_false_when_feature_is_not_enabled() {
    // ARRANGE
    openAEVConfig.setEnabledDevFeatures("");

    // ACT & ASSERT
    assertFalse(previewFeatureService.isFeatureEnabled(MULTI_TENANCY));
  }
}
