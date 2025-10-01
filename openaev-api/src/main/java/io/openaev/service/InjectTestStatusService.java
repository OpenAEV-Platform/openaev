package io.openaev.service;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectTestStatusRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.InjectTestSpecification;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.executors.Injector;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.output.InjectTestStatusOutput;
import io.openaev.utils.mapper.InjectStatusMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InjectTestStatusService {

  private ApplicationContext context;
  private final UserRepository userRepository;
  private final InjectRepository injectRepository;
  private final ExecutionContextService executionContextService;
  private final InjectTestStatusRepository injectTestStatusRepository;
  private final InjectStatusMapper injectStatusMapper;

  @Autowired
  public void setContext(ApplicationContext context) {
    this.context = context;
  }

  public InjectTestStatusOutput testInject(String injectId) {
    Inject inject =
        this.injectRepository
            .findById(injectId)
            .orElseThrow(() -> new EntityNotFoundException("Inject not found"));

    if (!inject.getInjectTestable()) {
      throw new IllegalArgumentException("Inject: " + injectId + " is not testable");
    }

    User user =
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    InjectTestStatus injectStatus = testInject(inject, user);
    return injectStatusMapper.toInjectTestStatusOutput(injectStatus);
  }

  /**
   * Bulk tests of injects
   *
   * @param searchSpecifications the criteria to search injects to test
   * @return the list of inject test status
   */
  public List<InjectTestStatusOutput> bulkTestInjects(
      final Specification<Inject> searchSpecifications) {
    List<Inject> searchResult = this.injectRepository.findAll(searchSpecifications);
    if (searchResult.isEmpty()) {
      throw new BadRequestException("No inject ID is testable");
    }
    User user =
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    List<InjectTestStatus> results = new ArrayList<>();
    searchResult.forEach(inject -> results.add(testInject(inject, user)));
    return results.stream().map(injectStatusMapper::toInjectTestStatusOutput).toList();
  }

  public Page<InjectTestStatusOutput> findAllInjectTestsByExerciseId(
      String exerciseId, SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            (Specification<InjectTestStatus> specification, Pageable pageable) ->
                injectTestStatusRepository.findAll(
                    InjectTestSpecification.findInjectTestInExercise(exerciseId).and(specification),
                    pageable),
            searchPaginationInput,
            InjectTestStatus.class)
        .map(injectStatusMapper::toInjectTestStatusOutput);
  }

  public Page<InjectTestStatusOutput> findAllInjectTestsByScenarioId(
      String scenarioId, SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            (Specification<InjectTestStatus> specification, Pageable pageable) ->
                injectTestStatusRepository.findAll(
                    InjectTestSpecification.findInjectTestInScenario(scenarioId).and(specification),
                    pageable),
            searchPaginationInput,
            InjectTestStatus.class)
        .map(injectStatusMapper::toInjectTestStatusOutput);
  }

  public InjectTestStatusOutput findInjectTestStatusById(String testId) {
    return injectStatusMapper.toInjectTestStatusOutput(
        injectTestStatusRepository.findById(testId).orElseThrow());
  }

  // -- PRIVATE --

  private InjectTestStatus testInject(Inject inject, User user) {
    ExecutionContext userInjectContext =
        this.executionContextService.executionContext(user, inject, "Direct test");

    Injector executor =
        context.getBean(
            inject
                .getInjectorContract()
                .map(contract -> contract.getInjector().getType())
                .orElseThrow(() -> new EntityNotFoundException("Injector contract not found")),
            Injector.class);

    ExecutableInject injection =
        new ExecutableInject(
            false,
            true,
            inject,
            List.of(),
            inject.getAssets(),
            inject.getAssetGroups(),
            List.of(userInjectContext));
    Execution execution = executor.executeInjection(injection);

    InjectTestStatus injectTestStatus =
        this.injectTestStatusRepository
            .findByInject(inject)
            .map(
                existingStatus -> {
                  InjectTestStatus updatedStatus = InjectTestStatus.fromExecutionTest(execution);
                  updatedStatus.setId(existingStatus.getId());
                  updatedStatus.setTestCreationDate(existingStatus.getTestCreationDate());
                  updatedStatus.setInject(inject);
                  return updatedStatus;
                })
            .orElseGet(
                () -> {
                  InjectTestStatus newStatus = InjectTestStatus.fromExecutionTest(execution);
                  newStatus.setInject(inject);
                  return newStatus;
                });

    return this.injectTestStatusRepository.save(injectTestStatus);
  }

  public void deleteInjectTest(String testId) {
    injectTestStatusRepository.deleteById(testId);
  }
}
