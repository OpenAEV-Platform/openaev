package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.Handler;
import io.openaev.engine.model.asset.EsAsset;
import io.openaev.engine.model.inject.EsInject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommonSearchServiceTest {

  @Mock private EngineContext engineContext;

  @InjectMocks private CommonSearchService commonSearchService;

  @Test
  @DisplayName(
      "Given indexed models, should collect base_*_side field names including superclasses")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void given_indexedModels_should_collectSideFieldNames() {
    when(engineContext.getModels())
        .thenReturn(
            (List)
                List.of(
                    new EsModel<>(EsInject.class, mock(Handler.class)),
                    new EsModel<>(EsAsset.class, mock(Handler.class))));

    Set<String> names = commonSearchService.getSideFieldNames();

    // Declared on EsInject itself
    assertThat(names)
        .contains("base_scenario_side", "base_simulation_side", "base_tags_side")
        // Inherited from EsTenantBase
        .contains("base_tenant_side")
        // Suffixed fields that are not pure "_side" must be excluded: the side-cleanup painless
        // script only rewrites keys ending exactly with "_side"
        .doesNotContain("base_platforms_side_denormalized");
    // Cached: a second call returns the same content without re-scanning
    assertThat(commonSearchService.getSideFieldNames()).isEqualTo(names);
  }
}
