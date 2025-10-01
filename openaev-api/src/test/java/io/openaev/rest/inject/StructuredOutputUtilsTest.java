package io.openaev.rest.inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.rest.inject.service.StructuredOutputUtils;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StructuredOutputUtilsTest extends IntegrationTest {

  public static final String SIMPLE_RAW_OUTPUT_TASKLIST =
      "\r\nImage Name                 PID  Session Name        Session#    Mem Usage\r\n"
          + "=========================  ========  ================  ===========  ============\r\n"
          + "System Idle Process           0  Services               0          8 K\r\n";

  public static final String SIMPLE_RAW_OUTPUT_NETSTAT =
      "\n"
          + "Active Connections\n"
          + "\n"
          + "  Proto  Local Address          Foreign Address        State\n"
          + "  TCP    192.168.1.10:135            0.0.0.0:0              LISTENING\n"
          + "  TCP    176.125.125.10:445            0.0.0.0:0              LISTENING\n"
          + "  TCP    192.168.12.12:902            0.0.0.0:0              LISTENING\n";

  private final ObjectMapper mapper = new ObjectMapper();

  private StructuredOutputUtils structuredOutputUtils;

  @BeforeEach
  void setup() {
    structuredOutputUtils = new StructuredOutputUtils(mapper);
  }

  @Test
  @DisplayName("Should return null string for a raw output empty")
  void given_a_raw_output_empty_should_return_null() {
    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("Empty output");
    regexGroup.setIndexValues("$2");
    testRegexExtraction(
        "",
        Set.of(regexGroup),
        ContractOutputType.Text,
        "Text",
        "^(\\S+)",
        Arrays.toString(Arrays.copyOf(new String[0], 0)));
  }

  @Test
  @DisplayName("Should return null for an index bigger than matcher count")
  void given_a_group_bigger_than_matcher_count_should_return_null() {
    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("Wrong Index");
    regexGroup.setIndexValues("$2");
    testRegexExtraction(
        SIMPLE_RAW_OUTPUT_TASKLIST,
        Set.of(regexGroup),
        ContractOutputType.Text,
        "Text",
        "^(\\S+)",
        Arrays.toString(Arrays.copyOf(new String[0], 0)));
  }

  @Test
  @DisplayName("Should return null for a non-numeric index")
  void given_an_index_no_numerical_should_return_null() {
    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("Non-numeric Index");
    regexGroup.setIndexValues("$t");
    testRegexExtraction(
        SIMPLE_RAW_OUTPUT_TASKLIST,
        Set.of(regexGroup),
        ContractOutputType.Text,
        "Text",
        "^(\\S+)",
        Arrays.toString(Arrays.copyOf(new String[0], 0)));
  }

  @Test
  @DisplayName("Should return numbers from raw output of command")
  void given_raw_output_netstat_should_return_number() {
    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("Any number");
    regexGroup.setIndexValues("$1");
    testRegexExtraction(
        SIMPLE_RAW_OUTPUT_NETSTAT,
        Set.of(regexGroup),
        ContractOutputType.Number,
        "Number",
        "(\\d+)",
        "[192,168,1,10,135,0,0,0,0,0,176,125,125,10,445,0,0,0,0,0,192,168,12,12,902,0,0,0,0,0]");
  }

  @Test
  @DisplayName("Should get image names from raw output of tasklist command")
  void given_raw_output_tasklist_should_return_names() {
    String rawOutput =
        "\r\nImage Name                 PID Session Name        Session#    Mem Usage\r\n"
            + "=========================  ========  ================  ===========  ============\r\n"
            + "System Idle Process           0 Services               0          8 K\r\n"
            + "System                       4 Services               0         16 K\r\n"
            + "Secure System                284 Services               0    112,156 K\r\n"
            + "Registry                     328 Services               0     58,904 K\r\n";

    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("Image name");
    regexGroup.setIndexValues("$0");

    testRegexExtraction(
        rawOutput,
        Set.of(regexGroup),
        ContractOutputType.Text,
        "Text",
        "^(\\S+)",
        "[\"Image\",\"=========================\",\"System\",\"System\",\"Secure\",\"Registry\"]");
  }

  @Test
  @DisplayName("Should get cve from raw output command")
  void given_raw_output_tasklist_should_return_cve() {
    // username:RID:LM_Hash:NTLM_Hash:::
    String rawOutput =
        "{\n"
            + "  \"severity\": \"critical\",\n"
            + "  \"host\": \"192.168.56.23\",\n"
            + "  \"classification\": {\n"
            + "    \"cve-id\": [\n"
            + "      \"cve-2023-35078\"\n"
            + "    ]\n"
            + "  }\n"
            + "}";

    RegexGroup regexGroup1 = new RegexGroup();
    regexGroup1.setField("severity");
    regexGroup1.setIndexValues("$1");

    RegexGroup regexGroup2 = new RegexGroup();
    regexGroup2.setField("host");
    regexGroup2.setIndexValues("$2");

    RegexGroup regexGroup3 = new RegexGroup();
    regexGroup3.setField("id");
    regexGroup3.setIndexValues("$3");

    Set<RegexGroup> regexGroups = Set.of(regexGroup1, regexGroup2, regexGroup3);

    String regex =
        "\"severity\"\\s*:\\s*\"([^\"]+)\"[\\s\\S]*?"
            + "\"host\"\\s*:\\s*\"([^\"]+)\"[\\s\\S]*?"
            + "\"cve-id\"\\s*:\\s*\\[\\s*((?:\"[^\"]+\"\\s*,?\\s*)+)";

    this.testRegexExtraction(
        rawOutput,
        regexGroups,
        ContractOutputType.CVE,
        "CVE",
        regex,
        "[{\"asset_id\":null,\"id\":\"\\\"cve-2023-35078\\\"\",\"host\":\"192.168.56.23\",\"severity\":\"critical\"}]");
  }

  @Test
  @DisplayName("Should get hosts, ports, service from raw output of netstat command")
  void given_raw_output_netstat_should_return_portscans() {
    RegexGroup regexGroup1 = new RegexGroup();
    regexGroup1.setField("host");
    regexGroup1.setIndexValues("$2");

    RegexGroup regexGroup2 = new RegexGroup();
    regexGroup2.setField("port");
    regexGroup2.setIndexValues("$3");

    RegexGroup regexGroup3 = new RegexGroup();
    regexGroup3.setField("service");
    regexGroup3.setIndexValues("$4");

    Set<RegexGroup> regexGroups = Set.of(regexGroup1, regexGroup2, regexGroup3);

    testRegexExtraction(
        SIMPLE_RAW_OUTPUT_NETSTAT,
        regexGroups,
        ContractOutputType.PortsScan,
        "PortScan",
        "^\\s*(TCP|UDP)\\s+([\\d\\.]+|\\*)?:?(\\d+)\\s+\\S+\\s+(\\S+)",
        "[{\"asset_id\":null,\"host\":\"192.168.1.10\",\"port\":135,\"service\":\"LISTENING\"},{\"asset_id\":null,\"host\":\"176.125.125.10\",\"port\":445,\"service\":\"LISTENING\"},{\"asset_id\":null,\"host\":\"192.168.12.12\",\"port\":902,\"service\":\"LISTENING\"}]");
  }

  @Test
  @DisplayName("Should return ports from raw output of netstat -an command")
  void given_raw_output_netstat_should_return_ports() {
    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("port");
    regexGroup.setIndexValues("$1");

    testRegexExtraction(
        SIMPLE_RAW_OUTPUT_NETSTAT,
        Set.of(regexGroup),
        ContractOutputType.Port,
        "Port",
        "(?:TCP|UDP)\\s+[\\d\\.]+:(\\d+)",
        "[135,445,902]");
  }

  @Test
  @DisplayName("Should return IPv4s from raw output of netstat -an command")
  void given_raw_output_netstat_should_return_ipv4() {
    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("ipv4");
    regexGroup.setIndexValues("$0");

    testRegexExtraction(
        SIMPLE_RAW_OUTPUT_NETSTAT,
        Set.of(regexGroup),
        ContractOutputType.IPv4,
        "Ipv4",
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b",
        "[\"192.168.1.10\",\"0.0.0.0\",\"176.125.125.10\",\"0.0.0.0\",\"192.168.12.12\",\"0.0.0.0\"]");
  }

  @Test
  @DisplayName("Should return IPv6s from raw output of netstat -an command")
  void given_raw_output_netstat_should_return_ipv6() {
    String rawOutput =
        "\n"
            + "Active Connections\n"
            + "\n"
            + " Proto Local Address Foreign Address State\n"
            + " TCP 0.0.0.0:135 0.0.0.0:0 LISTENING\n"
            + " UDP [fe80::1b03:a1ff:ccdb:b464%66]:1900 *:*\n"
            + " UDP [fe80::1b04:a1ff:ccdb:b464%66]:2177 *:*\n"
            + " UDP [fe80::1b03:a1ff:ccdb:b464%66]:58907 *:*\n"
            + " UDP [fe80::6168:894c:9ee9:d02a%27]:1900 *:*\n";

    RegexGroup regexGroup = new RegexGroup();
    regexGroup.setField("ipv6");
    regexGroup.setIndexValues("$1");

    String ipv6Regex = "\\[([a-fA-F0-9:]+(?:%[a-zA-Z0-9]+)?)\\]:\\d+";

    testRegexExtraction(
        rawOutput,
        Set.of(regexGroup),
        ContractOutputType.IPv6,
        "Ipv6",
        ipv6Regex,
        "[\"fe80::1b03:a1ff:ccdb:b464%66\",\"fe80::1b04:a1ff:ccdb:b464%66\",\"fe80::1b03:a1ff:ccdb:b464%66\",\"fe80::6168:894c:9ee9:d02a%27\"]");
  }

  @Test
  @DisplayName("Should get username:password from raw output command")
  void given_raw_output_credentials_should_return_credentials() {
    String rawOutput =
        "SMB                      192.168.11.23   415    CASSANOVAS          [+] workgroup\\\\savacano:savacano (Pwn3d!)\\n";

    RegexGroup regexGroup1 = new RegexGroup();
    regexGroup1.setField("username");
    regexGroup1.setIndexValues("$2");

    RegexGroup regexGroup2 = new RegexGroup();
    regexGroup2.setField("password");
    regexGroup2.setIndexValues("$3");

    Set<RegexGroup> regexGroups = Set.of(regexGroup1, regexGroup2);

    this.testRegexExtraction(
        rawOutput,
        regexGroups,
        ContractOutputType.Credentials,
        "Credentials",
        "(\\S+)\\\\(\\S+):(\\S+)",
        "[{\"username\":\"savacano\",\"password\":\"savacano\"}]");
  }

  private void testRegexExtraction(
      String input,
      Set<RegexGroup> regexGroups,
      ContractOutputType type,
      String key,
      String regex,
      String expected) {
    ContractOutputElement contractOutputElement = new ContractOutputElement();
    contractOutputElement.setType(type);
    contractOutputElement.setRule(regex);
    contractOutputElement.setKey(key);
    contractOutputElement.setRegexGroups(regexGroups);

    OutputParser outputParser = new OutputParser();
    outputParser.setType(ParserType.REGEX);
    outputParser.setMode(ParserMode.STDOUT);
    outputParser.setContractOutputElements(Set.of(contractOutputElement));

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stdoutNode = mapper.createObjectNode();
    stdoutNode.put("stdout", input);
    String jsonInput = stdoutNode.toString();

    Optional<ObjectNode> result =
        structuredOutputUtils.computeStructuredOutputFromOutputParsers(
            Set.of(outputParser), jsonInput);

    if (result.isEmpty()) {
      assertNull(expected);
    } else {
      assertEquals(expected, result.get().get(key).toString());
    }
  }
}
