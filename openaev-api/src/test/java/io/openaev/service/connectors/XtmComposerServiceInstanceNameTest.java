package io.openaev.service.connectors;

import static io.openaev.service.connectors.XtmComposerService.INSTANCE_NAME_MAX_LENGTH;
import static io.openaev.service.connectors.XtmComposerService.instanceName;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class XtmComposerServiceInstanceNameTest {

  private static final String DNS_LABEL = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$";

  @Test
  @DisplayName("A long catalog title gives a name of 63 characters or fewer, valid for Kubernetes")
  void longTitleIsCappedAndDnsSafe() {
    String name =
        instanceName(
            "DNS Enumeration (subdomains and DNS records)", "7653e749-8c9d-4723-a656-84f3b9fda3ab");

    assertThat(name).isEqualTo("dns-enumeration-subdomains-and-dns-records-7653e749");
    assertThat(name.length()).isLessThanOrEqualTo(INSTANCE_NAME_MAX_LENGTH);
    assertThat(name).matches(DNS_LABEL);
  }

  @Test
  @DisplayName("A title that alone exceeds the limit is cut and keeps the id prefix")
  void veryLongTitleIsCut() {
    String name =
        instanceName(
            "A catalog connector whose title is far too long to fit in a Kubernetes name",
            "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");

    assertThat(name.length()).isLessThanOrEqualTo(INSTANCE_NAME_MAX_LENGTH);
    assertThat(name).matches(DNS_LABEL).endsWith("-0f1e2d3c");
  }

  @Test
  @DisplayName("Two instances of one long catalog title keep distinct names")
  void instancesStayDistinct() {
    String title = "DNS Enumeration (subdomains and DNS records)";

    assertThat(instanceName(title, "7653e749-8c9d-4723-a656-84f3b9fda3ab"))
        .isNotEqualTo(instanceName(title, "11111111-8c9d-4723-a656-84f3b9fda3ab"));
  }

  @Test
  @DisplayName("A name that already fits is unchanged, so running deployments keep their name")
  void shortNameIsUnchanged() {
    assertThat(instanceName("Nmap", "7653e749-8c9d-4723-a656-84f3b9fda3ab"))
        .isEqualTo("Nmap-7653e749-8c9d-4723-a656-84f3b9fda3ab");
  }

  @Test
  @DisplayName("A non-ASCII title becomes an ASCII name")
  void nonAsciiTitleIsSlugged() {
    String name = instanceName("Énumération DNS", "7653e749-8c9d-4723-a656-84f3b9fda3ab");

    assertThat(name).matches(DNS_LABEL).endsWith("-7653e749");
  }

  @Test
  @DisplayName("Without an id value the title is the name")
  void missingIdKeepsTitle() {
    assertThat(instanceName("Nmap", "")).isEqualTo("Nmap");
  }
}
