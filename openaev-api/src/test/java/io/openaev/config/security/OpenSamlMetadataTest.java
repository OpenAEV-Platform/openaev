package io.openaev.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensaml.core.Version;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

@DisplayName("OpenSAML metadata parsing")
class OpenSamlMetadataTest {

  @Test
  @DisplayName("runs against the OpenSAML 5 line")
  void should_resolve_opensaml_5() {
    // OpenSAML 4 is unmaintained and dropped by Spring Security 7; the version is forced in the
    // root pom because spring-security-saml2-service-provider still ships 4.3.2.
    assertThat(Version.getVersion()).startsWith("5.");
  }

  @Test
  @DisplayName("parses IdP metadata the way Saml2RelyingPartyAutoConfiguration does")
  void should_parse_asserting_party_metadata() {
    RelyingPartyRegistration registration =
        RelyingPartyRegistrations.fromMetadataLocation("classpath:saml/idp-metadata.xml")
            .registrationId("openaev")
            .entityId("https://openaev.test/saml2/service-provider-metadata/openaev")
            .assertionConsumerServiceLocation("https://openaev.test/login/saml2/sso/openaev")
            .build();

    assertThat(registration.getAssertingPartyMetadata().getEntityId())
        .isEqualTo("https://idp.openaev.test/idp");
    assertThat(registration.getAssertingPartyMetadata().getSingleSignOnServiceLocation())
        .isEqualTo("https://idp.openaev.test/sso");
    assertThat(registration.getAssertingPartyMetadata().getSingleSignOnServiceBinding())
        .isEqualTo(Saml2MessageBinding.REDIRECT);
    assertThat(registration.getAssertingPartyMetadata().getVerificationX509Credentials())
        .isNotEmpty();
  }
}
