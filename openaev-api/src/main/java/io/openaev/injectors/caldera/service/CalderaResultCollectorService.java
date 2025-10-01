package io.openaev.injectors.caldera.service;

import io.openaev.database.model.*;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.InjectRepository;
import io.openaev.injectors.caldera.CalderaContract;
import io.openaev.injectors.caldera.model.ResultStatus;
import io.openaev.rest.inject.service.InjectStatusService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CalderaResultCollectorService implements Runnable {
  private final int EXPIRATION_TIME = 900;

  private final InjectRepository injectRepository;
  private final CalderaInjectorService calderaService;
  private final InjectStatusService injectStatusService;

  @Autowired
  public CalderaResultCollectorService(
      InjectRepository injectRepository,
      CalderaInjectorService calderaService,
      InjectStatusService injectStatusService) {
    this.injectRepository = injectRepository;
    this.calderaService = calderaService;
    this.injectStatusService = injectStatusService;
  }

  @Override
  @Transactional
  public void run() {
    // Retrieve Caldera inject not done
    List<InjectStatus> injectStatuses =
        this.injectStatusService.findPendingInjectStatusByType(CalderaContract.TYPE);
    // For each one ask for traces and status
    injectStatuses.forEach(
        (injectStatus -> {
          log.info("Found inject status: {}", injectStatus.getId());
          Map<String, Agent> linksMap = injectStatus.getStatusMapIdentifierAgent();

          log.info("Found links IDs: {}", linksMap.keySet());
          ResultStatus resultStatus = new ResultStatus();
          for (Map.Entry<String, Agent> entry : linksMap.entrySet()) {
            try {
              log.info("Trying to get result for {}", entry.getKey());
              resultStatus = this.calderaService.results(entry.getKey());
            } catch (Exception e) {
              injectStatus.addMayBePreventedTrace(
                  "Cannot get result for linkID " + entry.getKey() + ", injection has failed",
                  ExecutionTraceAction.COMPLETE,
                  entry.getValue());
              log.info("Cannot get result for linkID {}, injection has failed", entry.getKey());
            }

            if (resultStatus.getPaw() == null
                && injectStatus
                    .getTrackingSentDate()
                    .isBefore(Instant.now().minus(EXPIRATION_TIME / 60, ChronoUnit.MINUTES))) {
              injectStatus.addMayBePreventedTrace(
                  "Cannot get result for linkID " + entry.getKey() + ", injection has failed",
                  ExecutionTraceAction.COMPLETE,
                  entry.getValue());
              log.info("Cannot get result for linkID {}, injection has failed", entry.getKey());

            } else if (resultStatus.getPaw() != null
                && resultStatus.isComplete()
                && resultStatus.isFail()) {
              injectStatus.addTrace(
                  new ExecutionTrace(
                      injectStatus,
                      ExecutionTraceStatus.MAYBE_PREVENTED,
                      List.of(),
                      "Failed result for linkID "
                          + entry.getKey()
                          + " ("
                          + resultStatus.getContent()
                          + ")",
                      ExecutionTraceAction.COMPLETE,
                      entry.getValue(),
                      resultStatus.getFinish()));

            } else if (resultStatus.getPaw() != null
                && resultStatus.isComplete()
                && !resultStatus.isFail()) {
              injectStatus.addTrace(
                  new ExecutionTrace(
                      injectStatus,
                      ExecutionTraceStatus.SUCCESS,
                      List.of(),
                      "Success result for linkID "
                          + entry.getKey()
                          + " ("
                          + resultStatus.getContent()
                          + ")",
                      ExecutionTraceAction.COMPLETE,
                      entry.getValue(),
                      resultStatus.getFinish()));

            } else if (resultStatus.getPaw() != null
                && !resultStatus.isComplete()
                && injectStatus
                    .getTrackingSentDate()
                    .isBefore(Instant.now().minus(5L, ChronoUnit.MINUTES))) {

              injectStatus.addTrace(
                  new ExecutionTrace(
                      injectStatus,
                      ExecutionTraceStatus.MAYBE_PREVENTED,
                      List.of(),
                      "Timeout on linkID " + entry.getKey() + ", injection has failed",
                      ExecutionTraceAction.COMPLETE,
                      entry.getValue(),
                      resultStatus.getFinish()));

              log.info("Timeout on linkID {}, injection has failed", entry.getKey());
            }
          }

          Inject relatedInject = injectStatus.getInject();
          if (injectStatusService.isAllInjectAgentsExecuted(relatedInject)) {
            injectStatusService.updateFinalInjectStatus(injectStatus);
          }

          injectRepository.save(relatedInject);
        }));
  }
}
