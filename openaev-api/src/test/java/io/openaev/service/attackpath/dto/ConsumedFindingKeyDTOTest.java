package io.openaev.service.attackpath.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.PrimitiveType;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The DTO claims to be safe by construction: a condition targeting secret material carries the
 * secret as its value, and only its masked form may leave the platform. That claim rests on {@code
 * rawValue} never being serialized, which is a property of the Jackson configuration as much as of
 * the annotation - a version bump or a differently configured mapper could silently break it.
 *
 * <p>These tests therefore serialize with the <b>application's</b> {@link ObjectMapper}, taken from
 * the Spring context rather than built on the spot, so what is proven is the real behaviour of the
 * API and not that of a default mapper.
 */
@WithMockUser(isAdmin = true)
class ConsumedFindingKeyDTOTest extends IntegrationTest {

  private static final String SECRET = "Sup3rS3cret";
  private static final String MASKED_SECRET = "Su******";

  @Autowired private ObjectMapper objectMapper;

  @Nested
  @DisplayName("When a key targets secret material")
  class WhenTheKeyTargetsSecretMaterial {

    @Test
    @DisplayName("given a password key, should never write the cleartext secret to JSON")
    void given_aPasswordKey_should_neverWriteTheCleartextSecretToJson() throws Exception {
      // -- ARRANGE --
      ConsumedFindingKeyDTO key =
          ConsumedFindingKeyDTO.of(PrimitiveType.Password, "EQ", SECRET, "SMB UP");

      // -- ACT --
      String json = objectMapper.writeValueAsString(key);

      // -- ASSERT --
      assertThat(json).doesNotContain(SECRET);
      assertThat(json).doesNotContain("rawValue").doesNotContain("raw_value");
      assertThat(objectMapper.readTree(json).get("value").asText()).isEqualTo(MASKED_SECRET);
    }

    @Test
    @DisplayName("given a password key, should keep the cleartext in Java for the matcher")
    void given_aPasswordKey_should_keepTheCleartextInJavaForTheMatcher() {
      // -- ARRANGE / ACT --
      ConsumedFindingKeyDTO key =
          ConsumedFindingKeyDTO.of(PrimitiveType.Password, "EQ", SECRET, null);

      // -- ASSERT --
      // AttackPathKeyMatcher compares rawValue against the cleartext finding value: masking it
      // would silently break causal-chain resolution.
      assertThat(key.rawValue()).isEqualTo(SECRET);
      assertThat(key.value()).isEqualTo(MASKED_SECRET);
    }

    @Test
    @DisplayName("given the label-based constructor, should mask just like the factory does")
    void given_theLabelBasedConstructor_should_maskJustLikeTheFactoryDoes() {
      // -- ARRANGE / ACT --
      ConsumedFindingKeyDTO key = new ConsumedFindingKeyDTO("password", "EQ", SECRET, null);

      // -- ASSERT --
      // The invariant has to hold for every ordinary way of building the record, otherwise a future
      // caller reintroduces the leak without any test turning red.
      assertThat(key.value()).isEqualTo(MASKED_SECRET);
      assertThat(key.rawValue()).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("given a copy carrying matched ids, should still hide the secret")
    void given_aCopyCarryingMatchedIds_should_stillHideTheSecret() throws Exception {
      // -- ARRANGE --
      ConsumedFindingKeyDTO key =
          ConsumedFindingKeyDTO.of(PrimitiveType.Password, "EQ", SECRET, null)
              .withMatchedFindingIds(List.of("NODE_FINDING_H|credentials|abc"));

      // -- ACT --
      String json = objectMapper.writeValueAsString(key);

      // -- ASSERT --
      assertThat(json).doesNotContain(SECRET);
      assertThat(key.rawValue()).isEqualTo(SECRET);
    }
  }

  @Nested
  @DisplayName("When a key targets ordinary material")
  class WhenTheKeyTargetsOrdinaryMaterial {

    @Test
    @DisplayName("given a port key, should hand the value out untouched")
    void given_aPortKey_should_handTheValueOutUntouched() throws Exception {
      // -- ARRANGE --
      ConsumedFindingKeyDTO key = new ConsumedFindingKeyDTO("port", "EQ", "445", null);

      // -- ACT --
      String json = objectMapper.writeValueAsString(key);

      // -- ASSERT --
      assertThat(objectMapper.readTree(json).get("value").asText()).isEqualTo("445");
      assertThat(key.rawValue()).isEqualTo("445");
    }

    @Test
    @DisplayName("given an unknown key type label, should not fail and should not mask")
    void given_anUnknownKeyTypeLabel_should_notFailAndShouldNotMask() {
      // -- ARRANGE / ACT --
      ConsumedFindingKeyDTO key = new ConsumedFindingKeyDTO("not_a_primitive", "EQ", "plain", null);

      // -- ASSERT --
      // An unmapped label must never break the construction of a whole graph payload.
      assertThat(key.value()).isEqualTo("plain");
      assertThat(key.rawValue()).isEqualTo("plain");
    }
  }
}
