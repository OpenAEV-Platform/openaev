package io.openaev.utils.fixtures;

import io.openaev.rest.mitigation.form.MitigationCreateInput;
import io.openaev.rest.mitigation.form.MitigationUpdateInput;
import java.util.UUID;

public class MitigationInputFixture {

  public static final String DEFAULT_MITIGATION_NAME = "Account Use Policies";
  public static final String DEFAULT_MITIGATION_EXTERNAL_ID = "M1036";
  public static final String DEFAULT_MITIGATION_DESCRIPTION =
      "Configure features related to account use like login attempt lockouts.";

  /** Create input with a unique external ID to avoid constraint conflicts across tests. */
  public static MitigationCreateInput createDefaultMitigationInput() {
    return createMitigationInput(
        "mitigation-" + UUID.randomUUID(), "M-" + UUID.randomUUID().toString().substring(0, 8));
  }

  public static MitigationCreateInput createMitigationInput(String name, String externalId) {
    MitigationCreateInput input = new MitigationCreateInput();
    input.setName(name);
    input.setExternalId(externalId);
    input.setDescription(DEFAULT_MITIGATION_DESCRIPTION);
    return input;
  }

  public static MitigationUpdateInput createDefaultMitigationUpdateInput() {
    return createMitigationUpdateInput(DEFAULT_MITIGATION_NAME, DEFAULT_MITIGATION_EXTERNAL_ID);
  }

  public static MitigationUpdateInput createMitigationUpdateInput(String name, String externalId) {
    MitigationUpdateInput input = new MitigationUpdateInput();
    input.setName(name);
    input.setExternalId(externalId);
    return input;
  }
}
