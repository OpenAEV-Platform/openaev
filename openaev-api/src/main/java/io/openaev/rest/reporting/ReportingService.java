package io.openaev.rest.reporting;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.Document;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.ReportingGenerationTrigger;
import io.openaev.database.model.ReportingSchedule;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.database.repository.ReportingRepository;
import io.openaev.database.repository.ReportingScheduleRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.reporting.form.ReportingScheduleInput;
import io.openaev.rest.reporting.service.ReportingRenderer;
import io.openaev.service.GrantService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

  /**
   * RBAC resource type of each reporting subject. PLATFORM is absent on purpose: platform-wide
   * reportings have no subject entity, the REPORTINGS capabilities (checked by the API layer) are
   * their only gate.
   */
  private static final Map<ReportingContextType, ResourceType> SUBJECT_RESOURCE_TYPES =
      Map.of(
          ReportingContextType.SIMULATION, ResourceType.SIMULATION,
          ReportingContextType.SCENARIO, ResourceType.SCENARIO,
          ReportingContextType.ATOMIC_TESTING, ResourceType.ATOMIC_TESTING,
          ReportingContextType.ENDPOINT, ResourceType.ASSET,
          ReportingContextType.ASSET_GROUP, ResourceType.ASSET_GROUP,
          ReportingContextType.PLAYER, ResourceType.PLAYER,
          ReportingContextType.TEAM, ResourceType.TEAM);

  /** Subject types whose visibility can come from per-resource grants (scenario-like RBAC). */
  private static final EnumSet<ReportingContextType> GRANT_MANAGED_CONTEXT_TYPES =
      EnumSet.of(
          ReportingContextType.SIMULATION,
          ReportingContextType.SCENARIO,
          ReportingContextType.ATOMIC_TESTING);

  private final ReportingRepository reportingRepository;
  private final ReportingGenerationRepository reportingGenerationRepository;
  private final ReportingScheduleRepository reportingScheduleRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final DocumentService documentService;
  private final ReportingRenderer reportingRenderer;
  private final PermissionService permissionService;
  private final GrantService grantService;

  // -- SEARCH --

  /**
   * Retrieves a paginated list of {@link Reporting} entities according to the provided pagination
   * input.
   *
   * @param searchPaginationInput the pagination and filtering input
   * @return a {@link Page} of {@link Reporting} entities
   */
  @Transactional(readOnly = true)
  public Page<Reporting> reportings(@NotNull final SearchPaginationInput searchPaginationInput) {
    // RBAC: never list a reporting whose subject the user cannot read (a scenario reporting
    // must stay invisible to users without access to that scenario).
    Specification<Reporting> accessSpecification =
        subjectAccessSpecification(this.userService.currentUser());
    return buildPaginationJPA(
        (specification, pageable) ->
            this.reportingRepository.findAll(accessSpecification.and(specification), pageable),
        searchPaginationInput,
        Reporting.class);
  }

  /**
   * Lists the reportings built around a given subject, most recently updated first.
   *
   * @param contextType the subject type
   * @param contextId the subject id; null for PLATFORM-wide reportings
   * @return the matching {@link Reporting} entities
   */
  @Transactional(readOnly = true)
  public List<Reporting> reportingsByContext(
      @NotNull final ReportingContextType contextType, final String contextId) {
    checkSubjectAccess(contextType, contextId);
    if (contextId == null || contextId.isBlank()) {
      return this.reportingRepository.findAllByContextTypeAndContextIdIsNullOrderByUpdatedAtDesc(
          contextType);
    }
    return this.reportingRepository.findAllByContextTypeAndContextIdOrderByUpdatedAtDesc(
        contextType, contextId);
  }

  // -- CREATE --

  /**
   * Creates a new {@link Reporting} template; the tenant is set automatically by the tenant
   * listener.
   *
   * @param reporting the {@link Reporting} to save
   * @return the saved {@link Reporting}
   */
  @Transactional
  public Reporting createReporting(@NotNull final Reporting reporting) {
    checkSubjectAccess(reporting.getContextType(), reporting.getContextId());
    return this.reportingRepository.save(reporting);
  }

  // -- READ --

  /**
   * Retrieves a single {@link Reporting} by id within the current tenant.
   *
   * @param id the reporting id
   * @return the {@link Reporting}
   * @throws ElementNotFoundException if no reporting is found with the given id
   */
  @Transactional(readOnly = true)
  public Reporting reporting(@NotBlank final String id) {
    Reporting reporting = resolveReporting(id);
    checkSubjectAccess(reporting.getContextType(), reporting.getContextId());
    return reporting;
  }

  // -- UPDATE --

  /**
   * Updates an existing {@link Reporting} template.
   *
   * @param reporting the {@link Reporting} to update
   * @return the updated {@link Reporting}
   */
  @Transactional
  public Reporting updateReporting(@NotNull final Reporting reporting) {
    // The context may have been repointed by the update input: the NEW subject must be
    // readable too (the previous one was checked when the reporting was resolved).
    checkSubjectAccess(reporting.getContextType(), reporting.getContextId());
    return this.reportingRepository.save(reporting);
  }

  // -- DELETE --

  /**
   * Deletes a {@link Reporting} template with its generations and schedules (cascade).
   *
   * @param id the reporting id
   * @throws ElementNotFoundException if no reporting is found with the given id
   */
  @Transactional
  public void deleteReporting(@NotBlank final String id) {
    Reporting reporting = resolveReporting(id);
    checkSubjectAccess(reporting.getContextType(), reporting.getContextId());
    this.reportingRepository.delete(reporting);
  }

  // -- GENERATIONS --

  /**
   * Persists a PENDING {@link ReportingGeneration} for the given reporting and hands it to the
   * {@link ReportingRenderer} under the current user's identity. The rendering engine is
   * asynchronous by contract (the real renderer dispatches the browser work after commit); the
   * returned generation reflects the state after the renderer dispatch.
   *
   * @param reportingId the reporting to generate
   * @param format the requested output format; falls back to the reporting default when null
   * @param trigger what initiated the generation (manual or scheduled)
   * @return the persisted {@link ReportingGeneration}
   */
  @Transactional
  public ReportingGeneration requestGeneration(
      @NotBlank final String reportingId,
      final ReportingFormat format,
      @NotNull final ReportingGenerationTrigger trigger) {
    return requestGeneration(reportingId, format, trigger, this.userService.currentUserOrNull());
  }

  /**
   * Same as {@link #requestGeneration(String, ReportingFormat, ReportingGenerationTrigger)} but
   * with an explicit acting user; scheduled executions must pass the schedule owner since they run
   * outside an authenticated request.
   *
   * @param reportingId the reporting to generate
   * @param format the requested output format; falls back to the reporting default when null
   * @param trigger what initiated the generation (manual or scheduled)
   * @param actingUser the identity the render runs under
   * @return the persisted {@link ReportingGeneration}
   */
  @Transactional
  public ReportingGeneration requestGeneration(
      @NotBlank final String reportingId,
      final ReportingFormat format,
      @NotNull final ReportingGenerationTrigger trigger,
      final User actingUser) {
    Reporting reporting = resolveReporting(reportingId);
    // The render runs under the acting user's identity (their token authenticates every data
    // fetch), so the subject must be readable by that user - both for manual generations and
    // for scheduled ones (owner identity), including owners whose access was revoked since.
    if (actingUser != null) {
      checkSubjectAccess(reporting.getContextType(), reporting.getContextId(), actingUser);
    }
    ReportingGeneration generation = new ReportingGeneration();
    generation.setReporting(reporting);
    generation.setFormat(format != null ? format : reporting.getDefaultFormat());
    generation.setGenerationTrigger(trigger);
    generation.setStatus(ReportingGenerationStatus.PENDING);
    ReportingGeneration saved = this.reportingGenerationRepository.save(generation);
    this.reportingRenderer.render(saved, actingUser);
    return saved;
  }

  /**
   * Lists the generations of a reporting, most recent first.
   *
   * @param reportingId the reporting id
   * @return the {@link ReportingGeneration} entities
   */
  @Transactional(readOnly = true)
  public List<ReportingGeneration> generations(@NotBlank final String reportingId) {
    Reporting reporting = resolveReporting(reportingId);
    checkSubjectAccess(reporting.getContextType(), reporting.getContextId());
    return this.reportingGenerationRepository.findAllByReportingIdOrderByCreatedAtDesc(reportingId);
  }

  /**
   * Retrieves a single {@link ReportingGeneration} by id within the current tenant.
   *
   * @param generationId the generation id
   * @return the {@link ReportingGeneration}
   * @throws ElementNotFoundException if no generation is found with the given id
   */
  @Transactional(readOnly = true)
  public ReportingGeneration generation(@NotBlank final String generationId) {
    ReportingGeneration generation = resolveGeneration(generationId);
    checkSubjectAccess(
        generation.getReporting().getContextType(), generation.getReporting().getContextId());
    return generation;
  }

  /**
   * Deletes a {@link ReportingGeneration} together with its underlying document.
   *
   * @param generationId the generation id
   * @throws ElementNotFoundException if no generation is found with the given id
   */
  @Transactional
  public void deleteGeneration(@NotBlank final String generationId) {
    ReportingGeneration generation = resolveGeneration(generationId);
    checkSubjectAccess(
        generation.getReporting().getContextType(), generation.getReporting().getContextId());
    Document document = generation.getDocument();
    this.reportingGenerationRepository.delete(generation);
    if (document != null) {
      // NOT the generic deleteDocument: report outputs are read-only on the documents surface,
      // its guard would reject the document and, thrown inside this transaction, mark it
      // rollback-only (UnexpectedRollbackException at commit). This dedicated path deletes the
      // row and best-effort removes the stored file.
      this.documentService.deleteReportingGenerationOutput(document.getId());
    }
  }

  /**
   * Returns the stored {@link Document} of a successful generation, for download streaming.
   *
   * @param generationId the generation id
   * @return the produced {@link Document}
   * @throws BadRequestException if the generation is not in SUCCESS status
   * @throws ElementNotFoundException if the generation or its document is missing
   */
  @Transactional(readOnly = true)
  public Document generationDocument(@NotBlank final String generationId) {
    ReportingGeneration generation = resolveGeneration(generationId);
    // The produced document contains the subject's actual data: downloading it requires read
    // access to the subject, exactly like reading the reporting itself.
    checkSubjectAccess(
        generation.getReporting().getContextType(), generation.getReporting().getContextId());
    if (!ReportingGenerationStatus.SUCCESS.equals(generation.getStatus())) {
      throw new BadRequestException("Generation is not successful: " + generationId);
    }
    Document document = generation.getDocument();
    if (document == null) {
      throw new ElementNotFoundException("Generation has no document: " + generationId);
    }
    return document;
  }

  // -- SCHEDULES --

  /**
   * Creates a {@link ReportingSchedule} on a reporting, owned by the current user.
   *
   * @param reportingId the reporting to schedule
   * @param input the schedule definition
   * @return the saved {@link ReportingSchedule}
   */
  @Transactional
  public ReportingSchedule createSchedule(
      @NotBlank final String reportingId, @NotNull final ReportingScheduleInput input) {
    Reporting reporting = resolveReporting(reportingId);
    checkSubjectAccess(reporting.getContextType(), reporting.getContextId());
    ReportingSchedule schedule = new ReportingSchedule();
    schedule.setReporting(reporting);
    schedule.setOwner(this.userService.currentUser());
    applyScheduleInput(schedule, input);
    return this.reportingScheduleRepository.save(schedule);
  }

  /**
   * Updates a {@link ReportingSchedule} of a reporting.
   *
   * @param reportingId the parent reporting id
   * @param scheduleId the schedule id
   * @param input the new schedule definition
   * @return the updated {@link ReportingSchedule}
   * @throws ElementNotFoundException if the schedule does not exist or belongs to another reporting
   */
  @Transactional
  public ReportingSchedule updateSchedule(
      @NotBlank final String reportingId,
      @NotBlank final String scheduleId,
      @NotNull final ReportingScheduleInput input) {
    ReportingSchedule schedule = resolveSchedule(reportingId, scheduleId);
    checkSubjectAccess(
        schedule.getReporting().getContextType(), schedule.getReporting().getContextId());
    applyScheduleInput(schedule, input);
    return this.reportingScheduleRepository.save(schedule);
  }

  /**
   * Deletes a {@link ReportingSchedule} of a reporting.
   *
   * @param reportingId the parent reporting id
   * @param scheduleId the schedule id
   * @throws ElementNotFoundException if the schedule does not exist or belongs to another reporting
   */
  @Transactional
  public void deleteSchedule(
      @NotBlank final String reportingId, @NotBlank final String scheduleId) {
    ReportingSchedule schedule = resolveSchedule(reportingId, scheduleId);
    checkSubjectAccess(
        schedule.getReporting().getContextType(), schedule.getReporting().getContextId());
    this.reportingScheduleRepository.delete(schedule);
  }

  // -- INTERNAL: SUBJECT RBAC --

  /**
   * Enforces that the current user can read the subject a reporting is built around: a user without
   * access to a scenario must never see (or generate, or download) a reporting about that scenario.
   * Platform-wide reportings (no subject) are gated by the REPORTINGS capabilities alone, which the
   * API layer already verified.
   *
   * @param contextType the reporting subject type
   * @param contextId the reporting subject id; null/blank for platform-wide reportings
   * @throws AccessDeniedException when the subject is not readable by the current user
   */
  private void checkSubjectAccess(final ReportingContextType contextType, final String contextId) {
    checkSubjectAccess(contextType, contextId, this.userService.currentUser());
  }

  /**
   * Same as {@link #checkSubjectAccess(ReportingContextType, String)} for an explicit user
   * (scheduled generations run under the schedule owner's identity).
   */
  private void checkSubjectAccess(
      final ReportingContextType contextType, final String contextId, @NotNull final User user) {
    ResourceType subjectType = contextType == null ? null : SUBJECT_RESOURCE_TYPES.get(contextType);
    if (subjectType == null || !StringUtils.hasText(contextId)) {
      // PLATFORM reporting (or subject-less legacy row): capability gate only.
      return;
    }
    boolean allowed =
        this.permissionService.hasPermission(
            user, Optional.empty(), contextId, subjectType, Action.READ);
    if (!allowed) {
      throw new AccessDeniedException(
          "Access denied to the subject of this reporting: " + contextType + " " + contextId);
    }
  }

  /**
   * Search-time counterpart of {@link #checkSubjectAccess(ReportingContextType, String)}: a JPA
   * specification keeping only the reportings whose subject the user can read. Mirrors the platform
   * RBAC model: admin/BYPASS see everything; capability holders see every reporting of the covered
   * subject type; open subject types (players, teams) are visible to all; grant-based subject types
   * (scenario, simulation, atomic testing) fall back to the user's read grants.
   */
  private Specification<Reporting> subjectAccessSpecification(@NotNull final User user) {
    if (user.isAdminOrBypass()) {
      return (root, query, cb) -> cb.conjunction();
    }
    List<ReportingContextType> visibleTypes = new ArrayList<>();
    List<ReportingContextType> grantFilteredTypes = new ArrayList<>();
    for (Map.Entry<ReportingContextType, ResourceType> entry : SUBJECT_RESOURCE_TYPES.entrySet()) {
      ReportingContextType contextType = entry.getKey();
      ResourceType subjectType = entry.getValue();
      if (PermissionService.isOpenResource(subjectType, Action.READ)
          || this.permissionService.hasCapabilityPermission(user, subjectType, Action.READ)) {
        visibleTypes.add(contextType);
      } else if (GRANT_MANAGED_CONTEXT_TYPES.contains(contextType)) {
        grantFilteredTypes.add(contextType);
      }
    }
    List<String> grantedIds =
        grantFilteredTypes.isEmpty()
            ? List.of()
            : this.grantService.findReadGrantedResourceIds(user);
    return (root, query, cb) -> {
      List<Predicate> anyOf = new ArrayList<>();
      // Platform-wide reportings carry no subject: the capability gate suffices.
      anyOf.add(cb.isNull(root.get("contextId")));
      if (!visibleTypes.isEmpty()) {
        anyOf.add(root.get("contextType").in(visibleTypes));
      }
      if (!grantFilteredTypes.isEmpty() && !grantedIds.isEmpty()) {
        anyOf.add(
            cb.and(
                root.get("contextType").in(grantFilteredTypes),
                root.get("contextId").in(grantedIds)));
      }
      return cb.or(anyOf.toArray(new Predicate[0]));
    };
  }

  // -- INTERNAL --

  private Reporting resolveReporting(final String id) {
    return this.reportingRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .orElseThrow(() -> new ElementNotFoundException("Reporting not found with id: " + id));
  }

  private ReportingGeneration resolveGeneration(final String id) {
    return this.reportingGenerationRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .orElseThrow(
            () -> new ElementNotFoundException("Reporting generation not found with id: " + id));
  }

  private ReportingSchedule resolveSchedule(final String reportingId, final String scheduleId) {
    ReportingSchedule schedule =
        this.reportingScheduleRepository
            .findByIdAndTenantId(scheduleId, TenantContext.getCurrentTenant())
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Reporting schedule not found with id: " + scheduleId));
    if (!schedule.getReporting().getId().equals(reportingId)) {
      throw new ElementNotFoundException(
          "Reporting schedule " + scheduleId + " does not belong to reporting " + reportingId);
    }
    return schedule;
  }

  private void applyScheduleInput(
      final ReportingSchedule schedule, final ReportingScheduleInput input) {
    schedule.setName(input.getName());
    schedule.setPeriod(input.getPeriod());
    schedule.setTriggerTime(input.getTriggerTime());
    if (input.getFormat() != null) {
      schedule.setFormat(input.getFormat());
    }
    if (input.getEnabled() != null) {
      schedule.setEnabled(input.getEnabled());
    }
    if (input.getRecipientUserIds() != null) {
      schedule.setRecipientUsers(
          new ArrayList<>(
              fromIterable(this.userRepository.findAllById(input.getRecipientUserIds()))));
    }
    if (input.getRecipientEmails() != null) {
      schedule.setRecipientEmails(new ArrayList<>(input.getRecipientEmails()));
    }
  }
}
