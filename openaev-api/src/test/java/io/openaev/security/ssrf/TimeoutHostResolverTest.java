package io.openaev.security.ssrf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Host lookup bounded in time (a stuck DNS must not stall the caller)")
class TimeoutHostResolverTest {

  private static final Duration TIMEOUT = Duration.ofMillis(200);

  /** Held by lookups that never answer, released at the end so no thread outlives its test. */
  private final CountDownLatch dnsNeverAnswers = new CountDownLatch(1);

  @AfterEach
  void releaseStuckLookups() {
    this.dnsNeverAnswers.countDown();
  }

  /**
   * A DNS lookup that never answers and, like {@link InetAddress#getAllByName(String)}, ignores
   * interruption: cancelling it from the caller does not free the thread it runs on.
   */
  private HostResolver stuckDns() {
    return host -> {
      boolean interrupted = false;
      while (this.dnsNeverAnswers.getCount() > 0) {
        try {
          this.dnsNeverAnswers.await();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
      return new InetAddress[0];
    };
  }

  @Test
  void returns_the_answer_of_a_lookup_that_is_fast_enough() throws Exception {
    InetAddress[] answer = {InetAddress.getByName("8.8.8.8")};

    TimeoutHostResolver resolver = new TimeoutHostResolver(host -> answer, TIMEOUT, 2);

    assertArrayEquals(answer, resolver.resolve("logo.example.com"));
  }

  @Test
  void gives_up_on_a_lookup_that_takes_longer_than_the_timeout() {
    TimeoutHostResolver resolver = new TimeoutHostResolver(stuckDns(), TIMEOUT, 2);

    long start = System.nanoTime();
    UnknownHostException failure =
        assertThrows(UnknownHostException.class, () -> resolver.resolve("slow.example.com"));
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

    assertTrue(failure.getMessage().contains("timed out"), failure.getMessage());
    assertTrue(
        elapsedMillis < 2_000,
        "the caller must be released soon after the timeout, not when DNS finally answers: "
            + elapsedMillis
            + " ms");
  }

  @Test
  void reports_an_unresolvable_host_as_the_delegate_did() {
    TimeoutHostResolver resolver =
        new TimeoutHostResolver(
            host -> {
              throw new UnknownHostException("no-such-host.invalid: Name or service not known");
            },
            TIMEOUT,
            2);

    UnknownHostException failure =
        assertThrows(UnknownHostException.class, () -> resolver.resolve("no-such-host.invalid"));

    assertEquals("no-such-host.invalid: Name or service not known", failure.getMessage());
  }

  @Test
  void reports_any_other_failure_as_an_unresolvable_host() {
    TimeoutHostResolver resolver =
        new TimeoutHostResolver(
            host -> {
              throw new IllegalStateException("resolver exploded");
            },
            TIMEOUT,
            2);

    assertThrows(UnknownHostException.class, () -> resolver.resolve("broken.example.com"));
  }

  @Test
  void refuses_a_lookup_at_once_when_every_slot_is_held_by_a_stuck_lookup() {
    // A lookup that timed out is not interruptible: it still holds its slot. Queuing behind it
    // would stall the next caller for the whole timeout again, so the lookup is refused instead.
    TimeoutHostResolver resolver = new TimeoutHostResolver(stuckDns(), TIMEOUT, 1);
    assertThrows(UnknownHostException.class, () -> resolver.resolve("slow.example.com"));

    UnknownHostException failure =
        assertThrows(UnknownHostException.class, () -> resolver.resolve("other.example.com"));

    assertTrue(failure.getMessage().contains("Too many host lookups"), failure.getMessage());
  }

  @Test
  void does_not_cache_anything() throws Exception {
    AtomicInteger lookups = new AtomicInteger();
    InetAddress[] answer = {InetAddress.getByName("8.8.8.8")};
    TimeoutHostResolver resolver =
        new TimeoutHostResolver(
            host -> {
              lookups.incrementAndGet();
              return answer;
            },
            TIMEOUT,
            2);

    resolver.resolve("logo.example.com");
    resolver.resolve("logo.example.com");

    assertEquals(2, lookups.get());
  }
}
