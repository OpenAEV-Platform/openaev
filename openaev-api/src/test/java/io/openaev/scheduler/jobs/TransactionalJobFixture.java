package io.openaev.scheduler.jobs;

import org.springframework.transaction.annotation.Transactional;

/**
 * Violates the jobs guard: a @Transactional method on a class in the jobs package. Test-scope only,
 * so the frozen rules on production classes never see it; the fixture test imports it explicitly.
 */
public class TransactionalJobFixture {

  @Transactional
  public void run() {}
}
