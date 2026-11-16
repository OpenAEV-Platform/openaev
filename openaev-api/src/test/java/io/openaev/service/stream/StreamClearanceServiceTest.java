package io.openaev.service.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.MarkedTable;
import io.openaev.config.MarkedTables;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.database.model.User;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamClearanceServiceTest {

  private static final String USER_ID = "user-id";
  private static final String TENANT_ID = "tenant-id";
  private static final MarkedTables ASSETS_MARKED =
      new MarkedTables(Map.of("assets", new MarkedTable("assets")));

  @Mock private MarkingClearanceCacheManager clearanceCache;
  @Mock private PreviewFeatureService previewFeatureService;

  private User user;

  @BeforeEach
  void setUp() {
    // Lenient: not every path reads them (feature off, no tenant).
    user = mock(User.class);
    lenient().when(user.getId()).thenReturn(USER_ID);
  }

  private StreamClearanceService service(MarkedTables markedTables) {
    return new StreamClearanceService(clearanceCache, markedTables, previewFeatureService);
  }

  private void markingFeatureEnabled(boolean enabled) {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(enabled);
  }

  @Nested
  @DisplayName("Without marking filtering on assets")
  class FeatureOff {

    @Test
    @DisplayName("with the MARKING feature disabled, every reader sees everything")
    void given_markingFeatureDisabled_should_seeEverything() {
      // Arrange
      markingFeatureEnabled(false);

      // Act
      MarkingCtx clearance = service(ASSETS_MARKED).clearanceFor(user, TENANT_ID);

      // Assert
      assertThat(clearance).isEqualTo(MarkingCtx.all());
      verify(clearanceCache, never()).findClearance(anyString(), anyString());
    }

    @Test
    @DisplayName("with assets not activated for marking, every reader sees everything")
    void given_assetsNotMarked_should_seeEverything() {
      // Arrange
      markingFeatureEnabled(true);

      // Act
      MarkingCtx clearance = service(MarkedTables.EMPTY).clearanceFor(user, TENANT_ID);

      // Assert
      assertThat(clearance).isEqualTo(MarkingCtx.all());
      verify(clearanceCache, never()).findClearance(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("With marking filtering on assets")
  class FeatureOn {

    @BeforeEach
    void enableMarking() {
      markingFeatureEnabled(true);
    }

    @Test
    @DisplayName("an admin or a BYPASS holder gets the clearance of their groups, like everyone")
    void given_adminOrBypass_should_getClearanceOfTheirGroups() {
      // Arrange: lenient, the flag must not be read at all
      lenient().when(user.isAdminOrBypass()).thenReturn(true);
      when(clearanceCache.findClearance(USER_ID, TENANT_ID))
          .thenReturn(MarkingCtx.forMarkings(List.of("tlp-clear")));

      // Act
      MarkingCtx clearance = service(ASSETS_MARKED).clearanceFor(user, TENANT_ID);

      // Assert
      assertThat(clearance).isEqualTo(MarkingCtx.forMarkings(List.of("tlp-clear")));
    }

    @Test
    @DisplayName("a reader gets their clearance in the tenant")
    void given_clearance_should_returnIt() {
      // Arrange
      when(clearanceCache.findClearance(USER_ID, TENANT_ID))
          .thenReturn(MarkingCtx.forMarkings(List.of("tlp-clear", "tlp-green")));

      // Act
      MarkingCtx clearance = service(ASSETS_MARKED).clearanceFor(user, TENANT_ID);

      // Assert
      assertThat(clearance).isEqualTo(MarkingCtx.forMarkings(List.of("tlp-clear", "tlp-green")));
    }

    @Test
    @DisplayName("a reader with no clearance holds no marking")
    void given_noClearance_should_holdNoMarking() {
      // Arrange
      when(clearanceCache.findClearance(USER_ID, TENANT_ID)).thenReturn(MarkingCtx.none());

      // Act
      MarkingCtx clearance = service(ASSETS_MARKED).clearanceFor(user, TENANT_ID);

      // Assert
      assertThat(clearance).isEqualTo(MarkingCtx.none());
    }

    @Test
    @DisplayName("with no tenant to read the clearance in, a reader holds no marking")
    void given_noTenant_should_holdNoMarking() {
      // Act
      MarkingCtx clearance = service(ASSETS_MARKED).clearanceFor(user, null);

      // Assert
      assertThat(clearance).isEqualTo(MarkingCtx.none());
      verify(clearanceCache, never()).findClearance(any(), any());
    }
  }
}
