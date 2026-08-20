package io.openaev.executors.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the MAC sanitization seam. Agent registration and every external executor (CrowdStrike,
 * SentinelOne, Tanium, Palo Alto Cortex) feed their MAC addresses through this setter, which is the
 * only place they are normalized before reaching the endpoint matching queries. Dropping the
 * delegation here would silently reopen asset merging on shared pseudo-interface addresses for all
 * of them.
 */
@DisplayName("AgentRegisterInput.setMacAddresses")
class AgentRegisterInputTest {

  private static final String TEREDO_MAC = "00:00:00:00:00:00:00:E0";

  @Test
  @DisplayName("Normalizes MAC addresses")
  void given_aFormattedMac_should_normalizeIt() {
    AgentRegisterInput input = new AgentRegisterInput();

    input.setMacAddresses(new String[] {"00:AB:AD:C0:FF:EE"});

    assertThat(input.getMacAddresses()).containsExactly("00abadc0ffee");
  }

  @Test
  @DisplayName("Drops tunnel pseudo-interface MAC addresses")
  void given_aTeredoMac_should_dropIt() {
    AgentRegisterInput input = new AgentRegisterInput();

    input.setMacAddresses(new String[] {"00:AB:AD:C0:FF:EE", TEREDO_MAC});

    assertThat(input.getMacAddresses()).containsExactly("00abadc0ffee");
  }

  @Test
  @DisplayName("Drops blacklisted MAC addresses")
  void given_blacklistedMacs_should_dropThem() {
    AgentRegisterInput input = new AgentRegisterInput();

    input.setMacAddresses(new String[] {"FF:FF:FF:FF:FF:FF", "00:00:00:00:00:00"});

    assertThat(input.getMacAddresses()).isEmpty();
  }

  @Test
  @DisplayName("Returns an empty array for null")
  void given_null_should_returnEmptyArray() {
    AgentRegisterInput input = new AgentRegisterInput();

    input.setMacAddresses(null);

    assertThat(input.getMacAddresses()).isEmpty();
  }
}
