package io.openaev.utils;

import static io.openaev.utils.ExpectationSignatureUtils.createAiRequestMarkerSignature;
import static io.openaev.utils.ExpectationSignatureUtils.createAiTargetEndpointSignature;

import io.openaev.expectation.ExpectationSignature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Correlation helpers for AI adversarial validation.
 *
 * <p>Builds the deterministic per-inject canary marker and the AI expectation signatures ({@code
 * ai_request_marker}, {@code ai_target_endpoint}) used to correlate AI defense (LLM firewall /
 * guardrail) events back to a specific AI inject execution.
 *
 * <p>The marker algorithm is intentionally identical to the one used by the {@code ai-redteam}
 * injector and the AI defense collectors ({@code pyoaev.signatures.ai_marker}), so all three
 * compute the same value independently: {@code "oaev" + sha256("<injectId>:<agentId>")[:16 hex
 * chars]}.
 */
public final class AiSignatureUtils {

  private AiSignatureUtils() {}

  public static String computeMarker(String injectId, String agentId) {
    String seed = (injectId == null ? "" : injectId) + ":" + (agentId == null ? "" : agentId);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return "oaev" + hex.substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed to be available on every JVM.
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }

  /**
   * Builds the AI correlation signatures for an inject execution: the request marker (always) and
   * the target endpoint (when known). Collectors can also derive the marker from the inject id
   * independently, so these signatures are an explicit, first-class representation of the same
   * correlation key.
   */
  public static List<ExpectationSignature> computeAiSignatures(
      String injectId, String agentId, String targetEndpoint) {
    List<ExpectationSignature> signatures = new ArrayList<>();
    ExpectationSignature marker = createAiRequestMarkerSignature(computeMarker(injectId, agentId));
    if (marker != null) {
      signatures.add(marker);
    }
    ExpectationSignature endpoint = createAiTargetEndpointSignature(targetEndpoint);
    if (endpoint != null) {
      signatures.add(endpoint);
    }
    return signatures;
  }
}
