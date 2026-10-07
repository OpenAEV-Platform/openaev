---
paths:
  - "openaev-api/src/main/java/io/openaev/scheduler/jobs/QueueChainingJob.java"
  - "openaev-api/src/main/java/io/openaev/scheduler/jobs/WorkflowTimeoutJob.java"
  - "openaev-api/src/main/java/io/openaev/aop/WorkflowUpdateEvent.java"
  - "openaev-api/src/main/java/io/openaev/aop/WorkflowUpdateEventAspect.java"
  - "openaev-api/src/main/java/io/openaev/utils/ConditionUtils.java"
  - "openaev-api/src/main/java/io/openaev/rest/exception/ChainingException.java"
  - "openaev-model/src/main/java/io/openaev/database/model/Step.java"
  - "openaev-model/src/main/java/io/openaev/database/model/Workflow.java"
  - "openaev-model/src/main/java/io/openaev/database/model/Condition.java"
  - "openaev-model/src/main/java/io/openaev/database/model/ConditionStep.java"
  - "openaev-model/src/main/java/io/openaev/database/model/WorkflowState.java"
  - "openaev-model/src/main/java/io/openaev/database/model/WorkflowStateEntries.java"
  - "openaev-model/src/main/java/io/openaev/database/model/StepDelayQueue.java"
  - "openaev-model/src/main/java/io/openaev/database/model/WorkflowScopeRule.java"
  - "openaev-model/src/main/java/io/openaev/database/model/ScopeVariable.java"
  - "openaev-model/src/main/java/io/openaev/database/model/InjectDependencyConditions.java"
  - "openaev-model/src/main/java/io/openaev/database/model/StepStatus.java"
  - "openaev-model/src/main/java/io/openaev/database/model/StepActionClass.java"
  - "openaev-model/src/main/java/io/openaev/database/model/WorkflowStatus.java"
  - "openaev-model/src/main/java/io/openaev/database/model/ConditionType.java"
  - "openaev-model/src/main/java/io/openaev/database/model/ConditionKeyType.java"
  - "openaev-model/src/main/java/io/openaev/database/model/MappingType.java"
  - "openaev-model/src/main/java/io/openaev/database/model/ScopeRuleSelectedMode.java"
  - "openaev-model/src/main/java/io/openaev/database/model/ContractOutputType.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/StepRepository.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/WorkflowRepository.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/ConditionRepository.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/WorkflowStateRepository.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/WorkflowStateRepositoryCustom.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/WorkflowStateRepositoryCustomImpl.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/StepDelayQueueRepository.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/WorkflowScopeRuleRepository.java"
  - "openaev-model/src/main/java/io/openaev/database/repository/ScopeVariableRepository.java"
---

<!-- `paths` = the single-file entries of `applyTo` in .github/instructions/chaining-engine.instructions.md
     (its folders are covered by a CLAUDE.md in each folder). Keep both lists in sync. -->

This file belongs to the Chaining Engine. Before changing it, read
`.github/instructions/chaining-engine.instructions.md` and follow it.
