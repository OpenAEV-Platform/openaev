/**
 * Expectation results of one gauge, split by outcome. Partial results are their
 * own bucket: they are resolved but neither a success nor a failure.
 */
export interface ResilienceBuckets {
  success: number;
  partial: number;
  failed: number;
  pending: number;
}

export interface Resilience {
  /** Every expectation behind the gauge, pending included. */
  total: number;
  /** Expectations with a result: success, partial or failed. */
  resolved: number;
  /** Rounded percentage of successes over every expectation, pending included. */
  resilience: number;
}

/**
 * The one definition of resilience shared by the simulation Results gauges
 * (PostureGauges) and the home dashboard gauges (ResilienceGaugeWidget), so the
 * number always matches the green arc of the ring and both screens agree (#8810).
 */
export const computeResilience = ({ success, partial, failed, pending }: ResilienceBuckets): Resilience => {
  const resolved = success + partial + failed;
  const total = resolved + pending;
  return {
    total,
    resolved,
    resilience: total > 0 ? Math.round((success / total) * 100) : 0,
  };
};
