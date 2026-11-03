package io.openaev.finding.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.finding.identity.FindingLocation.AssetLocation;
import io.openaev.finding.identity.FindingLocation.UnknownLocation;
import io.openaev.finding.identity.FindingLocation.UserLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Finding location")
class FindingLocationTest {

  private static final String ASSET_ID = "4a8f2c1e-asset";
  private static final String USER_ID = "9b3d7e2f-user";

  @Nested
  @DisplayName("When building the location from its columns")
  class WhenBuildingTheLocationFromItsColumns {

    @Test
    @DisplayName("given an asset id only, should be an asset location")
    void given_anAssetIdOnly_should_beAnAssetLocation() {
      // -- ACT --
      FindingLocation location = FindingLocation.of(ASSET_ID, null);

      // -- ASSERT --
      assertThat(location).isEqualTo(new AssetLocation(ASSET_ID));
    }

    @Test
    @DisplayName("given a user id only, should be a user location")
    void given_aUserIdOnly_should_beAUserLocation() {
      // -- ACT --
      FindingLocation location = FindingLocation.of(null, USER_ID);

      // -- ASSERT --
      assertThat(location).isEqualTo(new UserLocation(USER_ID));
    }

    @Test
    @DisplayName("given no id, should be the unknown location")
    void given_noId_should_beTheUnknownLocation() {
      // -- ACT --
      FindingLocation location = FindingLocation.of(null, null);

      // -- ASSERT --
      assertThat(location).isSameAs(UnknownLocation.INSTANCE);
    }

    @Test
    @DisplayName("given both ids, should refuse a second location")
    void given_bothIds_should_refuseASecondLocation() {
      // -- ACT & ASSERT --
      assertThatThrownBy(() -> FindingLocation.of(ASSET_ID, USER_ID))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("given a blank id, should refuse it")
    void given_aBlankId_should_refuseIt() {
      // -- ACT & ASSERT --
      assertThatThrownBy(() -> new AssetLocation(" ")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new UserLocation("")).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("When computing the key")
  class WhenComputingTheKey {

    @Test
    @DisplayName("given each kind of location, should match the generated column format")
    void given_eachKindOfLocation_should_matchTheGeneratedColumnFormat() {
      // -- ARRANGE --
      // finding_location_key = COALESCE('asset:' || asset_id, 'user:' || user_id, 'unknown')

      // -- ACT & ASSERT --
      assertThat(FindingLocation.of(ASSET_ID, null).key()).isEqualTo("asset:" + ASSET_ID);
      assertThat(FindingLocation.of(null, USER_ID).key()).isEqualTo("user:" + USER_ID);
      assertThat(FindingLocation.of(null, null).key()).isEqualTo("unknown");
    }
  }
}
