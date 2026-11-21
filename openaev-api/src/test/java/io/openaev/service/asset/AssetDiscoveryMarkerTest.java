package io.openaev.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Tag;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.service.settings.TenantSettingsService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Asset discovery marker: the discovered tag, opt-in")
class AssetDiscoveryMarkerTest {

  private static final String TENANT = "tenant-a";

  private final TenantSettingsService settings = mock(TenantSettingsService.class);
  private final TagService tagService = mock(TagService.class);
  private final AssetDiscoveryMarker marker = new AssetDiscoveryMarker(settings, tagService);

  @Test
  @DisplayName("given the default setting should leave the input untouched and create no tag")
  void given_defaultSetting_should_leaveInputUntouched() {
    // Arrange
    when(settings.isAssetDiscoveryTagEnabled(TENANT)).thenReturn(false);
    EndpointInput input = new EndpointInput();
    input.setTagIds(new ArrayList<>(List.of("existing")));

    // Act
    marker.markDiscovered(input, TENANT);

    // Assert
    assertThat(input.getTagIds()).containsExactly("existing");
    verifyNoInteractions(tagService);
  }

  @Test
  @DisplayName("given the tenant opted in should add the discovered tag once, in the tenant")
  void given_optedIn_should_addDiscoveredTagOnce() {
    // Arrange
    when(settings.isAssetDiscoveryTagEnabled(TENANT)).thenReturn(true);
    Tag tag = new Tag();
    tag.setId("tag-discovered");
    when(tagService.upsertTag(any(TagCreateInput.class), eq(TENANT))).thenReturn(tag);
    EndpointInput input = new EndpointInput();
    input.setTagIds(new ArrayList<>(List.of("existing")));

    // Act: twice, the tag is not duplicated
    marker.markDiscovered(input, TENANT);
    marker.markDiscovered(input, TENANT);

    // Assert
    assertThat(input.getTagIds()).containsExactly("existing", "tag-discovered");
    ArgumentCaptor<TagCreateInput> captor = ArgumentCaptor.forClass(TagCreateInput.class);
    verify(tagService, org.mockito.Mockito.times(2)).upsertTag(captor.capture(), eq(TENANT));
    assertThat(captor.getValue().getName()).isEqualTo(AssetDiscoveryMarker.DISCOVERED_TAG_NAME);
  }
}
