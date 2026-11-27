package io.openaev.service;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.fixtures.AssetGroupFixture.createDefaultAssetGroup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.repository.AssetGroupRepository;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class AssetGroupServiceTest extends IntegrationTest {

  @Autowired private AssetGroupService assetGroupService;
  @Autowired private AssetGroupRepository assetGroupRepository;

  @Nested
  @DisplayName("Syncing an asset group from an external source")
  class SyncExternalAssetGroup {

    @Test
    void given_knownReference_should_updateTheGroupAndKeepItsDescription() {
      // Arrange
      AssetGroup existing = createDefaultAssetGroup("Old name");
      existing.setExternalReference("edr-group");
      existing = assetGroupRepository.save(existing);
      String existingId = existing.getId();
      String userDescription = existing.getDescription();

      // Act
      AssetGroup synced =
          assetGroupService.syncExternalAssetGroup(
              DEFAULT_TENANT_UUID, "edr-group", "New name", null, new ArrayList<>());

      // Assert
      assertThat(synced.getId()).isEqualTo(existingId);
      assertThat(synced.getName()).isEqualTo("New name");
      assertThat(synced.getDescription()).isEqualTo(userDescription);
    }

    @Test
    void given_unknownReference_should_createTheGroupInTheTenant() {
      // Act
      AssetGroup synced =
          assetGroupService.syncExternalAssetGroup(
              DEFAULT_TENANT_UUID, "new-edr-group", "EDR group", "From the EDR", new ArrayList<>());

      // Assert
      assertThat(synced.getId()).isNotNull();
      assertThat(synced.getExternalReference()).isEqualTo("new-edr-group");
      assertThat(synced.getDescription()).isEqualTo("From the EDR");
      assertThat(synced.getTenant().getId()).isEqualTo(DEFAULT_TENANT_UUID);
    }
  }
}
