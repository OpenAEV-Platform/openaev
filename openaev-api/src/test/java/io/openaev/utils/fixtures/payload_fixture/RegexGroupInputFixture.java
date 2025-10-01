package io.openaev.utils.fixtures.payload_fixture;

import static io.openaev.utils.fixtures.payload_fixture.ContractOutputElementInputFixture.CREDENTIALS_USERNAME;
import static io.openaev.utils.fixtures.payload_fixture.ContractOutputElementInputFixture.IPV_6;

import io.openaev.rest.payload.regex_group.RegexGroupInput;

public class RegexGroupInputFixture {

  public static final String INDEX_VALUES_O = "$0";
  public static final String INDEX_VALUES_1 = "$1";

  public static RegexGroupInput createDefaultRegexGroupInputIPV6() {
    RegexGroupInput regexGroupInput = new RegexGroupInput();
    regexGroupInput.setField(IPV_6);
    regexGroupInput.setIndexValues(INDEX_VALUES_O);
    return regexGroupInput;
  }

  public static RegexGroupInput createDefaultRegexGroupInputCredentials() {
    RegexGroupInput regexGroupInput = new RegexGroupInput();
    regexGroupInput.setField(CREDENTIALS_USERNAME);
    regexGroupInput.setIndexValues(INDEX_VALUES_1);
    return regexGroupInput;
  }
}
