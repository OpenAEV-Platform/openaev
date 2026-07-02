package io.openaev.utilstest;

import static io.openaev.utils.ExpectationSignatureUtils.*;
import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.expectation.ExpectationSignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

  // -- mergeExpectationSignatures --

  @Nested
  @DisplayName("mergeExpectationSignatures")
  class MergeExpectationSignatures {

    @Test
    @DisplayName("given duplicated signatures should keep insertion order and remove duplicates")
    void given_duplicatedSignatures_should_keepInsertionOrderAndRemoveDuplicates() {
      // -- PREPARE --
      InjectExpectation expectation = new InjectExpectation();
      expectation.setId("expectation-id");
      InjectExpectationSignature signatureA =
          new InjectExpectationSignature(expectation, "type_a", "value_a", now());
      InjectExpectationSignature signatureB =
          new InjectExpectationSignature(expectation, "type_b", "value_b", now());
      InjectExpectationSignature signatureBDuplicate =
          new InjectExpectationSignature(expectation, "type_b", "value_b", now());
      InjectExpectationSignature signatureC =
          new InjectExpectationSignature(expectation, "type_c", "value_c", now());

      // -- EXECUTE --
      List<InjectExpectationSignature> result =
          mergeExpectationSignatures(
              List.of(signatureA, signatureB), List.of(signatureBDuplicate, signatureC));

      // -- ASSERT --
      assertEquals(List.of(signatureA, signatureB, signatureC), result);
    }

    @Test
    @DisplayName("given null lists should return empty list")
    void given_nullLists_should_returnEmptyList() {
      // -- EXECUTE --
      List<InjectExpectationSignature> result = mergeExpectationSignatures(null, null);

      // -- ASSERT --
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("given lists containing null should ignore null signatures")
    void given_listsContainingNull_should_ignoreNullSignatures() {
      // -- PREPARE --
      InjectExpectation expectation = new InjectExpectation();
      expectation.setId("expectation-id");
      InjectExpectationSignature signatureA =
          new InjectExpectationSignature(expectation, "type_a", "value_a", now());
      ArrayList<InjectExpectationSignature> secondSignatures = new ArrayList<>();
      secondSignatures.add(null);

      // -- EXECUTE --
      List<InjectExpectationSignature> result =
          mergeExpectationSignatures(List.of(signatureA), secondSignatures);

      // -- ASSERT --
      assertEquals(List.of(signatureA), result);
    }
  }
}
