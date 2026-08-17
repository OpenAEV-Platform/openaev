package io.openaev.utils.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EndpointMapper.setMacAddresses")
class EndpointMapperTest {

  /** Teredo tunnel pseudo-interface: 8 bytes, identical on every Windows host. */
  private static final String TEREDO_MAC = "00:00:00:00:00:00:00:E0";

  private static final String REGULAR_MAC = "00:AB:AD:C0:FF:EE";
  private static final String REGULAR_MAC_NORMALIZED = "00abadc0ffee";

  @Nested
  @DisplayName("Normalization")
  class Normalization {

    @Test
    @DisplayName("Lowercases and strips separators")
    void given_aColonSeparatedMac_should_lowercaseAndStripSeparators() {
      assertThat(EndpointMapper.setMacAddresses(new String[] {REGULAR_MAC}))
          .containsExactly(REGULAR_MAC_NORMALIZED);
    }

    @Test
    @DisplayName("Accepts dash separators")
    void given_aDashSeparatedMac_should_normalizeItTheSameWay() {
      assertThat(EndpointMapper.setMacAddresses(new String[] {"00-AB-AD-C0-FF-EE"}))
          .containsExactly(REGULAR_MAC_NORMALIZED);
    }

    @Test
    @DisplayName("Collapses values that normalize to the same string")
    void given_theSameMacInTwoFormats_should_keepOnlyOne() {
      assertThat(EndpointMapper.setMacAddresses(new String[] {REGULAR_MAC, "00-ab-ad-c0-ff-ee"}))
          .containsExactly(REGULAR_MAC_NORMALIZED);
    }

    @Test
    @DisplayName("Returns an empty array for null")
    void given_null_should_returnEmptyArray() {
      assertThat(EndpointMapper.setMacAddresses(null)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Blacklist")
  class Blacklist {

    @Test
    @DisplayName("Drops the historical bad MAC addresses")
    void given_theBlacklistedMacs_should_dropThemAll() {
      assertThat(
              EndpointMapper.setMacAddresses(
                  new String[] {"FF:FF:FF:FF:FF:FF", "00:00:00:00:00:00", "01:80:C2:00:00:00"}))
          .isEmpty();
    }

    @Test
    @DisplayName("Keeps a MAC that only shares a prefix with a blacklisted one")
    void given_aMacDifferingFromABlacklistedOneByOneCharacter_should_keepIt() {
      // Pins the substring matching against over-filtering: for 12-character values a substring hit
      // can only happen on an exact match.
      assertThat(EndpointMapper.setMacAddresses(new String[] {"00:00:00:00:00:01"}))
          .containsExactly("000000000001");
    }
  }

  @Nested
  @DisplayName("Non-Ethernet interface addresses")
  class NonEthernetInterfaceAddresses {

    @Test
    @DisplayName("Drops the 8-byte Teredo MAC address")
    void given_theTeredoMac_should_dropIt() {
      assertThat(EndpointMapper.setMacAddresses(new String[] {TEREDO_MAC})).isEmpty();
    }

    @Test
    @DisplayName("Drops the Teredo MAC but keeps the physical one")
    void given_aTeredoMacAlongsideAPhysicalOne_should_keepOnlyThePhysicalOne() {
      // The payload shape a Windows agent actually sends.
      assertThat(EndpointMapper.setMacAddresses(new String[] {REGULAR_MAC, TEREDO_MAC}))
          .containsExactly(REGULAR_MAC_NORMALIZED);
    }

    @Test
    @DisplayName("Drops an all-zero tunnel MAC of any length")
    void given_anAllZeroEightByteMac_should_dropIt() {
      assertThat(EndpointMapper.setMacAddresses(new String[] {"00:00:00:00:00:00:00:00"}))
          .isEmpty();
    }

    @Test
    @DisplayName("Drops any address that is not a 6-byte Ethernet MAC")
    void given_anEightByteMac_should_dropIt() {
      // Length is the rule, not the byte pattern: a pseudo-interface address is dropped whether or
      // not it happens to embed a blacklisted value.
      assertThat(EndpointMapper.setMacAddresses(new String[] {"AA:BB:CC:DD:EE:FF:00:11"}))
          .isEmpty();
      assertThat(EndpointMapper.setMacAddresses(new String[] {"AA:00:00:00:00:00:00:11"}))
          .isEmpty();
    }

    @Test
    @DisplayName("Drops the 4-byte address of a Linux tunnel interface")
    void given_aFourByteTunnelAddress_should_dropIt() {
      // sit0 / tunl0 expose their IPv4 endpoint as hardware address, all zeroes when unconfigured,
      // so every host reporting such an interface would share this value.
      assertThat(EndpointMapper.setMacAddresses(new String[] {"00:00:00:00"})).isEmpty();
    }

    @Test
    @DisplayName("Drops the empty address of an interface with no hardware address")
    void given_anInterfaceWithoutHardwareAddress_should_dropIt() {
      // ARPHRD_NONE interfaces (tun, ppp) declare a zero-length hardware address, which interface
      // enumeration renders as an empty string rather than omitting it.
      assertThat(EndpointMapper.setMacAddresses(new String[] {""})).isEmpty();
    }

    @Test
    @DisplayName("Drops a value that normalizes to something shorter than a MAC")
    void given_aMalformedValue_should_dropIt() {
      // Stripping separators turns arbitrary input into a plausible looking key, which would then
      // be used as an identity fallback.
      assertThat(EndpointMapper.setMacAddresses(new String[] {"not-a-mac"})).isEmpty();
    }
  }
}
