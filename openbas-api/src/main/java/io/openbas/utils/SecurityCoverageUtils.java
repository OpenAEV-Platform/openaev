package io.openbas.utils;

import io.openbas.database.model.StixRefToExternalRef;
import io.openbas.stix.objects.Bundle;
import io.openbas.stix.objects.ObjectBase;
import java.util.ArrayList;
import java.util.List;
import org.apache.coyote.BadRequestException;

public class SecurityCoverageUtils {

  public static final String STIX_X_MITRE_ID = "x_mitre_id";
  public static final String STIX_ID = "id";
  public static final String X_SECURITY_COVERAGE = "x-security-coverage";

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
    List<ObjectBase> coverages = bundle.findByType(X_SECURITY_COVERAGE);
    if (coverages.size() != 1) {
      throw new BadRequestException("STIX bundle must contain exactly one x-security-coverage");
    }
    return coverages.get(0);
  }

  /**
   * Extracts MITRE attack references from a list of STIX objects.
   *
   * <p>For each object that has a {@code x_mitre_id} property, this method creates a {@link
   * StixRefToExternalRef} mapping between the object's STIX ID and its MITRE external ID.
   *
   * @param objects the list of STIX objects to scan
   * @return a list of {@link StixRefToExternalRef} mappings between STIX and MITRE IDs
   */
  public static List<StixRefToExternalRef> extractAttackReferences(List<ObjectBase> objects) {
    List<StixRefToExternalRef> stixToRef = new ArrayList<>();
    for (ObjectBase obj : objects) {
      String mitreId = (String) obj.getProperty(STIX_X_MITRE_ID).getValue();
      if (mitreId != null) {
        String stixId = (String) obj.getProperty(STIX_ID).getValue();
        StixRefToExternalRef stixRef = new StixRefToExternalRef(stixId, mitreId);
        stixToRef.add(stixRef);
      }
    }
    return stixToRef;
  }
}
