package io.openaev.architecture.fixture;

import org.springframework.transaction.annotation.Transactional;

/** Violates the self-invocation rule: an intra-class call to its own @Transactional method. */
public class SelfInvocationTrapFixture {

  public void outer() {
    // The trap: this call never goes through the Spring proxy.
    inner();
  }

  @Transactional
  public void inner() {}
}
