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
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.SecurityCoverageUtils;
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

  @InjectMocks private AttackPatternService attackPatternService;

  @BeforeEach
  void beforeEach() {
    attackPatternService.mapper = new ObjectMapper();
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
