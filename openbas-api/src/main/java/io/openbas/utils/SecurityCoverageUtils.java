package io.openbas.utils;

import static io.openbas.utils.constants.StixConstants.STIX_NAME;
import static io.openbas.utils.constants.StixConstants.STIX_TYPE;
import static io.openbas.utils.constants.StixConstants.STIX_X_MITRE_ID;
import static io.openbas.utils.constants.StixConstants.STIX_X_SECURITY_COVERAGE;

import io.openbas.database.model.StixRefToExternalRef;
import io.openbas.stix.objects.Bundle;
import io.openbas.stix.objects.ObjectBase;
import io.openbas.stix.objects.constants.CommonProperties;
import io.openbas.stix.objects.constants.ObjectTypes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.coyote.BadRequestException;

public class SecurityCoverageUtils {

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
    List<ObjectBase> coverages = bundle.findByType(STIX_X_SECURITY_COVERAGE);
    if (coverages.size() != 1) {
      throw new BadRequestException("STIX bundle must contain exactly one x-security-coverage");
    }
    return coverages.get(0);
  }

  /**
   * Extracts references from a list of STIX objects.
   *
   * <p>For each object that has a {@code x_mitre_id} property, this method creates a {@link
   * StixRefToExternalRef} mapping between the object's STIX ID and its MITRE external ID.
   *
   * @param objects the list of STIX objects to scan
   * @return a list of {@link StixRefToExternalRef} mappings between STIX and MITRE IDs
   */
  public static Set<StixRefToExternalRef> extractObjectReferences(List<ObjectBase> objects) {
    Set<StixRefToExternalRef> stixToRef = new HashSet<>();

    for (ObjectBase obj : objects) {
      String stixType = (String) obj.getProperty(STIX_TYPE).getValue();
      String refId;

      if (ObjectTypes.ATTACK_PATTERN.toString().equals(stixType)) {
        refId = (String) obj.getProperty(STIX_X_MITRE_ID).getValue();
      } else {
        refId = (String) obj.getProperty(STIX_NAME).getValue();
      }

      if (refId != null) {
        String stixId = (String) obj.getProperty(CommonProperties.ID).getValue();
        if (stixId != null) {
          stixToRef.add(new StixRefToExternalRef(stixId, refId));
        }
      }
    }

    return stixToRef;
  }

  /**
   * @param objectRefs the list of STIX objects to scan
   * @return a list of {@link StixRefToExternalRef} mappings between STIX and MITRE IDs
   */
  public static Set<String> getExternalIds(Set<StixRefToExternalRef> objectRefs) {
    return objectRefs.stream()
        .map(StixRefToExternalRef::getExternalRef)
        .collect(Collectors.toSet());
  }
}
