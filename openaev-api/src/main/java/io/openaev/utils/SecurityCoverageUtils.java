package io.openaev.utils;

import static io.openaev.utils.constants.StixConstants.*;

import io.openaev.database.model.StixRefToExternalRef;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.ObjectBase;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.objects.constants.ExtendedProperties;
import io.openaev.stix.objects.constants.ObjectTypes;
import io.openaev.stix.types.Dictionary;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.coyote.BadRequestException;

/**
 * Utility class for processing security coverage data from STIX bundles.
 *
 * <p>Provides methods for extracting and validating security coverage objects from STIX 2.1
 * bundles, as well as mapping STIX identifiers to external references (e.g., MITRE ATT&CK IDs).
 *
 * <p>Security coverage objects represent the mapping between security controls and attack
 * techniques, used for evaluating defensive capabilities.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.stix.objects.Bundle
 * @see io.openaev.database.model.StixRefToExternalRef
 */
public class SecurityCoverageUtils {

  private static final String DOMAIN_NAME = "Domain-Name";

  private SecurityCoverageUtils() {}

  /**
   * Extracts and validates the {@code x-security-coverage} object from a STIX bundle.
   *
   * <p>This method ensures that the bundle contains exactly one object of type {@code
   * x-security-coverage}.
   *
   * @param bundle the STIX bundle to search
   * @return the extracted {@code x-security-coverage} object
   * @throws BadRequestException if the bundle does not contain exactly one such object
   */
  public static ObjectBase extractAndValidateCoverage(Bundle bundle) throws BadRequestException {
    List<ObjectBase> coverages = bundle.findByType(ObjectTypes.SECURITY_COVERAGE);
    if (coverages.size() != 1) {
      throw new BadRequestException("STIX bundle must contain exactly one security-coverage");
    }
    return coverages.getFirst();
  }

  /**
   * Extracts references from a list of STIX objects.
   *
   * <p>For each object that has a {@code x_mitre_id} property, this method creates a {@link
   * StixRefToExternalRef} mapping between the object's STIX ID and its MITRE external ID.
   *
   * @param objects the list of STIX objects to scan
   * @return a set of {@link StixRefToExternalRef} mappings between STIX and MITRE IDs
   */
  public static Set<StixRefToExternalRef> extractObjectReferences(List<ObjectBase> objects) {
    Set<StixRefToExternalRef> stixToRef = new HashSet<>();

    for (ObjectBase obj : objects) {
      String stixType = (String) obj.getProperty(STIX_TYPE).getValue();
      String refId = null;

      if (ObjectTypes.ATTACK_PATTERN.toString().equals(stixType)) {
        if (obj.hasExtension(ExtendedProperties.MITRE_EXTENSION_DEFINITION)) {
          Dictionary extensionObj =
              (Dictionary) obj.getExtension(ExtendedProperties.MITRE_EXTENSION_DEFINITION);
          if (extensionObj.has(CommonProperties.ID.toString())) {
            refId = (String) extensionObj.get(CommonProperties.ID.toString()).getValue();
          }
        }
      }

      boolean isIndicator = false;
      if (ObjectTypes.INDICATOR.toString().equals(stixType)) {
        if (obj.hasExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION)) {
          Dictionary extensionObj =
              (Dictionary) obj.getExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION);
          List<Dictionary> observables =
              obj.getExtensionObservables(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION);
          if (extensionObj.has(CommonProperties.ID.toString()) && hasDomainNameType(observables)) {
            refId = getDomainNameValue(observables);
          }
          isIndicator = true;
        }
      }

      if (obj.hasProperty(STIX_NAME) && StringUtils.isBlank(refId) && !isIndicator) {
        refId = (String) obj.getProperty(STIX_NAME).getValue();
      }

      if (!StringUtils.isBlank(refId)) {
        String stixId = (String) obj.getProperty(CommonProperties.ID).getValue();
        if (stixId != null) {
          stixToRef.add(new StixRefToExternalRef(stixId, refId));
        }
      }
    }

    return stixToRef;
  }

  /**
   * Extracts external reference IDs from a set of STIX-to-external mappings.
   *
   * <p>Returns only the external reference portion (e.g., MITRE ATT&CK IDs) from the mapping
   * objects, useful for lookups against external databases.
   *
   * @param objectRefs the set of STIX-to-external reference mappings
   * @return a set of external reference IDs
   */
  public static Set<String> getExternalIds(Set<StixRefToExternalRef> objectRefs) {
    return objectRefs.stream()
        .map(StixRefToExternalRef::getExternalRef)
        .collect(Collectors.toSet());
  }

  private static boolean hasDomainNameType(List<Dictionary> observables) {
    if (observables == null || observables.isEmpty()) {
      return false;
    }

    return observables.stream()
        .anyMatch(
            observable ->
                DOMAIN_NAME.equals(observable.get(CommonProperties.TYPE.toString()).getValue()));
  }

  private static String getDomainNameValue(List<Dictionary> observables) {
    if (!hasDomainNameType(observables)) {
      return null;
    }

    Dictionary domainName =
        observables.stream()
            .filter(
                observable ->
                    DOMAIN_NAME.equals(observable.get(CommonProperties.TYPE.toString()).getValue()))
            .findFirst()
            .orElse(null);
    return domainName != null
        ? (String) domainName.get(CommonProperties.VALUE.toString()).getValue()
        : null;
  }
}
