package io.openaev.api.asset.software;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.rest.exception.BadRequestException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the software components of a CycloneDX or SPDX JSON SBOM, bundled components included.
 *
 * <p>CycloneDX: every entry of {@code components}, nested {@code components} giving the parent;
 * vendor from {@code group}, else {@code publisher}, else {@code supplier.name}. SPDX: every entry
 * of {@code packages}, CPE and PURL from {@code externalRefs}, vendor from {@code supplier}, a
 * {@code CONTAINS} relationship (or its inverse {@code CONTAINED_BY}) giving the parent. A version
 * that is missing, blank or {@code NOASSERTION} is returned as null, so the component is stored
 * with an unknown version rather than an empty one.
 *
 * <p>Pure: no database, no Spring, so each format is unit tested on a file.
 */
public final class SbomParser {

  /** Components read from one SBOM, at most: an SBOM larger than this is refused, not cut. */
  public static final int MAX_COMPONENTS = 10_000;

  private SbomParser() {}

  /** One component of the SBOM, with the local reference of the component that bundles it. */
  public record ParsedComponent(
      String ref,
      String parentRef,
      String vendor,
      String product,
      String version,
      String cpe,
      String purl) {}

  public static List<ParsedComponent> parse(JsonNode sbom) {
    if (sbom == null || !sbom.isObject()) {
      throw new BadRequestException("The SBOM must be a JSON object");
    }
    if ("CycloneDX".equalsIgnoreCase(text(sbom, "bomFormat"))) {
      return cycloneDx(sbom);
    }
    if (sbom.hasNonNull("spdxVersion")) {
      return spdx(sbom);
    }
    throw new BadRequestException(
        "Unsupported SBOM: expected CycloneDX JSON (bomFormat) or SPDX JSON (spdxVersion)");
  }

  // -- CYCLONEDX --

  private static List<ParsedComponent> cycloneDx(JsonNode sbom) {
    List<ParsedComponent> out = new ArrayList<>();
    int[] counter = {0};
    walkCycloneDx(sbom.path("components"), null, out, counter);
    return out;
  }

  private static void walkCycloneDx(
      JsonNode components, String parentRef, List<ParsedComponent> out, int[] counter) {
    if (!components.isArray()) {
      return;
    }
    for (JsonNode c : components) {
      String product = text(c, "name");
      if (product == null) {
        continue;
      }
      checkSize(out);
      String ref = text(c, "bom-ref");
      if (ref == null) {
        ref = "component-" + counter[0];
      }
      counter[0]++;
      String vendor = text(c, "group");
      if (vendor == null) {
        vendor = text(c, "publisher");
      }
      if (vendor == null) {
        vendor = text(c.path("supplier"), "name");
      }
      out.add(
          new ParsedComponent(
              ref,
              parentRef,
              vendor,
              product,
              version(text(c, "version")),
              text(c, "cpe"),
              text(c, "purl")));
      walkCycloneDx(c.path("components"), ref, out, counter);
    }
  }

  // -- SPDX --

  private static List<ParsedComponent> spdx(JsonNode sbom) {
    Map<String, String> parentOf = new HashMap<>();
    for (JsonNode r : sbom.path("relationships")) {
      String type = text(r, "relationshipType");
      String element = text(r, "spdxElementId");
      String related = text(r, "relatedSpdxElement");
      if (type == null || element == null || related == null) {
        continue;
      }
      switch (type.toUpperCase(Locale.ROOT)) {
        case "CONTAINS" -> parentOf.putIfAbsent(related, element);
        case "CONTAINED_BY" -> parentOf.putIfAbsent(element, related);
        default -> {
          // Other relationships (DEPENDS_ON, DESCRIBES...) are not a bundling.
        }
      }
    }
    List<ParsedComponent> out = new ArrayList<>();
    for (JsonNode p : sbom.path("packages")) {
      String product = text(p, "name");
      String ref = text(p, "SPDXID");
      if (product == null || ref == null) {
        continue;
      }
      checkSize(out);
      String cpe = null;
      String purl = null;
      for (JsonNode ext : p.path("externalRefs")) {
        String type = text(ext, "referenceType");
        String locator = text(ext, "referenceLocator");
        if (type == null || locator == null) {
          continue;
        }
        if (cpe == null && type.toLowerCase(Locale.ROOT).startsWith("cpe")) {
          cpe = locator;
        } else if (purl == null && "purl".equalsIgnoreCase(type)) {
          purl = locator;
        }
      }
      out.add(
          new ParsedComponent(
              ref,
              parentOf.get(ref),
              spdxSupplier(text(p, "supplier")),
              product,
              version(text(p, "versionInfo")),
              cpe,
              purl));
    }
    return out;
  }

  /** "Organization: F5, Inc." or "Person: Jane" to its name; NOASSERTION to null. */
  static String spdxSupplier(String supplier) {
    if (supplier == null || isNoAssertion(supplier)) {
      return null;
    }
    int colon = supplier.indexOf(':');
    String name = colon >= 0 ? supplier.substring(colon + 1).trim() : supplier.trim();
    return name.isEmpty() ? null : name;
  }

  // -- HELPERS --

  /** A version an SBOM gives, or null when it says nothing usable. */
  static String version(String raw) {
    if (raw == null || isNoAssertion(raw)) {
      return null;
    }
    return raw;
  }

  private static boolean isNoAssertion(String value) {
    String v = value.trim().toUpperCase(Locale.ROOT);
    return v.isEmpty() || "NOASSERTION".equals(v) || "NONE".equals(v) || "UNKNOWN".equals(v);
  }

  private static void checkSize(List<ParsedComponent> out) {
    if (out.size() >= MAX_COMPONENTS) {
      throw new BadRequestException(
          "The SBOM holds more than " + MAX_COMPONENTS + " components: split it per application");
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual()) {
      return null;
    }
    String text = value.asText().trim();
    return text.isEmpty() ? null : text;
  }
}
