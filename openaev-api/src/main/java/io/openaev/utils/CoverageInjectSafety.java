package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.DnsResolution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;

/**
 * Decides whether an inject generated from a security coverage may be created enabled (#9875).
 *
 * <p>A coverage scenario is built without a person in the loop and can be launched by its own
 * recurrence, so an inject it generates is enabled only when both hold:
 *
 * <ul>
 *   <li>its contract is known to be read-only: a template whose metadata says {@code read_only:
 *       true} and {@code intrusive: false} (the Nuclei contracts), or a DNS resolution. Anything
 *       else (a payload that runs a command, drops or executes a file, an unverified payload, a
 *       contract with no safety metadata) is not known to be safe and stays disabled;
 *   <li>it has targets the coverage resolved itself (never the platform-wide groups of the tag
 *       rules).
 * </ul>
 *
 * Everything else is created disabled: the user enables it explicitly after checking it.
 */
public final class CoverageInjectSafety {

  /** Key under which an injector stores the template safety flags in the contract content. */
  public static final String TEMPLATE_METADATA = "template_metadata";

  public static final String READ_ONLY = "read_only";
  public static final String INTRUSIVE = "intrusive";

  private CoverageInjectSafety() {}

  /**
   * Whether the contract is known not to change state on its target.
   *
   * @param contract the injector contract of the generated inject
   * @return true only for a contract whose metadata says read-only and not intrusive, or a DNS
   *     resolution payload; false for a manual placeholder and for anything not known to be safe
   */
  public static boolean isReadOnly(InjectorContract contract) {
    if (contract == null || contract.getManualEffective()) {
      return false;
    }
    Payload payload = contract.getPayload();
    if (payload != null) {
      if (payload.getStatus() != null && payload.getStatus() != Payload.PAYLOAD_STATUS.VERIFIED) {
        return false;
      }
      // A DNS resolution only queries a name: it writes nothing on the host.
      return payload instanceof DnsResolution;
    }
    JsonNode metadata = templateMetadata(contract.getConvertedContent());
    if (metadata == null) {
      return false;
    }
    return metadata.path(READ_ONLY).asBoolean(false) && !metadata.path(INTRUSIVE).asBoolean(true);
  }

  /**
   * Sets the enabled flag of a generated inject: enabled only when its contract is read-only and it
   * carries targets the coverage resolved.
   */
  public static Inject applyEnabled(Inject inject, InjectorContract contract, boolean hasTargets) {
    inject.setEnabled(hasTargets && isReadOnly(contract));
    return inject;
  }

  private static JsonNode templateMetadata(ObjectNode content) {
    if (content == null) {
      return null;
    }
    JsonNode metadata = content.get(TEMPLATE_METADATA);
    if (metadata != null && metadata.isObject()) {
      return metadata;
    }
    // Flags written at the top level of the content.
    if (content.has(READ_ONLY) || content.has(INTRUSIVE)) {
      return content;
    }
    return null;
  }
}
