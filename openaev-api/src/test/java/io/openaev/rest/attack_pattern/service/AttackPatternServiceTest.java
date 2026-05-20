package io.openaev.rest.attack_pattern.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.utils.SecurityCoverageUtils;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import io.openaev.xtmone.XtmOneService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AttackPatternServiceTest {

  @Mock private Environment env;
  @Mock private AttackPatternRepository attackPatternRepository;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private RestTemplate restTemplate;
  @Mock private SecurityCoverageUtils securityCoverageUtils;
  @Mock private XtmOneConfig xtmOneConfig;
  @Mock private XtmOneClient xtmOneClient;
  @Mock private XtmOneService xtmOneService;
  @Mock private UserService userService;

  @InjectMocks private AttackPatternService attackPatternService;

  @BeforeEach
  void beforeEach() {
    attackPatternService.mapper = new ObjectMapper();
  }

  /**
   * Build a stub {@link User} that satisfies the per-user JWT minting performed by the XTM One
   * branch of {@code searchAttackPatternWithTTPAIWebservice} (no DB reads required because {@code
   * userService} is mocked).
   */
  private static User stubUser() {
    User user = new User();
    user.setId("user-id");
    user.setEmail("tester@openaev.local");
    return user;
  }

  @DisplayName("given_noFilesAndBlankText_should_throwIllegalArgumentException")
  @Test
  void given_noFilesAndBlankText_should_throwIllegalArgumentException() {
    // Arrange
    List<MockMultipartFile> files = List.of();

    // Act / Assert
    assertThrows(
        IllegalArgumentException.class,
        () ->
            attackPatternService.searchAttackPatternWithTTPAIWebservice(
                new ArrayList<>(files), "  "));
  }

  @DisplayName("given_moreThanFiveFiles_should_throwIllegalArgumentException")
  @Test
  void given_moreThanFiveFiles_should_throwIllegalArgumentException() {
    // Arrange
    List<org.springframework.web.multipart.MultipartFile> files =
        List.of(
            new MockMultipartFile("file", "1.txt", "text/plain", "1".getBytes()),
            new MockMultipartFile("file", "2.txt", "text/plain", "2".getBytes()),
            new MockMultipartFile("file", "3.txt", "text/plain", "3".getBytes()),
            new MockMultipartFile("file", "4.txt", "text/plain", "4".getBytes()),
            new MockMultipartFile("file", "5.txt", "text/plain", "5".getBytes()),
            new MockMultipartFile("file", "6.txt", "text/plain", "6".getBytes()));

    // Act / Assert
    assertThrows(
        IllegalArgumentException.class,
        () -> attackPatternService.searchAttackPatternWithTTPAIWebservice(files, "valid text"));
  }

  @DisplayName("given_missingEnterpriseLicense_should_throwIllegalStateException")
  @Test
  void given_missingEnterpriseLicense_should_throwIllegalStateException() {
    // Arrange
    when(xtmOneConfig.isConfigured()).thenReturn(false);
    when(env.getProperty("ttp.extraction.ai.webservice.url")).thenReturn("http://localhost/api");
    when(enterpriseEditionService.getEnterpriseEditionLicensePem()).thenReturn(" ");

    // Act / Assert
    assertThrows(
        IllegalStateException.class,
        () ->
            attackPatternService.searchAttackPatternWithTTPAIWebservice(List.of(), "extract this"));
    verify(restTemplate, never()).postForEntity(anyString(), any(), any());
  }

  @DisplayName("given_webserviceResponse_should_returnInternalAttackPatternIds")
  @Test
  void given_webserviceResponse_should_returnInternalAttackPatternIds() {
    // Arrange
    when(xtmOneConfig.isConfigured()).thenReturn(false);
    when(env.getProperty("ttp.extraction.ai.webservice.url")).thenReturn("http://localhost/api");
    when(enterpriseEditionService.getEnterpriseEditionLicensePem()).thenReturn("pem-content");

    String responseBody = "[[{\"text\":\"chunk\",\"predictions\":{\"T1003\":0.9,\"T1059\":0.8}}]]";
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(ResponseEntity.ok(responseBody));

    AttackPattern first = new AttackPattern();
    first.setId("internal-1");
    first.setExternalId("t1003");
    AttackPattern second = new AttackPattern();
    second.setId("internal-2");
    second.setExternalId("T1059");

    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(anyList(), anyString()))
        .thenReturn(List.of(first, second));

    // Act
    List<String> ids =
        attackPatternService.searchAttackPatternWithTTPAIWebservice(List.of(), "extract this");

    // Assert
    assertEquals(2, ids.size());
    assertTrue(ids.contains("internal-1"));
    assertTrue(ids.contains("internal-2"));
  }

  @DisplayName("given_xtmOneConfigured_should_routeThroughXtmOneAndUnwrapCopilotEnvelope")
  @Test
  void given_xtmOneConfigured_should_routeThroughXtmOneAndUnwrapCopilotEnvelope() {
    // Arrange — XTM One configured + ttp.extractor agent registered with the requested slug
    when(xtmOneConfig.isConfigured()).thenReturn(true);
    when(xtmOneService.resolveAgentSlugForIntent(anyString(), anyString()))
        .thenReturn("filigran-ttp-extractor");
    when(userService.currentUser()).thenReturn(stubUser());
    when(xtmOneClient.issueAuthenticationJwt(anyString(), anyString(), anyString()))
        .thenReturn("user-jwt");
    // Copilot envelope: {"files": [{"extraction": {"input": [{"predictions": {...}}]}}]}
    String copilotEnvelope =
        "{\"files\":[{\"extraction\":{\"input\":[{\"text\":\"chunk\","
            + "\"predictions\":{\"T1003\":0.9}}]}}]}";
    when(xtmOneClient.callAgentSync(anyString(), anyString(), anyString(), any()))
        .thenReturn(copilotEnvelope);

    AttackPattern ap = new AttackPattern();
    ap.setId("internal-xtm-1");
    ap.setExternalId("T1003");
    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(anyList(), anyString()))
        .thenReturn(List.of(ap));

    // Act
    List<String> ids =
        attackPatternService.searchAttackPatternWithTTPAIWebservice(
            List.of(), "Analyze this attack", "filigran-ttp-extractor");

    // Assert
    assertEquals(1, ids.size());
    assertTrue(ids.contains("internal-xtm-1"));
    // Per-user JWT path: callAgentSync(jwt, slug, content, files) — never the service-level
    // overload, which would attribute the request to the generic "system" user.
    verify(xtmOneClient).callAgentSync(anyString(), anyString(), anyString(), any());
    verify(xtmOneClient, never()).callAgentSyncAsService(anyString(), anyString(), any());
    // Legacy path must not be exercised when XTM One is configured
    verify(restTemplate, never()).postForEntity(anyString(), any(), any());
  }

  @DisplayName("given_xtmOneConfiguredAndNoCatalogYet_should_fallbackToDefaultSlugAndStillExtract")
  @Test
  void given_xtmOneConfiguredAndNoCatalogYet_should_fallbackToDefaultSlugAndStillExtract() {
    // Arrange — XTM One configured but the registration catalog hasn't populated yet
    when(xtmOneConfig.isConfigured()).thenReturn(true);
    when(xtmOneService.resolveAgentSlugForIntent(anyString(), any())).thenReturn(null);
    when(userService.currentUser()).thenReturn(stubUser());
    when(xtmOneClient.issueAuthenticationJwt(anyString(), anyString(), anyString()))
        .thenReturn("user-jwt");
    // Native (already-unwrapped) response shape
    String nativeResponse = "{\"input.txt\":[{\"predictions\":{\"T1059\":0.7}}]}";
    when(xtmOneClient.callAgentSync(anyString(), anyString(), anyString(), any()))
        .thenReturn(nativeResponse);

    AttackPattern ap = new AttackPattern();
    ap.setId("internal-xtm-2");
    ap.setExternalId("T1059");
    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(anyList(), anyString()))
        .thenReturn(List.of(ap));

    // Act
    List<String> ids =
        attackPatternService.searchAttackPatternWithTTPAIWebservice(List.of(), "context", null);

    // Assert — caller's null slug falls back to the default; ids resolved through repository
    assertEquals(1, ids.size());
    assertTrue(ids.contains("internal-xtm-2"));
    verify(xtmOneClient).callAgentSync(anyString(), anyString(), anyString(), any());
  }

  @DisplayName("given_xtmOneCallFails_should_throwServiceUnavailable")
  @Test
  void given_xtmOneCallFails_should_throwServiceUnavailable() {
    // Arrange
    when(xtmOneConfig.isConfigured()).thenReturn(true);
    when(xtmOneService.resolveAgentSlugForIntent(anyString(), any()))
        .thenReturn("filigran-ttp-extractor");
    when(userService.currentUser()).thenReturn(stubUser());
    when(xtmOneClient.issueAuthenticationJwt(anyString(), anyString(), anyString()))
        .thenReturn("user-jwt");
    when(xtmOneClient.callAgentSync(anyString(), anyString(), anyString(), any())).thenReturn(null);

    // Act / Assert
    org.springframework.web.server.ResponseStatusException ex =
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () ->
                attackPatternService.searchAttackPatternWithTTPAIWebservice(
                    List.of(), "context", null));
    assertEquals(503, ex.getStatusCode().value());
    // Generic message — must NOT propagate raw upstream details
    assertTrue(ex.getReason() == null || !ex.getReason().contains("@"));
  }

  @DisplayName("given_oversizedAiUpload_should_throwPayloadTooLarge")
  @Test
  void given_oversizedAiUpload_should_throwPayloadTooLarge() {
    // Arrange — 6 MB file is above the 5 MB AI-upload cap
    byte[] big = new byte[(int) (6L * 1024 * 1024)];
    org.springframework.web.multipart.MultipartFile huge =
        new MockMultipartFile("file", "big.pdf", "application/pdf", big);

    // Act / Assert
    org.springframework.web.server.ResponseStatusException ex =
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () ->
                attackPatternService.searchAttackPatternWithTTPAIWebservice(
                    List.of(huge), "context", null));
    assertEquals(413, ex.getStatusCode().value());
    // No XTM One / legacy call should happen if validation rejects the upload
    verify(xtmOneClient, never()).callAgentSync(anyString(), anyString(), anyString(), any());
    verify(xtmOneClient, never()).callAgentSyncAsService(anyString(), anyString(), any());
    verify(restTemplate, never()).postForEntity(anyString(), any(), any());
  }

  @DisplayName("given_missingExternalIds_should_throwElementNotFoundException")
  @Test
  void given_missingExternalIds_should_throwElementNotFoundException() {
    // Arrange
    AttackPattern found = new AttackPattern();
    found.setExternalId("T1003");
    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(anyList(), anyString()))
        .thenReturn(List.of(found));

    // Act / Assert
    assertThrows(
        ElementNotFoundException.class,
        () ->
            attackPatternService.getAttackPatternsByExternalIdsThrowIfMissing(
                Set.of("T1003", "T1059")));
  }

  @DisplayName("given_missingInternalIds_should_throwElementNotFoundException")
  @Test
  void given_missingInternalIds_should_throwElementNotFoundException() {
    // Arrange
    AttackPattern found = new AttackPattern();
    found.setId("id-1");
    when(attackPatternRepository.findAllById(anyList())).thenReturn(List.of(found));

    // Act / Assert
    assertThrows(
        ElementNotFoundException.class,
        () -> attackPatternService.findAllByInternalIdsThrowIfMissing(Set.of("id-1", "id-2")));
  }

  @DisplayName("given_existingExternalId_should_returnExistingAttackPattern")
  @Test
  void given_existingExternalId_should_returnExistingAttackPattern() {
    // Arrange
    AttackPattern existing = new AttackPattern();
    existing.setId("existing-id");

    AttackPatternCreateInput input = new AttackPatternCreateInput();
    input.setName("Credential Dumping");
    input.setDescription("desc");
    input.setExternalId("T1003");
    input.setPlatforms(new String[] {"Windows"});
    input.setPermissionsRequired(new String[] {"Administrator"});

    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(anyList(), anyString()))
        .thenReturn(List.of(existing));

    // Act
    AttackPattern result = attackPatternService.findOrCreate(input);

    // Assert
    assertSame(existing, result);
    verify(attackPatternRepository, never()).save(any());
  }

  @DisplayName("given_unknownExternalId_should_createAndSaveAttackPattern")
  @Test
  void given_unknownExternalId_should_createAndSaveAttackPattern() {
    // Arrange
    AttackPatternCreateInput input = new AttackPatternCreateInput();
    input.setName("Command and Scripting Interpreter");
    input.setDescription("desc");
    input.setStixId("attack-pattern--123");
    input.setExternalId("T1059");
    input.setPlatforms(new String[] {"Linux", "Windows"});
    input.setPermissionsRequired(new String[] {"User"});

    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(anyList(), anyString()))
        .thenReturn(new ArrayList<>());

    AttackPattern saved = new AttackPattern();
    saved.setId("saved-id");
    when(attackPatternRepository.save(any(AttackPattern.class))).thenReturn(saved);

    // Act
    AttackPattern result = attackPatternService.findOrCreate(input);

    // Assert
    ArgumentCaptor<AttackPattern> captor = ArgumentCaptor.forClass(AttackPattern.class);
    verify(attackPatternRepository).save(captor.capture());

    AttackPattern captured = captor.getValue();
    assertEquals("Command and Scripting Interpreter", captured.getName());
    assertEquals("desc", captured.getDescription());
    assertEquals("attack-pattern--123", captured.getStixId());
    assertEquals("T1059", captured.getExternalId());
    assertArrayEquals(new String[] {"Linux", "Windows"}, captured.getPlatforms());
    assertArrayEquals(new String[] {"User"}, captured.getPermissionsRequired());
    assertEquals(Tenant.DEFAULT_TENANT_UUID, captured.getTenant().getId());
    assertSame(saved, result);
  }
}
