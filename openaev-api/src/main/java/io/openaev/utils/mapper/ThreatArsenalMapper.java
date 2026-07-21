package io.openaev.utils.mapper;

import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionFullOutput;
import io.openaev.database.model.*;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ThreatArsenalMapper {

  private final PayloadMapper payloadMapper;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final EntityManager entityManager;

  /**
   * Resolves an author association that may be an uninitialized proxy detached from its session
   * (duplicate/update flows copy entity graphs across persistence contexts). A live proxy is
   * initialized lazily on access; a dead one is reloaded through the current session so reading its
   * display fields never throws {@code LazyInitializationException}.
   */
  private <T> T resolveAuthor(Class<T> type, T association) {
    if (association instanceof HibernateProxy proxy) {
      LazyInitializer initializer = proxy.getHibernateLazyInitializer();
      if (initializer.isUninitialized()
          && (initializer.getSession() == null || initializer.getSession().isClosed())) {
        return entityManager.find(type, initializer.getIdentifier());
      }
    }
    return association;
  }

  /**
   * Convert an injectorContract to a ThreatArsenalAction
   *
   * @param injectorContract the injectorContract to convert
   * @return the threat arsenal action DTO
   */
  public ThreatArsenalAction toThreatArsenalAction(InjectorContract injectorContract) {
    if (injectorContract == null) {
      return null;
    }
    // Resolve the polymorphic author (contract-level first, then payload-level,
    // via the entity getters) so a freshly created action already carries its
    // author. Name resolution mirrors the search projection: user -> email,
    // team/organization -> name. Left null for authorless built-in content
    // (the UI presents those as authored by Filigran).
    String authorId = null;
    String authorName = null;
    String authorType = null;
    User authorUser = resolveAuthor(User.class, injectorContract.getPayloadAuthorUser());
    Team authorTeam = resolveAuthor(Team.class, injectorContract.getPayloadAuthorTeam());
    Organization authorOrganization =
        resolveAuthor(Organization.class, injectorContract.getPayloadAuthorOrganization());
    if (authorUser != null) {
      authorId = authorUser.getId();
      authorName = authorUser.getEmail();
      authorType = "user";
    } else if (authorTeam != null) {
      authorId = authorTeam.getId();
      authorName = authorTeam.getName();
      authorType = "team";
    } else if (authorOrganization != null) {
      authorId = authorOrganization.getId();
      authorName = authorOrganization.getName();
      authorType = "organization";
    }
    return ThreatArsenalAction.builder()
        .id(injectorContract.getId())
        .labels(injectorContract.getLabels())
        .injectorType(injectorContract.getInjectorType())
        .domains(
            injectorContract.getDomains().stream().map(d -> d.getId()).collect(Collectors.toSet()))
        .platforms(injectorContract.getPlatforms())
        .tags(injectorContract.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .updatedAt(injectorContract.getUpdatedAt())
        .payload(payloadMapper.toPayloadSimple(Optional.ofNullable(injectorContract.getPayload())))
        .authorId(authorId)
        .authorName(authorName)
        .authorType(authorType)
        .build();
  }

  public ThreatArsenalActionFullOutput toThreatArsenalActionFullOutput(
      InjectorContract injectorContract) {
    return new ThreatArsenalActionFullOutput(
        injectorContract.getId(),
        injectorContract.getInjectorType(),
        injectorContract.getLabels(),
        null,
        injectorContract.getPlatforms(),
        null,
        null,
        null,
        null,
        injectorContract.getExternalId(),
        null,
        injectorContractContentUtils.getPredefinedExpectations(injectorContract),
        injectorContractContentUtils.getPredefinedExpectedSecurityPlatforms(injectorContract),
        null,
        null,
        null,
        null,
        null,
        injectorContract.getTags().stream().map(Tag::getId).toList(),
        injectorContract.getDomains().stream().map(Domain::getId).toList(),
        injectorContract.getAttackPatterns().stream().map(AttackPattern::getId).toList(),
        null,
        null,
        null,
        null,
        null,
        injectorContract.getCreatedAt(),
        injectorContract.getUpdatedAt());
  }

  /**
   * Converts a {@link Payload} entity and its related IDs into a full-detail {@link
   * ThreatArsenalActionFullOutput} record.
   *
   * @param payload the payload entity to convert
   * @param attackPatternIds attack-pattern IDs linked through the injector contract
   * @param domainIds domain IDs linked through the injector contract
   * @param tagIds tag IDs linked through the injector contract
   * @return the fully populated action output DTO
   */
  public ThreatArsenalActionFullOutput toThreatArsenalActionFullOutput(
      @NotNull Payload payload,
      @NotNull String injectorContractId,
      @NotNull Map<String, String> labels,
      List<String> attackPatternIds,
      List<String> domainIds,
      List<String> tagIds) {

    String commandExecutor = null;
    String commandContent = null;
    String dnsResolutionHostname = null;
    String fileDropFile = null;
    String executableFile = null;

    Payload unproxied = (Payload) Hibernate.unproxy(payload);
    if (unproxied instanceof Command command) {
      commandExecutor = command.getExecutor();
      commandContent = command.getContent();
    } else if (unproxied instanceof DnsResolution dnsResolution) {
      dnsResolutionHostname = dnsResolution.getHostname();
    } else if (unproxied instanceof FileDrop fileDrop && fileDrop.getFileDropFile() != null) {
      fileDropFile = fileDrop.getFileDropFile().getId();
    } else if (unproxied instanceof Executable executable
        && executable.getExecutableFile() != null) {
      executableFile = executable.getExecutableFile().getId();
    }

    return new ThreatArsenalActionFullOutput(
        injectorContractId,
        payload.getType(),
        labels,
        payload.getDescription(),
        payload.getPlatforms(),
        payload.getCleanupExecutor(),
        payload.getCleanupCommand(),
        payload.getArguments(),
        payload.getPrerequisites(),
        payload.getExternalId(),
        payload.getSource(),
        payload.getExpectations(),
        payload.getExpectedSecurityPlatforms(),
        payload.getStatus(),
        payload.getExecutionArch(),
        payload.getCollectorTypeValue(),
        payload.getDetectionRemediations(),
        payload.getOutputParsers(),
        tagIds,
        domainIds,
        attackPatternIds,
        commandExecutor,
        commandContent,
        dnsResolutionHostname,
        fileDropFile,
        executableFile,
        payload.getCreatedAt(),
        payload.getUpdatedAt());
  }
}
