package io.openaev.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.opensaml.core.Version;
import org.springframework.security.saml2.Saml2Exception;
import org.springframework.security.saml2.provider.service.metadata.OpenSaml5MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

class OpenSamlMetadataTest {

  private static final String ACS = "https://openaev.test/login/saml2/sso/openaev";
  private static final String ENTITY_ID = "https://openaev.test/saml2/service-provider-metadata";

  private static RelyingPartyRegistration registration() {
    return RelyingPartyRegistrations.fromMetadataLocation("classpath:saml/idp-metadata.xml")
        .registrationId("openaev")
        .entityId(ENTITY_ID)
        .assertionConsumerServiceLocation(ACS)
        .build();
  }

  @Test
  void should_resolve_opensaml_5() {
    assertThat(Version.getVersion()).startsWith("5.");
  }

  @Test
  void should_parse_asserting_party_metadata() {
    RelyingPartyRegistration registration = registration();

    assertThat(registration.getAssertingPartyMetadata().getEntityId())
        .isEqualTo("https://idp.openaev.test/idp");
    assertThat(registration.getAssertingPartyMetadata().getSingleSignOnServiceLocation())
        .isEqualTo("https://idp.openaev.test/sso");
    assertThat(registration.getAssertingPartyMetadata().getSingleSignOnServiceBinding())
        .isEqualTo(Saml2MessageBinding.REDIRECT);
    assertThat(registration.getAssertingPartyMetadata().getVerificationX509Credentials())
        .hasSize(1);
  }

  @Test
  void should_reject_metadata_without_asserting_party() {
    assertThatThrownBy(
            () ->
                RelyingPartyRegistrations.fromMetadataLocation(
                    "classpath:saml/sp-only-metadata.xml"))
        .isInstanceOf(Saml2Exception.class);
  }

  @Test
  void should_resolve_service_provider_metadata() {
    String metadata = new OpenSaml5MetadataResolver().resolve(registration());

    assertThat(metadata).contains("entityID=\"" + ENTITY_ID + "\"").contains(ACS);
  }
}
