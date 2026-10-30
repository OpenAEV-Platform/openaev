package io.openaev.architecture.fixture;

import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;

/** Accessors of a tenant-active table's repository that no access rule allowlists. */
public final class TenantActiveTableAccessFixtures {

  private TenantActiveTableAccessFixtures() {}

  public static class UnreviewedAutonomousRunAccessor {

    private final AutonomousRunRepository autonomousRunRepository;

    public UnreviewedAutonomousRunAccessor(AutonomousRunRepository autonomousRunRepository) {
      this.autonomousRunRepository = autonomousRunRepository;
    }

    public long countRuns() {
      return autonomousRunRepository.count();
    }
  }

  public static class UnreviewedObjectiveTemplateAccessor {

    private final AutonomousObjectiveTemplateRepository objectiveTemplateRepository;

    public UnreviewedObjectiveTemplateAccessor(
        AutonomousObjectiveTemplateRepository objectiveTemplateRepository) {
      this.objectiveTemplateRepository = objectiveTemplateRepository;
    }

    public long countTemplates() {
      return objectiveTemplateRepository.count();
    }
  }
}
