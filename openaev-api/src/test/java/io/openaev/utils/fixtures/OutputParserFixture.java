package io.openaev.utils.fixtures;

import io.openaev.database.model.*;
import java.util.Set;

public class OutputParserFixture {

  public static OutputParser getOutputParser(Set<ContractOutputElement> contractOutputElements) {
    OutputParser outputParser = new OutputParser();
    outputParser.setType(ParserType.REGEX);
    outputParser.setMode(ParserMode.STDOUT);
    outputParser.setContractOutputElements(contractOutputElements);
    return outputParser;
  }

  public static OutputParser getDefaultOutputParser() {
    ContractOutputElement contractOutputElement = getDefaultContractOutputElement();
    return getOutputParser(Set.of(contractOutputElement));
  }

  public static RegexGroup getRegexGroup(String field, String value) {
    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField(field);
    regexGroup.setIndexValues(value);
    return regexGroup;
  }

  public static RegexGroup getDefaultRegexGroup() {
    return getRegexGroup("Any text", "$1");
  }

  public static ContractOutputElement getContractOutputElement(
      ContractOutputType type, String rule, Set<RegexGroup> regexGroup, Boolean isFinding) {
    ContractOutputElement contractOutputElement = new ContractOutputElement();
    contractOutputElement.setType(type);
    contractOutputElement.setRule(rule);
    contractOutputElement.setRegexGroups(regexGroup);
    contractOutputElement.setKey(type.label + "-key");
    contractOutputElement.setName(type.label + " Name");
    contractOutputElement.setFinding(isFinding);
    return contractOutputElement;
  }

  public static ContractOutputElement getDefaultContractOutputElement() {
    return getContractOutputElement(
        ContractOutputType.IPv6, "/d+", Set.of(getDefaultRegexGroup()), false);
  }

  public static ContractOutputElement getCVEOutputElement() {
    RegexGroup regexGroupHost = getRegexGroup("host", "3");
    RegexGroup regexGroupSeverity = getRegexGroup("severity", "2");
    RegexGroup regexGroupId = getRegexGroup("id", "1");
    return getContractOutputElement(
        ContractOutputType.CVE,
        "\\[(CVE-\\d{4}-\\d+)\\]\\s\\[\\w+\\]\\s\\[(\\w+)\\]\\s((http[s]?:\\/\\/[^\\s]+))",
        Set.of(regexGroupHost, regexGroupSeverity, regexGroupId),
        true);
  }
}
