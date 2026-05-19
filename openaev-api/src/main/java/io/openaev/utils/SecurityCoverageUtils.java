package io.openaev.utils;

import static io.openaev.utils.constants.StixConstants.*;

import io.openaev.database.model.Document;
import io.openaev.database.model.StixRefToExternalRef;
import io.openaev.opencti.service.OpenCTIService;
import io.openaev.service.stix.error.BundleValidationError;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.ObjectBase;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.objects.constants.ExtendedProperties;
import io.openaev.stix.objects.constants.ObjectTypes;
import io.openaev.stix.types.Dictionary;
import io.openaev.utils.constants.StixConstants;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for processing security coverage data from STIX bundles.
 *
 * <p>Provides methods for extracting and validating security coverage objects from STIX 2.1
 * bundles, as well as mapping STIX identifiers to external references (e.g., MITRE ATT&CK IDs).
 *
 * <p>Security coverage objects represent the mapping between security controls and attack
 * techniques, used for evaluating defensive capabilities.
 *
 * @see io.openaev.stix.objects.Bundle
 * @see io.openaev.database.model.StixRefToExternalRef
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityCoverageUtils {

  private static final String DOMAIN_NAME = "Domain-Name";
  private final OpenCTIService openCtiService;

  /**
   * Extracts and validates the {@code x-security-coverage} object from a STIX bundle.
   *
   * <p>This method ensures that the bundle contains exactly one object of type {@code
   * x-security-coverage}.
   *
   * @param bundle the STIX bundle to search
   * @return the extracted {@code x-security-coverage} object
   * @throws BundleValidationError if the bundle does not contain exactly one such object
   */
  public ObjectBase extractAndValidateCoverage(Bundle bundle) throws BundleValidationError {
    List<ObjectBase> coverages = bundle.findByType(ObjectTypes.SECURITY_COVERAGE);
    if (coverages.size() != 1) {
      throw new BundleValidationError("STIX bundle must contain exactly one security-coverage");
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
  public Set<StixRefToExternalRef> extractObjectReferences(List<ObjectBase> objects) {
    Set<StixRefToExternalRef> stixToRef = new HashSet<>();

    for (ObjectBase obj : objects) {
      String stixType = (String) obj.getProperty(STIX_TYPE).getValue();

      if (ObjectTypes.ATTACK_PATTERN.toString().equals(stixType)) {
        if (obj.hasExtension(ExtendedProperties.MITRE_EXTENSION_DEFINITION)) {
          Dictionary extensionObj =
              (Dictionary) obj.getExtension(ExtendedProperties.MITRE_EXTENSION_DEFINITION);
          if (extensionObj.has(CommonProperties.ID.toString())) {
            manageAndAddStixRefToExternalRefs(
                stixToRef,
                obj,
                new ArrayList<>(
                    Collections.singleton(
                        (String) extensionObj.get(CommonProperties.ID.toString()).getValue())));
            continue;
          }
        }
      }

      if (ObjectTypes.INDICATOR.toString().equals(stixType)
          && obj.hasExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION)) {
        Dictionary extensionObj =
            (Dictionary) obj.getExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION);
        List<Dictionary> observables =
            obj.getExtensionObservables(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION);
        if (extensionObj.has(CommonProperties.ID.toString()) && hasDomainNameType(observables)) {
          manageAndAddStixRefToExternalRefs(
              stixToRef,
              obj,
              new ArrayList<>(Collections.singleton(getDomainNameValue(observables))));
        }
        continue;
      }

      if (ObjectTypes.ARTIFACT.toString().equals(stixType)
          && obj.hasExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION)) {
        Dictionary extensionObj =
            (Dictionary) obj.getExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION);
        Object filesValue = extensionObj.get(StixConstants.FILES);
        if (extensionObj.has(StixConstants.FILES)
            && filesValue instanceof io.openaev.stix.types.List<?> filesList) {
          List<String> documentIds =
              getAllDocumentIdsFromFiles((io.openaev.stix.types.List<Dictionary>) filesList);
          manageAndAddStixRefToExternalRefs(stixToRef, obj, documentIds);
        }
        continue;
      }

      manageAndAddStixRefToExternalRefs(stixToRef, obj, null);
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
  public Set<String> getExternalIds(Set<StixRefToExternalRef> objectRefs) {
    return objectRefs.stream()
        .flatMap(ref -> ref.getExternalRefs().stream())
        .collect(Collectors.toSet());
  }

  private void manageAndAddStixRefToExternalRefs(
      Set<StixRefToExternalRef> stixToRef, ObjectBase obj, List<String> refIds) {
    if (obj.hasProperty(STIX_NAME) && (refIds == null || refIds.isEmpty())) {
      refIds =
          new ArrayList<>(Collections.singleton((String) obj.getProperty(STIX_NAME).getValue()));
    }

    if (refIds != null && !refIds.isEmpty()) {
      String stixId = (String) obj.getProperty(CommonProperties.ID).getValue();
      if (stixId != null) {
        stixToRef.add(new StixRefToExternalRef(stixId, refIds));
      }
    }
  }

  private boolean hasDomainNameType(List<Dictionary> observables) {
    if (observables == null || observables.isEmpty()) {
      return false;
    }

    return observables.stream()
        .anyMatch(
            observable ->
                DOMAIN_NAME.equals(observable.get(CommonProperties.TYPE.toString()).getValue()));
  }

  private String getDomainNameValue(List<Dictionary> observables) {
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

  private List<String> getAllDocumentIdsFromFiles(
      io.openaev.stix.types.List<Dictionary> filesList) {
    return filesList.getValue().stream()
        .filter(
            file ->
                file.has(CommonProperties.NAME.toString())
                    && file.has(CommonProperties.URI.toString())
                    && file.has(CommonProperties.MIME_TYPE.toString()))
        .map(
            file -> {
              Document document =
                  openCtiService.downloadAndSaveFile(
                      (String) file.get(CommonProperties.URI.toString()).getValue(),
                      (String) file.get(CommonProperties.NAME.toString()).getValue(),
                      (String) file.get(CommonProperties.MIME_TYPE.toString()).getValue());
              return document != null
                  ? document.getId()
                  : (String) file.get(CommonProperties.NAME.toString()).getValue();
            })
        .filter(Objects::nonNull)
        .toList();
  }
}
