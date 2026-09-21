package io.openaev.architecture.background_fixtures;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * Near-miss for the Quartz family: a bean whose only background marker is implementing {@code
 * org.quartz.Job}. It carries no annotation, no executor field and no inline hand-off, so removing
 * the Quartz predicate would let it escape. Test-scope only; imported explicitly by the detection
 * test.
 */
public class QuartzJobFixture implements Job {

  @Override
  public void execute(JobExecutionContext context) {}
}
