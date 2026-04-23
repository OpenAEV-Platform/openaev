package io.openaev.utils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility for running parallel I/O-bound tasks on virtual threads instead of the common
 * ForkJoinPool.
 *
 * <p><b>Why?</b> {@code stream().parallel()} uses the common ForkJoinPool (platform threads). When
 * tasks perform blocking I/O (DB, HTTP, Elasticsearch), they occupy platform threads indefinitely,
 * causing thread starvation for the entire application. Virtual threads avoid this: the JDK
 * unmounts them from carrier threads during I/O waits.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Before (dangerous — blocks ForkJoinPool common pool):
 * items.stream().parallel().map(this::fetchFromDb).toList();
 *
 * // After (safe — uses virtual threads):
 * VirtualThreads.parallelMap(items, this::fetchFromDb);
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VirtualThreads {

  /**
   * Apply a mapping function to each element in parallel using virtual threads.
   *
   * @param items the input collection
   * @param mapper the function to apply to each element
   * @param <T> input type
   * @param <R> result type
   * @return list of results, in the same order as the input
   */
  public static <T, R> List<R> parallelMap(Collection<T> items, Function<T, R> mapper) {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<R>> tasks =
          items.stream().map(item -> (Callable<R>) () -> mapper.apply(item)).toList();
      return executor.invokeAll(tasks).stream().map(VirtualThreads::getResult).toList();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Parallel execution interrupted", e);
    }
  }

  /**
   * Apply a mapping function to each element in parallel using virtual threads.
   *
   * @param items the input stream
   * @param mapper the function to apply to each element
   * @param <T> input type
   * @param <R> result type
   * @return list of results, in the same order as the input
   */
  public static <T, R> List<R> parallelMap(Stream<T> items, Function<T, R> mapper) {
    return parallelMap(items.toList(), mapper);
  }

  /**
   * Run an action on each element in parallel using virtual threads. Waits for all tasks to
   * complete before returning.
   *
   * @param items the input collection
   * @param action the action to run for each element
   * @param <T> element type
   */
  public static <T> void parallelForEach(
      Collection<T> items, java.util.function.Consumer<T> action) {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<Void>> tasks =
          items.stream()
              .map(
                  item ->
                      (Callable<Void>)
                          () -> {
                            action.accept(item);
                            return null;
                          })
              .toList();
      executor.invokeAll(tasks).forEach(VirtualThreads::getResult);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Parallel execution interrupted", e);
    }
  }

  private static <R> R getResult(Future<R> future) {
    try {
      return future.get();
    } catch (java.util.concurrent.ExecutionException e) {
      if (e.getCause() instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Task interrupted", e);
    }
  }
}
