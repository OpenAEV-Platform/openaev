package io.openaev.utils;

import static io.openaev.utils.constants.StixConstants.*;

import io.openaev.database.model.Document;
import io.openaev.database.model.StixRefToExternalRef;
import io.openaev.database.model.Tag;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.document.form.DocumentCreateInput;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.ObjectBase;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.objects.constants.ExtendedProperties;
import io.openaev.stix.objects.constants.ObjectTypes;
import io.openaev.stix.types.BaseType;
import io.openaev.stix.types.Dictionary;
import io.openaev.utils.constants.StixConstants;
import jakarta.validation.constraints.NotBlank;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
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
  private final TagService tagService;

  /** Connection timeout in milliseconds for file downloads (10 seconds). */
  private static final int CONNECTION_TIMEOUT_MS = 10000;

  /** Read timeout in milliseconds for file downloads (30 seconds). */
  private static final int READ_TIMEOUT_MS = 30000;

  @NotBlank
  @Value("${openaev.xtm.opencti.url:#{null}}")
  private String octiUrl;

  private final DocumentService documentService;

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
  public ObjectBase extractAndValidateCoverage(Bundle bundle) throws BadRequestException {
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
          continue;
        }
      }

      if (ObjectTypes.ARTIFACT.toString().equals(stixType)
          && obj.hasExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION)) {
        Dictionary extensionObj =
            (Dictionary) obj.getExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION);
        if (extensionObj.has(StixConstants.FILES)) {

          TagCreateInput tagCreateInput = new TagCreateInput();
          tagCreateInput.setName(Tag.OPENCTI_TAG_NAME);
          Tag openCtiTag = tagService.upsertTag(tagCreateInput);
          String openCitTagId = openCtiTag.getId();
          List<String> documentIds = new ArrayList<>();
          String url = octiUrl.endsWith("/") ? octiUrl.substring(0, octiUrl.length() - 1) : octiUrl;

          io.openaev.stix.types.List<Dictionary> filesList = (io.openaev.stix.types.List<Dictionary>) extensionObj.get(StixConstants.FILES);
          filesList.getValue().stream()
                  .filter(
                      file ->
                          file.has(CommonProperties.NAME.toString())
                              && file.has(CommonProperties.URI.toString())
                              && file.has(CommonProperties.MIME_TYPE.toString()))
                  .forEach(
                      file -> {
                        String fileName =
                            (String) file.get(CommonProperties.NAME.toString()).getValue();
                        String fileUri =
                            (String) file.get(CommonProperties.URI.toString()).getValue();
                        String fileMymeType =
                            (String) file.get(CommonProperties.MIME_TYPE.toString()).getValue();

                        try {

                          HttpURLConnection connection = (HttpURLConnection) new URL(url + fileUri).openConnection();
                          connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                          connection.setReadTimeout(READ_TIMEOUT_MS);

                          if (connection instanceof HttpURLConnection httpConnection) {
                              httpConnection.setRequestMethod("GET");
                          }

                          try (InputStream inputStream = connection.getInputStream()) {
                              DocumentCreateInput documentCreateInput = new DocumentCreateInput();
                              documentCreateInput.setDescription(fileName);
                              documentCreateInput.setTagIds(new ArrayList<>(Set.of(openCitTagId)));

                              Document document =
                                      documentService.upsert(
                                              fileName,
                                              inputStream,
                                              connection.getContentLengthLong(),
                                              fileMymeType,
                                              documentCreateInput);
                              documentIds.add(document.getId());
                          }
                        } catch (IOException e) {
                            throw new RuntimeException("Error while downloading file "  + fileName + " from octi URL " + url + fileUri, e);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException("Invalid file URL: " + url + fileUri, e);
                        } catch (Exception e) {
                            throw new RuntimeException("Error during management of file " + fileName + " from URL " + url + fileUri, e);
                        }
                      });
            manageAndAddStixRefToExternalRefs(stixToRef, obj, documentIds);
            continue;
        }
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
}
