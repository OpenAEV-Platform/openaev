package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Instance creation date")
class InstanceCreationDateTest {

  private static final Instant CREATED = Instant.parse("2026-10-05T16:02:03.456Z");
  private static final List<ZoneId> ZONES =
      List.of(
          ZoneId.of("UTC"),
          ZoneId.of("Europe/Paris"),
          ZoneId.of("Asia/Tokyo"),
          ZoneId.of("America/Los_Angeles"),
          ZoneId.of("Pacific/Kiritimati"));

  @Test
  @DisplayName("Given an instant should write it as an ISO-8601 instant in UTC")
  void given_instant_should_formatIsoUtc() {
    String value = InstanceCreationDate.format(CREATED);

    assertThat(value).isEqualTo("2026-10-05T16:02:03.456Z");
    assertThat(InstanceCreationDate.isInstant(value)).isTrue();
    assertThat(InstanceCreationDate.parse(value)).contains(CREATED);
  }

  @Test
  @DisplayName("Given an ISO-8601 value should read the same instant in every time zone")
  void given_isoValue_should_beTimeZoneIndependent() {
    for (ZoneId zone : ZONES) {
      assertThat(InstanceCreationDate.parse("2026-10-05T16:02:03.456Z", zone)).contains(CREATED);
      assertThat(InstanceCreationDate.parse("2026-10-05T18:02:03.456+02:00", zone))
          .contains(CREATED);
    }
  }

  @Test
  @DisplayName("Given a legacy Timestamp value should read it in the zone it was written in")
  void given_legacyValue_should_readItInItsZone() {
    assertThat(InstanceCreationDate.parse("2026-10-05 18:02:03.456", ZoneId.of("Europe/Paris")))
        .contains(CREATED);
    assertThat(InstanceCreationDate.parse("2026-10-06 01:02:03.456", ZoneId.of("Asia/Tokyo")))
        .contains(CREATED);
    assertThat(InstanceCreationDate.parse("2026-10-05 16:02:03.0", ZoneId.of("UTC")))
        .contains(Instant.parse("2026-10-05T16:02:03Z"));
    assertThat(InstanceCreationDate.parse("2026-10-05 16:02:03.123456789", ZoneId.of("UTC")))
        .contains(Instant.parse("2026-10-05T16:02:03.123456789Z"));
    assertThat(InstanceCreationDate.parse("2026-10-05 16:02:03", ZoneId.of("UTC")))
        .contains(Instant.parse("2026-10-05T16:02:03Z"));
    assertThat(InstanceCreationDate.isInstant("2026-10-05 16:02:03.0")).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(
      strings = {
        "",
        "  ",
        "yesterday",
        "2026-10-05",
        "2026-13-05 16:02:03",
        "16:02:03",
        "2026-02-30 16:02:03",
        "2027-02-29 16:02:03.5",
        "2026-10-05 24:00:00",
        "2026-10-05 16:02:03.",
        "2026-10-05 16:02:03.1234567890",
        "2026-02-30T16:02:03Z",
        "2026-10-05T16:02:03"
      })
  @DisplayName("Given a blank or unreadable value should read nothing")
  void given_unreadableValue_should_readNothing(String value) {
    assertThat(InstanceCreationDate.parse(value)).isEmpty();
    assertThat(InstanceCreationDate.isInstant(value)).isFalse();
  }

  @Test
  @DisplayName("Given no value should read nothing")
  void given_null_should_readNothing() {
    assertThat(InstanceCreationDate.parse(null)).isEmpty();
    assertThat(InstanceCreationDate.isInstant(null)).isFalse();
  }
}
