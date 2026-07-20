package io.openaev.scheduler.jobs;

import org.springframework.transaction.support.TransactionTemplate;

/**
 * Violates the plumbing guard: a class in the jobs package hand-rolling its transactions.
 * Test-scope only, so the frozen rules on production classes never see it; the fixture test imports
 * it explicitly.
 */
public class RawTemplateJobFixture {

  private final TransactionTemplate transactionTemplate;

  public RawTemplateJobFixture(TransactionTemplate transactionTemplate) {
    this.transactionTemplate = transactionTemplate;
  }

  public void run() {
    transactionTemplate.execute(status -> null);
  }
}
