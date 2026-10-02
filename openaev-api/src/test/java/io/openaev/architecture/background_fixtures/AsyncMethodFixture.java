package io.openaev.architecture.background_fixtures;

import org.springframework.scheduling.annotation.Async;

/**
 * Near-miss for the {@code @Async} family: a bean whose only background marker is an {@code @Async}
 * method, which Spring runs on a pool thread that inherits neither the caller's transaction nor its
 * tenant scope. It carries no other marker, so removing the {@code @Async} predicate would let it
 * escape. Test-scope only; imported explicitly by the detection test.
 */
public class AsyncMethodFixture {

  @Async
  public void runAsync() {}
}
