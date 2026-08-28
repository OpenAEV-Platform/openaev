/**
 * Creates a gate that runs at most `maxConcurrent` async tasks at a time,
 * queueing the rest in FIFO order.
 *
 * Browsers only open ~6 HTTP/1.1 connections per origin: a page that fires
 * dozens of slow API calls at once (e.g. every widget of a dashboard while the
 * backend is still warming up) monopolizes all of them, and anything else -
 * lazy route chunks, navigation data - queues behind for seconds. Funneling
 * those calls through this limiter keeps connections free for user actions.
 */
const limitConcurrency = (maxConcurrent: number) => {
  // A non-positive limit would enqueue every task forever and deadlock callers.
  if (!Number.isInteger(maxConcurrent) || maxConcurrent <= 0) {
    throw new Error(`limitConcurrency: maxConcurrent must be a positive integer (got ${maxConcurrent})`);
  }
  let active = 0;
  const queue: (() => void)[] = [];

  const onSettled = () => {
    active -= 1;
    const run = queue.shift();
    if (run) {
      run();
    }
  };

  return <T>(task: () => Promise<T>): Promise<T> => new Promise<T>((resolve, reject) => {
    const run = () => {
      active += 1;
      // Route through Promise.resolve().then(task) so a synchronous throw in
      // task() becomes a rejection and still releases the slot via onSettled.
      Promise.resolve().then(task).then(resolve, reject).finally(onSettled);
    };
    if (active < maxConcurrent) {
      run();
    } else {
      queue.push(run);
    }
  });
};

export default limitConcurrency;
