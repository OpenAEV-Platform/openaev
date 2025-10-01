package io.openaev.utils.fixtures.payload_fixture;

import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;

public class ContractOutputElementInputFixture {

  public static final String RULE = "rule";
  public static final String REGEX_RULE = "regex xPath";
  public static final String IPV_6 = "IPV6";
  public static final String CREDENTIALS = "Credentials";
  public static final String CREDENTIALS_USERNAME = "username";

  public static ContractOutputElementInput createDefaultContractOutputElementInputIPV6() {
    ContractOutputElementInput contractOutputElementInput = new ContractOutputElementInput();
    contractOutputElementInput.setFinding(true);
    contractOutputElementInput.setRule(RULE);
    contractOutputElementInput.setName(IPV_6);
    contractOutputElementInput.setKey(IPV_6);
    contractOutputElementInput.setType(ContractOutputType.IPv6);
    return contractOutputElementInput;
  }

  public static ContractOutputElementInput createDefaultContractOutputElementInputCredentials() {
    ContractOutputElementInput contractOutputElementInput = new ContractOutputElementInput();
    contractOutputElementInput.setFinding(true);
    contractOutputElementInput.setRule(REGEX_RULE);
    contractOutputElementInput.setName(CREDENTIALS);
    contractOutputElementInput.setKey(CREDENTIALS_USERNAME);
    contractOutputElementInput.setType(ContractOutputType.Credentials);
    return contractOutputElementInput;
  }
}
