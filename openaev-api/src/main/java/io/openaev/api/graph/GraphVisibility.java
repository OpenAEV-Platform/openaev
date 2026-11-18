package io.openaev.api.graph;

/**
 * The resource-level gate of the exploration graph. Assets, asset groups and findings are gated by
 * the endpoint's capability; simulations, scenarios and injects are grant-managed, so each node of
 * those kinds is kept only when the caller can read it. An inject is read through its simulation,
 * or as an atomic testing when it has none, the way {@code BaseInjectExpectation} resolves its own
 * permission resource.
 */
public interface GraphVisibility {

  boolean canReadSimulation(String simulationId);

  boolean canReadScenario(String scenarioId);

  boolean canReadAtomicTesting(String injectId);

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
      };
}
