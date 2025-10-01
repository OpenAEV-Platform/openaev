package io.openaev.injectors.lade.service;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.injectors.lade.LadeContract;
import io.openaev.injectors.lade.model.LadeWorkflow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LadeListener {

  private final InjectRepository injectRepository;
  private final InjectStatusRepository injectStatusRepository;
  private final LadeService ladeService;

  @Scheduled(fixedDelay = 15000, initialDelay = 0)
  public void listenWorkflows() {
    // Get all lade inject with workflow_id that are not done yet
    List<InjectStatus> injectStatuses =
        this.injectStatusRepository.pendingForInjectType(LadeContract.TYPE);
    // For each workflow ask for traces and status
    injectStatuses.forEach(
        injectStatus -> {
          // Add traces and close inject if needed.
          String asyncId =
              injectStatus.statusIdentifiers().stream()
                  .findFirst()
                  .orElse(null); // Lade handle only one asyncID for now
          try {
            LadeWorkflow workflowStatus = this.ladeService.getWorkflowStatus(asyncId);
            if (workflowStatus.isDone()) {
              ExecutionStatus name =
                  workflowStatus.isFail() ? ExecutionStatus.ERROR : ExecutionStatus.SUCCESS;
              injectStatus.setName(name);
              injectStatus.setTrackingEndDate(workflowStatus.getStopTime());
            }
            injectStatus.setTraces(workflowStatus.getTraces());
            // Update related inject
            Inject relatedInject = injectStatus.getInject();
            relatedInject.setUpdatedAt(Instant.now());
            this.injectRepository.save(relatedInject);
          } catch (Exception e) {
            log.error(e.getMessage(), e);
          }
        });
  }
}
