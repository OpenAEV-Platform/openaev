package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetTest {

  private static final Instant SENTINEL = Instant.EPOCH;

  private static Tag tag(String id) {
    Tag tag = new Tag();
    tag.setId(id);
    tag.setName(id);
    return tag;
  }

  private static Asset assetAtSentinel() {
    Asset asset = new Asset();
    asset.setTags(new HashSet<>(Set.of(tag("t1"), tag("t2"))));
    asset.setUpdatedAt(SENTINEL);
    return asset;
  }

  @Test
  @DisplayName(
      "setTags with the same tag ids does not bump updatedAt, so no-op collector upserts do not"
          + " force an UPDATE and an SSE restream (#6778)")
  void setTags_same_ids_does_not_bump() {
    Asset asset = assetAtSentinel();

    asset.setTags(new HashSet<>(Set.of(tag("t2"), tag("t1"))));

    assertThat(asset.getUpdatedAt()).isEqualTo(SENTINEL);
  }

  @Test
  @DisplayName("setTags with different tag ids bumps updatedAt")
  void setTags_different_ids_bumps() {
    Asset asset = assetAtSentinel();

    asset.setTags(new HashSet<>(Set.of(tag("t1"), tag("t3"))));

    assertThat(asset.getUpdatedAt()).isAfter(SENTINEL);
  }
}
