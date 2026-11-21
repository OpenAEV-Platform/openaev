package io.openaev.service.asset;

import io.openaev.database.model.Tag;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.service.settings.TenantSettingsService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Marks the assets a run discovers (CTEM: first seen by discovery) with the existing tag mechanism:
 * an endpoint that an autonomous run promotes from a finding, or that an inject output creates,
 * carries the tag {@value #DISCOVERED_TAG_NAME}, so a filter, a dashboard or a live trigger on
 * asset creation tells it apart from an asset a person declared.
 *
 * <p>Opt-in: with the tenant setting {@code asset_discovery_tag} off (the default) the input is
 * left untouched and no tag is created, so the behaviour is the one before this change.
 */
@Service
@RequiredArgsConstructor
public class AssetDiscoveryMarker {

  /** Name of the tag put on an asset created by discovery. */
  public static final String DISCOVERED_TAG_NAME = "discovered";

  static final String DISCOVERED_TAG_COLOR = "#7a4dff";

  private final TenantSettingsService tenantSettingsService;
  private final TagService tagService;

  /**
   * Adds the discovery tag to an endpoint about to be created by discovery, when the tenant opted
   * in. Returns the input for chaining.
   */
  public EndpointInput markDiscovered(EndpointInput input, String tenantId) {
    if (input == null || !tenantSettingsService.isAssetDiscoveryTagEnabled(tenantId)) {
      return input;
    }
    TagCreateInput tagInput = new TagCreateInput();
    tagInput.setName(DISCOVERED_TAG_NAME);
    tagInput.setColor(DISCOVERED_TAG_COLOR);
    Tag tag = tagService.upsertTag(tagInput, tenantId);
    List<String> tagIds =
        input.getTagIds() == null ? new ArrayList<>() : new ArrayList<>(input.getTagIds());
    if (!tagIds.contains(tag.getId())) {
      tagIds.add(tag.getId());
    }
    input.setTagIds(tagIds);
    return input;
  }
}
