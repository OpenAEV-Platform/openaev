package io.openaev.service;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectTestStatusRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.InjectTestSpecification;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.output.InjectTestStatusOutput;
import io.openaev.rest.inject.service.ScenarioInjectService;
import io.openaev.rest.inject.service.SimulationInjectService;
import io.openaev.service.utils.BulkOperationMonitor;
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
  private final ManagerFactory managerFactory;
  private final ConnectorInstanceRepository connectorInstanceRepository;
  private final BulkOperationMonitor bulkOperationMonitor;
  private final SimulationInjectService simulationInjectService;
  private final ScenarioInjectService scenarioInjectService;

  @Autowired
  public void setContext(ApplicationContext context) {
    this.context = context;
  }

  /**
   * Tests the inject only when it belongs to the simulation.
   *
   * @throws ElementNotFoundException when the id is unknown or the inject is not of the simulation
   */
  public InjectTestStatusOutput testInjectOfSimulation(String simulationId, String injectId) {
    return runTest(simulationInjectService.findInjectForSimulation(simulationId, injectId));
  }

  /**
   * Tests the inject only when it belongs to the scenario.
   *
   * @throws ElementNotFoundException when the id is unknown or the inject is not of the scenario
   */
  public InjectTestStatusOutput testInjectOfScenario(String scenarioId, String injectId) {
    return runTest(scenarioInjectService.findInjectForScenario(scenarioId, injectId));
  }

  /**
   * Bulk tests of injects. Tracked as a massive operation (header progress indicator): each inject
   * test performs a real injection execution, so per-inject progress is meaningful.
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
    String operationId = bulkOperationMonitor.start("test", "injects", searchResult.size());
    try {
      for (Inject inject : searchResult) {
        results.add(testInject(inject, user));
        bulkOperationMonitor.progress(operationId, 1);
      }
      bulkOperationMonitor.complete(operationId);
    } catch (RuntimeException e) {
      bulkOperationMonitor.fail(operationId);
      throw e;
    }
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

  /**
   * A test status carries no tenant of its own: it is answered only when its inject lies in the
   * request scope, which leaves the inject association empty otherwise.
   *
   * @throws ElementNotFoundException when the id is unknown or its inject is outside the scope
   */
  public InjectTestStatusOutput findInjectTestStatusById(String testId) {
    return injectStatusMapper.toInjectTestStatusOutput(
        injectTestStatusRepository
            .findById(testId)
            .filter(testStatus -> testStatus.getInject() != null)
            .orElseThrow(() -> testStatusNotFound(testId)));
  }

  /**
   * Deletes the test status only when its inject belongs to the simulation.
   *
   * @throws ElementNotFoundException when the id is unknown or its inject is not of the simulation
   */
  public void deleteInjectTestOfSimulation(String simulationId, String testId) {
    injectTestStatusRepository.delete(
        injectTestStatusRepository
            .findByIdAndInjectExerciseId(testId, simulationId)
            .orElseThrow(() -> testStatusNotFound(testId)));
  }

  /**
   * Deletes the test status only when its inject belongs to the scenario.
   *
   * @throws ElementNotFoundException when the id is unknown or its inject is not of the scenario
   */
  public void deleteInjectTestOfScenario(String scenarioId, String testId) {
    injectTestStatusRepository.delete(
        injectTestStatusRepository
            .findByIdAndInjectScenarioId(testId, scenarioId)
            .orElseThrow(() -> testStatusNotFound(testId)));
  }

  // -- PRIVATE --
  private InjectTestStatusOutput runTest(Inject inject) {
    if (!inject.getInjectTestable()) {
      throw new IllegalArgumentException("Inject: " + inject.getId() + " is not testable");
    }

    User user =
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    InjectTestStatus injectStatus = testInject(inject, user);
    return injectStatusMapper.toInjectTestStatusOutput(injectStatus);
  }

  private InjectTestStatus testInject(Inject inject, User user) {
    ExecutionContext userInjectContext =
        this.executionContextService.executionContext(user, inject, "Direct test");

    Injector injector;
    if (inject.getInjector() != null) {
      injector = inject.getInjector();
    } else {
      injector =
          inject
              .getInjectorContract()
              .map(contract -> contract.getFirstInjector())
              .orElseThrow(() -> new EntityNotFoundException("Threat arsenal item not found"));
    }
    io.openaev.executors.Injector executor;
    ConnectorInstancePersisted connectorInstancePersisted =
        connectorInstanceRepository.findById(injector.getId()).orElse(null);
    if (connectorInstancePersisted == null) {
      executor =
          managerFactory
              .getManager(inject.getTenant().getId())
              .requestInjectorExecutorByType(injector.getType());
    } else {
      executor =
          managerFactory
              .getManager(inject.getTenant().getId())
              .requestForInstance(connectorInstancePersisted, io.openaev.executors.Injector.class);
    }

    ExecutableInject injection =
        new ExecutableInject(
            false,
            true,
            inject,
            List.of(),
            inject.getAssets(),
            inject.getAssetGroups(),
            inject.getSecretReferences(),
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

  private static ElementNotFoundException testStatusNotFound(String testId) {
    return new ElementNotFoundException("Inject test status not found: " + testId);
  }
}
