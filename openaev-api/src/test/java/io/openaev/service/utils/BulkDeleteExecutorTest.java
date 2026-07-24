package io.openaev.service.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.BulkOperationContext;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BulkDeleteExecutorTest {

  private BulkDeleteChunkRunner chunkRunner;
  private BulkOperationMonitor bulkOperationMonitor;
  private EntityManager entityManager;
  private BulkDeleteExecutor executor;

  @BeforeEach
  void setUp() {
    chunkRunner = mock(BulkDeleteChunkRunner.class);
    bulkOperationMonitor = mock(BulkOperationMonitor.class);
    entityManager = mock(EntityManager.class);
    // The real runner only opens a transaction around the work: execute it directly here.
    when(chunkRunner.call(any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
    when(bulkOperationMonitor.start(any(), any(), anyInt())).thenReturn("op-id");
    executor = new BulkDeleteExecutor(chunkRunner, bulkOperationMonitor, entityManager);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test
  @DisplayName("Given more ids than the chunk size, should delete sorted ids chunk by chunk")
  void given_moreIdsThanChunkSize_should_deleteSortedIdsChunkByChunk() {
    List<String> ids =
        IntStream.range(0, BulkDeleteExecutor.CHUNK_SIZE * 2 + 10)
            .mapToObj(i -> String.format("id-%03d", i))
            .toList();
    List<String> shuffled = new ArrayList<>(ids);
    Collections.shuffle(shuffled);
    List<List<String>> chunks = new ArrayList<>();

    List<String> deleted = executor.deleteInChunks("things", shuffled, chunks::add);

    assertThat(deleted).isEqualTo(ids.stream().sorted().toList());
    assertThat(chunks)
        .hasSize(3)
        .allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(25));
    assertThat(chunks.stream().flatMap(List::stream).toList())
        .isEqualTo(ids.stream().sorted().toList());
    verify(entityManager, times(3)).clear();
    // The deletion is tracked as a massive operation: start, one progress per chunk, complete.
    verify(bulkOperationMonitor).start("delete", "things", ids.size());
    verify(bulkOperationMonitor, times(3)).progress(eq("op-id"), anyInt());
    verify(bulkOperationMonitor).complete("op-id");
    verify(bulkOperationMonitor, never()).fail(any());
  }

  @Test
  @DisplayName("Given chunked deletion, should suppress per-entity stream events inside chunks")
  void given_chunkedDeletion_should_suppressStreamEventsInsideChunks() {
    List<Boolean> suppressedDuringChunk = new ArrayList<>();

    executor.deleteInChunks(
        "things",
        List.of("a", "b"),
        chunk -> suppressedDuringChunk.add(BulkOperationContext.isActive()));

    assertThat(suppressedDuringChunk).containsExactly(true);
    assertThat(BulkOperationContext.isActive()).isFalse();
  }

  @Test
  @DisplayName("Given a chunk failing with a non-retriable error, should mark the operation failed")
  void given_chunkFailingWithNonRetriableError_should_markOperationFailed() {
    assertThatThrownBy(
            () ->
                executor.deleteInChunks(
                    "things",
                    List.of("a"),
                    chunk -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);
    verify(bulkOperationMonitor).fail("op-id");
    verify(bulkOperationMonitor, never()).complete(any());
  }

  @Test
  @DisplayName("Given a chunk losing a deadlock, should retry it and succeed")
  void given_chunkLosingDeadlock_should_retryAndSucceed() {
    AtomicInteger attempts = new AtomicInteger();

    List<String> deleted =
        executor.deleteInChunks(
            "things",
            List.of("a", "b"),
            chunk -> {
              if (attempts.incrementAndGet() < 2) {
                throw new CannotAcquireLockException("deadlock detected");
              }
            });

    assertThat(deleted).containsExactly("a", "b");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("Given a chunk deadlocking on every attempt, should give up after the max attempts")
  void given_chunkAlwaysDeadlocking_should_giveUpAfterMaxAttempts() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor.deleteInChunks(
                    "things",
                    List.of("a"),
                    chunk -> {
                      attempts.incrementAndGet();
                      throw new CannotAcquireLockException("deadlock detected");
                    }))
        .isInstanceOf(CannotAcquireLockException.class);
    assertThat(attempts.get()).isEqualTo(BulkDeleteExecutor.MAX_ATTEMPTS);
  }

  @Test
  @DisplayName(
      "Given an already-active transaction (transactional tests), should delete inline without chunking")
  void given_activeTransaction_should_deleteInlineWithoutChunking() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    List<String> ids =
        IntStream.range(0, BulkDeleteExecutor.CHUNK_SIZE + 5).mapToObj(i -> "id-" + i).toList();
    List<List<String>> calls = new ArrayList<>();

    List<String> deleted = executor.deleteInChunks("things", ids, calls::add);

    assertThat(deleted).hasSameSizeAs(ids);
    // One single inline call with every id: chunked commits are impossible inside the caller's
    // transaction.
    assertThat(calls).hasSize(1);
    assertThat(calls.get(0)).hasSameSizeAs(ids);
    // Inline deletions (transactional tests) are not tracked as massive operations.
    verify(bulkOperationMonitor, never()).start(any(), any(), anyInt());
  }

  @Test
  @DisplayName("Given no ids, should not invoke the deleter at all")
  void given_noIds_should_notInvokeDeleter() {
    List<List<String>> calls = new ArrayList<>();

    List<String> deleted = executor.deleteInChunks("things", List.of(), calls::add);

    assertThat(deleted).isEmpty();
    assertThat(calls).isEmpty();
  }
}
