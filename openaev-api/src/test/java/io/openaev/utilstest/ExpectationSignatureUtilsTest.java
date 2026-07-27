package io.openaev.utilstest;

import static io.openaev.utils.ExpectationSignatureUtils.*;
import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.BaseInjectExpectation;
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

  // -- convertToInjectExpectationSignatures --

  @Nested
  @DisplayName("convertToInjectExpectationSignatures")
  class ConvertToInjectExpectationSignatures {

    @Test
    @DisplayName("given duplicate (type, value) signatures should produce a single entity per pair")
    void given_duplicateSignatures_should_deduplicateByTypeAndValue() {
      // -- PREPARE --
      // Regression: duplicate (type, value) pairs on one expectation map to the same composite
      // primary key and used to make Hibernate throw NonUniqueObjectException on flush,
      // rolling back the whole expectation save (e.g. an endpoint whose seen IP is also one
      // of its declared IPs yields two identical source_ipv4_address signatures).
      BaseInjectExpectation expectation = new BaseInjectExpectation();
      expectation.setId("expectation-id");
      ExpectationSignature seenIp =
          new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS, "10.0.0.1");
      ExpectationSignature declaredIp =
          new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS, "10.0.0.1");
      ExpectationSignature hostname =
          new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_TARGET_HOSTNAME_ADDRESS, "host-a");

      // -- EXECUTE --
      List<InjectExpectationSignature> result =
          convertToInjectExpectationSignatures(List.of(seenIp, declaredIp, hostname), expectation);

      // -- ASSERT --
      assertEquals(2, result.size());
      assertEquals(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS, result.get(0).getType());
      assertEquals("10.0.0.1", result.get(0).getValue());
      assertEquals(EXPECTATION_SIGNATURE_TYPE_TARGET_HOSTNAME_ADDRESS, result.get(1).getType());
      assertEquals("host-a", result.get(1).getValue());
    }

    @Test
    @DisplayName("given same value under different types should keep both signatures")
    void given_sameValueDifferentTypes_should_keepBoth() {
      // -- PREPARE --
      BaseInjectExpectation expectation = new BaseInjectExpectation();
      expectation.setId("expectation-id");
      ExpectationSignature source =
          new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS, "10.0.0.1");
      ExpectationSignature target =
          new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_TARGET_IPV4_ADDRESS, "10.0.0.1");

      // -- EXECUTE --
      List<InjectExpectationSignature> result =
          convertToInjectExpectationSignatures(List.of(source, target), expectation);

      // -- ASSERT --
      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("given null entries should drop them")
    void given_nullEntries_should_dropThem() {
      // -- PREPARE --
      BaseInjectExpectation expectation = new BaseInjectExpectation();
      expectation.setId("expectation-id");
      ArrayList<ExpectationSignature> signatures = new ArrayList<>();
      signatures.add(null);
      signatures.add(
          new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS, "10.0.0.1"));

      // -- EXECUTE --
      List<InjectExpectationSignature> result =
          convertToInjectExpectationSignatures(signatures, expectation);

      // -- ASSERT --
      assertEquals(1, result.size());
      assertEquals("10.0.0.1", result.get(0).getValue());
    }

    @Test
    @DisplayName("given null or empty input should return an empty list")
    void given_nullOrEmptyInput_should_returnEmptyList() {
      // -- PREPARE --
      BaseInjectExpectation expectation = new BaseInjectExpectation();
      expectation.setId("expectation-id");

      // -- EXECUTE / ASSERT --
      assertTrue(convertToInjectExpectationSignatures(null, expectation).isEmpty());
      assertTrue(convertToInjectExpectationSignatures(List.of(), expectation).isEmpty());
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
      BaseInjectExpectation expectation = new BaseInjectExpectation();
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
      BaseInjectExpectation expectation = new BaseInjectExpectation();
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
