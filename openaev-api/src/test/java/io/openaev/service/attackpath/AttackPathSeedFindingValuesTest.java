package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.output_processor.PortScanOutputProcessor;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A seeded finding value must be re-extracted by the real {@link AttackPathKeyMatcher} exactly as a
 * produced key consumes it, so the causal chain's {@code matchedFindingIds} resolve on seeded rows
 * identically to real ones. Pure by design: the matcher is a static function over {@link
 * AttackPathFindingRow}, so no DB/Spring is needed to pin the format.
 */
@DisplayName("causal seed: finding values match the real key matcher")
class AttackPathSeedFindingValuesTest {

  private static AttackPathFindingRow row(String type, String value) {
    return new AttackPathFindingRow("f", type, value, null, null, null, null, true);
  }

  private static boolean matchesEq(String type, String value, String keyType, String keyValue) {
    return AttackPathKeyMatcher.matches(
        row(type, value), new ConsumedFindingKeyDTO(keyType, "EQ", keyValue, null));
  }

  @Test
  @DisplayName("portscan value exposes its port sub-field to a port EQ key")
  void portscanMatchesPort() {
    String value = AttackPathSeedFindingValues.portscan("20.224.192.102", 445, "microsoft-ds");
    assertThat(matchesEq("portscan", value, "port", "445")).isTrue();
  }

  @Test
  @DisplayName("portscan value is byte-identical to PortScanOutputProcessor.toFindingValue")
  void portscanIsByteIdenticalToProcessor() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("host", "20.224.192.102");
    node.put("port", 445);
    node.put("service", "microsoft-ds");
    String produced = new PortScanOutputProcessor(null).toFindingValue(node);
    assertThat(AttackPathSeedFindingValues.portscan("20.224.192.102", 445, "microsoft-ds"))
        .isEqualTo(produced);
  }

  @Test
  @DisplayName(
      "credentials value exposes username and password sub-fields (password keeps its colon)")
  void credentialsMatchesUsernameAndPassword() {
    String value = AttackPathSeedFindingValues.credentials("administrator", "P@ss:w0rd");
    assertThat(matchesEq("credentials", value, "username", "administrator")).isTrue();
    assertThat(matchesEq("credentials", value, "password", "P@ss:w0rd")).isTrue();
  }

  @Test
  @DisplayName("share value exposes its share_name sub-field")
  void shareMatchesShareName() {
    String value = AttackPathSeedFindingValues.share("winterfell", "C$", "READ,WRITE");
    assertThat(matchesEq("share", value, "share_name", "C$")).isTrue();
  }

  @Test
  @DisplayName("file value exposes its file_name basename")
  void fileMatchesFileName() {
    String value =
        AttackPathSeedFindingValues.file("winterfell", "C$", "Users/admin", "secret.txt");
    assertThat(matchesEq("file", value, "file_name", "secret.txt")).isTrue();
  }

  @Test
  @DisplayName("username value exposes its username and domain sub-fields")
  void usernameMatchesUserAndDomain() {
    String value = AttackPathSeedFindingValues.username("CORP", "administrator");
    assertThat(matchesEq("username", value, "username", "administrator")).isTrue();
    assertThat(matchesEq("username", value, "domain", "CORP")).isTrue();
  }

  @Test
  @DisplayName("cve value matches a cve EQ key on the whole value")
  void cveMatchesWholeValue() {
    String value = AttackPathSeedFindingValues.cve("CVE-2021-34527");
    assertThat(matchesEq("cve", value, "cve", "CVE-2021-34527")).isTrue();
  }

  @Test
  @DisplayName("bare port value matches a port EQ key on the whole value")
  void barePortMatchesWholeValue() {
    String value = AttackPathSeedFindingValues.port(445);
    assertThat(matchesEq("port", value, "port", "445")).isTrue();
  }
}
