package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Command;
import io.openaev.database.model.DnsResolution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #9875: injects generated from a security coverage are enabled only when known to be safe. */
@DisplayName("Coverage inject safety")
class CoverageInjectSafetyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static InjectorContract templateContract(Boolean readOnly, Boolean intrusive) {
    ObjectNode content = MAPPER.createObjectNode();
    ObjectNode metadata = content.putObject(CoverageInjectSafety.TEMPLATE_METADATA);
    if (readOnly != null) {
      metadata.put(CoverageInjectSafety.READ_ONLY, readOnly);
    }
    if (intrusive != null) {
      metadata.put(CoverageInjectSafety.INTRUSIVE, intrusive);
    }
    InjectorContract contract = new InjectorContract();
    contract.setManual(false);
    contract.setConvertedContent(content);
    return contract;
  }

  private static InjectorContract payloadContract(Payload payload) {
    InjectorContract contract = new InjectorContract();
    contract.setManual(false);
    contract.setPayload(payload);
    return contract;
  }

  @Test
  @DisplayName("given_intrusiveTemplate_should_createInjectDisabled_evenWithTargets")
  void given_intrusiveTemplate_should_createInjectDisabled_evenWithTargets() {
    Inject inject = new Inject();
    CoverageInjectSafety.applyEnabled(inject, templateContract(false, true), true);
    assertThat(inject.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("given_readOnlyTemplateWithTargets_should_stayEnabled")
  void given_readOnlyTemplateWithTargets_should_stayEnabled() {
    Inject inject = new Inject();
    CoverageInjectSafety.applyEnabled(inject, templateContract(true, false), true);
    assertThat(inject.isEnabled()).isTrue();
  }

  @Test
  @DisplayName("given_readOnlyTemplateWithoutTargets_should_createInjectDisabled")
  void given_readOnlyTemplateWithoutTargets_should_createInjectDisabled() {
    Inject inject = new Inject();
    CoverageInjectSafety.applyEnabled(inject, templateContract(true, false), false);
    assertThat(inject.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("given_contractWithoutSafetyMetadata_should_notBeReadOnly")
  void given_contractWithoutSafetyMetadata_should_notBeReadOnly() {
    assertThat(CoverageInjectSafety.isReadOnly(templateContract(null, null))).isFalse();
    assertThat(CoverageInjectSafety.isReadOnly(null)).isFalse();
  }

  @Test
  @DisplayName("given_commandPayload_should_notBeReadOnly")
  void given_commandPayload_should_notBeReadOnly() {
    Command command = new Command();
    command.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    assertThat(CoverageInjectSafety.isReadOnly(payloadContract(command))).isFalse();
  }

  @Test
  @DisplayName("given_verifiedDnsResolution_should_beReadOnly_andUnverifiedNot")
  void given_verifiedDnsResolution_should_beReadOnly_andUnverifiedNot() {
    DnsResolution dns = new DnsResolution();
    dns.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    assertThat(CoverageInjectSafety.isReadOnly(payloadContract(dns))).isTrue();

    DnsResolution unverified = new DnsResolution();
    unverified.setStatus(Payload.PAYLOAD_STATUS.UNVERIFIED);
    assertThat(CoverageInjectSafety.isReadOnly(payloadContract(unverified))).isFalse();
  }

  @Test
  @DisplayName("given_manualPlaceholder_should_notBeReadOnly")
  void given_manualPlaceholder_should_notBeReadOnly() {
    InjectorContract manual = templateContract(true, false);
    manual.setManual(true);
    assertThat(CoverageInjectSafety.isReadOnly(manual)).isFalse();
  }
}
