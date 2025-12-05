package io.openaev.service.chaining;

import io.openaev.database.model.WORKFLOW_STATUS;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class WorkflowService {
private final WorkflowRepository workflowRepository;

public void creationWorkflow(Workflow workflow) {
    workflowRepository.save(workflow);
}

public void updateWorkflowTemplate(String workflowId) {
    Workflow workflow = workflowRepository.findById(workflowId).orElseThrow();
    workflow.setEdited(true);
    workflowRepository.save(workflow);
}

    public Workflow launchWorkflowTemplateBySimulation(String simulationId) {
        // 1 WORKFLOW TEMPLATE / SIMULATION
        Workflow workflowTemplate = workflowRepository.findBySimulationIdAndStatus(simulationId, WORKFLOW_STATUS.TEMPLATE);
        if (workflowTemplate == null) return null;//todo exception not find
        return this.launchWorkflow(workflowTemplate);
    }

    private Workflow launchWorkflow(Workflow workflowTemplate){
        if(workflowTemplate.isEdited()){
            workflowTemplate.setEdited(false);
            int version = workflowTemplate.getVersion();
            workflowTemplate.setVersion(++version);
            workflowTemplate = workflowRepository.save(workflowTemplate);
        }

        //COPY WORKFLOW Template to Workflow execution
        return Workflow.builder()
                .isEdited(false)
                .status(WORKFLOW_STATUS.RUN)
                .simulationId(workflowTemplate.getSimulationId())
                .version(workflowTemplate.getVersion())
                .build();
    }

public void launchWorkflowTemplate(String workflowId) {
    Workflow workflow = workflowRepository.findById(workflowId).orElseThrow();
    workflow.setEdited(false);
    int version = workflow.getVersion();
    workflow.setVersion(++version);
    workflowRepository.save(workflow);
}
public void deleteWorkflow(String workflowId) {
    workflowRepository.deleteById(workflowId);
}



}
