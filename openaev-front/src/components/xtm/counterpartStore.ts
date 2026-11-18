import { type CounterpartKindPath, fetchXtmConnections, lookupCounterparts } from '../../actions/xtm/counterpart-actions';
import { type CounterpartOutput, type XtmConnectionOutput } from '../../utils/api-types';

// Client side of the counterpart lookups. Every pivot of a page (the hero button, each list row
// chip, the overview card) asks for one key; the keys asked in the same tick are sent as ONE
// batch per kind, and the answers are kept for a short time so scrolling a list or coming back to
// a page does not call again. The backend caches too (5 minutes per tenant / product / key).

const TTL_MS = 2 * 60 * 1000;
const MAX_BATCH = 200;

export type CounterpartState = 'loading' | 'match' | 'no_match' | 'not_connected' | 'unavailable';

export interface CounterpartAnswer {
  state: CounterpartState;
  product: string;
  counterpart?: CounterpartOutput;
}

interface Cached {
  at: number;
  value: CounterpartOutput;
}

const cache = new Map<string, Cached>();
const pending = new Map<CounterpartKindPath, Map<string, ((value: CounterpartOutput[]) => void)[]>>();
let connectionsPromise: Promise<XtmConnectionOutput[]> | null = null;
let connectionsAt = 0;

const cacheKey = (kind: CounterpartKindPath, key: string) => `${kind}|${key}`;

/** Visible for tests: forget every cached answer and connection. */
export const resetCounterpartCache = () => {
  cache.clear();
  pending.clear();
  connectionsPromise = null;
  connectionsAt = 0;
};

export const getXtmConnections = (): Promise<XtmConnectionOutput[]> => {
  if (!connectionsPromise || Date.now() - connectionsAt > TTL_MS) {
    connectionsAt = Date.now();
    connectionsPromise = fetchXtmConnections().catch(() => {
      // A failed read is retried at the next view, never cached.
      connectionsPromise = null;
      return [];
    });
  }
  return connectionsPromise;
};

const flush = (kind: CounterpartKindPath) => {
  const waiting = pending.get(kind);
  pending.delete(kind);
  if (!waiting || waiting.size === 0) {
    return;
  }
  const keys = Array.from(waiting.keys());
  for (let i = 0; i < keys.length; i += MAX_BATCH) {
    const batch = keys.slice(i, i + MAX_BATCH);
    lookupCounterparts(kind, batch)
      .then((answers) => {
        const now = Date.now();
        answers.forEach((answer) => {
          // An unavailable product is never cached: the next view retries.
          if (answer.counterpart_key && answer.counterpart_status !== 'unavailable') {
            cache.set(cacheKey(kind, answer.counterpart_key), {
              at: now,
              value: answer,
            });
          }
        });
        batch.forEach((key) => {
          const forKey = answers.filter(a => a.counterpart_key === key);
          (waiting.get(key) ?? []).forEach(resolve => resolve(forKey));
        });
      })
      .catch(() => {
        batch.forEach(key => (waiting.get(key) ?? []).forEach(resolve => resolve([])));
      });
  }
};

/**
 * Resolves the counterparts of one key in every connected product. Keys asked in the same tick
 * are sent together; an answer younger than the TTL is served from memory.
 */
export const resolveCounterparts = (kind: CounterpartKindPath, key: string): Promise<CounterpartOutput[]> => {
  const cached = cache.get(cacheKey(kind, key));
  if (cached && Date.now() - cached.at < TTL_MS) {
    return Promise.resolve([cached.value]);
  }
  return new Promise((resolve) => {
    let waiting = pending.get(kind);
    if (!waiting) {
      waiting = new Map();
      pending.set(kind, waiting);
      setTimeout(() => flush(kind), 0);
    }
    const list = waiting.get(key) ?? [];
    list.push(resolve);
    waiting.set(key, list);
  });
};

/** The answer for one product, from the lookup answers and the connection list. */
export const toAnswer = (
  product: string,
  connections: XtmConnectionOutput[],
  answers: CounterpartOutput[] | null,
): CounterpartAnswer => {
  const connection = connections.find(c => c.xtm_connection_product === product);
  if (!connection?.xtm_connection_connected) {
    return {
      state: 'not_connected',
      product,
    };
  }
  if (answers === null) {
    return {
      state: 'loading',
      product,
    };
  }
  const answer = answers.find(a => a.counterpart_product === product);
  if (!answer) {
    return {
      state: 'unavailable',
      product,
    };
  }
  switch (answer.counterpart_status) {
    case 'match':
      return {
        state: 'match',
        product,
        counterpart: answer,
      };
    case 'no_match':
      return {
        state: 'no_match',
        product,
      };
    case 'not_connected':
      return {
        state: 'not_connected',
        product,
      };
    default:
      return {
        state: 'unavailable',
        product,
      };
  }
};
