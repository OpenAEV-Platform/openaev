package io.openaev.api.graph;

/**
 * The resource-level gate of the exploration graph. Assets and asset groups are gated by the
 * endpoint's capability, findings by the findings read capability; simulations, scenarios and
 * injects are grant-managed, so each node of those kinds is kept only when the caller can read it.
 * An inject is read through its simulation, or as an atomic testing when it has none, the way
 * {@code BaseInjectExpectation} resolves its own permission resource.
 */
public interface GraphVisibility {

  boolean canReadSimulation(String simulationId);

  boolean canReadScenario(String scenarioId);

  boolean canReadAtomicTesting(String injectId);

  /**
   * Persons, organizations and teams (#9447) are capability-gated: a person node needs the player
   * read capability, an organization the organization one, a team the team one. Every
   * implementation states its own answer: there is no permissive default.
   */
  boolean canReadPeople(io.openaev.api.graph.dto.GraphNodeType type);

  /**
   * Finding nodes follow the findings read capability, not the asset one the endpoint requires: a
   * caller who reads assets without that capability sees no finding node and no finding count.
   */
  boolean canReadFindings();

  /** Everything visible: for tests and for callers that gate elsewhere. */
  GraphVisibility ALL =
      new GraphVisibility() {
        @Override
        public boolean canReadSimulation(String simulationId) {
          return true;
        }

        @Override
        public boolean canReadScenario(String scenarioId) {
          return true;
        }

        @Override
        public boolean canReadAtomicTesting(String injectId) {
          return true;
        }

        @Override
        public boolean canReadPeople(io.openaev.api.graph.dto.GraphNodeType type) {
          return true;
        }

        @Override
        public boolean canReadFindings() {
          return true;
        }
      };
}
