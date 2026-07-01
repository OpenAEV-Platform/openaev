package io.openaev.utilstest;

import io.openaev.expectation.ExpectationSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.openaev.utils.ExpectationSignatureUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ExpectationSignatureUtils")
class ExpectationSignatureUtilsTest {

  private static final String VALID_IPV6 = "2001:db8::1";
  private static final String INVALID_IP = "not-an-ip";

  // -- createIpSignature --

  @Nested
  @DisplayName("createIpSignature")
  class CreateIpSignature {

    @Test
    @DisplayName("given null IP should return null")
    void given_nullIp_should_returnNull() {
      // -- EXECUTE --
      ExpectationSignature result = createIpSignature(null, false);

      // -- ASSERT --
      assertNull(result);
    }

    @Test
    @DisplayName("given empty IP should return null")
    void given_emptyIp_should_returnNull() {
      // -- EXECUTE --
      ExpectationSignature result = createIpSignature("", false);

      // -- ASSERT --
      assertNull(result);
    }

    @Test
    @DisplayName("given valid IPv6 and isTarget=true should return TARGET_IPV6 signature")
    void given_validIpv6AndIsTargetTrue_should_returnTargetIpv6Signature() {
      // -- EXECUTE --
      ExpectationSignature result = createIpSignature(VALID_IPV6, true);

      // -- ASSERT --
      assertEquals(EXPECTATION_SIGNATURE_TYPE_TARGET_IPV6_ADDRESS, result.getType());
      assertEquals(VALID_IPV6, result.getValue());
    }

    @Test
    @DisplayName("given valid IPv6 and isTarget=false should return SOURCE_IPV6 signature")
    void given_validIpv6AndIsTargetFalse_should_returnSourceIpv6Signature() {
      // -- EXECUTE --
      ExpectationSignature result = createIpSignature(VALID_IPV6, false);

      // -- ASSERT --
      assertEquals(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV6_ADDRESS, result.getType());
      assertEquals(VALID_IPV6, result.getValue());
    }

    @Test
    @DisplayName("given IP that is neither IPv4 nor IPv6 should return null")
    void given_invalidIp_should_returnNull() {
      // -- EXECUTE --
      ExpectationSignature result = createIpSignature(INVALID_IP, false);

      // -- ASSERT --
      assertNull(result);
    }
  }

  // -- createAiRequestMarkerSignature --

  @Nested
  @DisplayName("createAiRequestMarkerSignature")
  class CreateAiRequestMarkerSignature {

    @Test
    @DisplayName("given null marker should return null")
    void given_nullMarker_should_returnNull() {
      // -- EXECUTE --
      ExpectationSignature result = createAiRequestMarkerSignature(null);

      // -- ASSERT --
      assertNull(result);
    }

    @Test
    @DisplayName("given empty marker should return null")
    void given_emptyMarker_should_returnNull() {
      // -- EXECUTE --
      ExpectationSignature result = createAiRequestMarkerSignature("");

      // -- ASSERT --
      assertNull(result);
    }

    @Test
    @DisplayName("given valid marker should return AI_REQUEST_MARKER signature")
    void given_validMarker_should_returnAiRequestMarkerSignature() {
      // -- PREPARE --
      String marker = "unique-marker-abc123";

      // -- EXECUTE --
      ExpectationSignature result = createAiRequestMarkerSignature(marker);

      // -- ASSERT --
      assertEquals(EXPECTATION_SIGNATURE_TYPE_AI_REQUEST_MARKER, result.getType());
      assertEquals(marker, result.getValue());
    }
  }

  // -- createAiTargetEndpointSignature --

  @Nested
  @DisplayName("createAiTargetEndpointSignature")
  class CreateAiTargetEndpointSignature {

    @Test
    @DisplayName("given null endpoint should return null")
    void given_nullEndpoint_should_returnNull() {
      // -- EXECUTE --
      ExpectationSignature result = createAiTargetEndpointSignature(null);

      // -- ASSERT --
      assertNull(result);
    }

    @Test
    @DisplayName("given empty endpoint should return null")
    void given_emptyEndpoint_should_returnNull() {
      // -- EXECUTE --
      ExpectationSignature result = createAiTargetEndpointSignature("");

      // -- ASSERT --
      assertNull(result);
    }

    @Test
    @DisplayName("given valid endpoint should return AI_TARGET_ENDPOINT signature")
    void given_validEndpoint_should_returnAiTargetEndpointSignature() {
      // -- PREPARE --
      String endpoint = "https://api.example.com/llm";

      // -- EXECUTE --
      ExpectationSignature result = createAiTargetEndpointSignature(endpoint);

      // -- ASSERT --
      assertEquals(EXPECTATION_SIGNATURE_TYPE_AI_TARGET_ENDPOINT, result.getType());
      assertEquals(endpoint, result.getValue());
    }
  }
}

