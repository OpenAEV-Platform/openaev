package io.openaev.architecture.background_fixtures;

/**
 * Negative control: a plain class with no background marker of any family. The detection must
 * report it as belonging to no family, so a passing detection test cannot come from a predicate
 * that trivially matches everything. Test-scope only.
 */
public class PlainBeanFixture {

  public void doWork() {}
}
