import { useEffect, useMemo, useState } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import { getXtmConnections, resolveCounterparts } from './counterpartStore';
import { OPENCTI_PRODUCT } from './useCounterpart';

export interface CounterpartRef {
  kind: CounterpartKindPath;
  key: string;
}

export const counterpartRefId = (ref: CounterpartRef) => `${ref.kind}|${ref.key}`;

/**
 * The objects of a set (the nodes of a graph, the rows of a page) that have a counterpart in one
 * connected product. Nothing is looked up when the product is not connected; every key is asked in
 * the same tick, so counterpartStore sends one batch per kind and serves repeats from its cache. A
 * failed or unavailable lookup marks nothing: the marks are a hint and never an error.
 */
const useCounterpartMatches = (
  refs: CounterpartRef[],
  product: string = OPENCTI_PRODUCT,
): Set<string> => {
  const [matched, setMatched] = useState<Set<string>>(() => new Set());
  // A stable dependency for the effect: the distinct ids, sorted.
  const signature = useMemo(
    () => Array.from(new Set(refs.map(counterpartRefId))).sort().join('\n'),
    [refs],
  );

  useEffect(() => {
    let alive = true;
    if (!signature) {
      setMatched(new Set());
      return undefined;
    }
    const wanted: CounterpartRef[] = signature.split('\n').map((id) => {
      const at = id.indexOf('|');
      return {
        kind: id.slice(0, at) as CounterpartKindPath,
        key: id.slice(at + 1),
      };
    });
    getXtmConnections()
      .then((connections) => {
        const connected = !!connections.find(c => c.xtm_connection_product === product)?.xtm_connection_connected;
        if (!connected) {
          return [];
        }
        return Promise.all(wanted.map(ref => resolveCounterparts(ref.kind, ref.key).then(answers => ({
          ref,
          match: answers.some(a => a.counterpart_product === product && a.counterpart_status === 'match'),
        }))));
      })
      .then((results) => {
        if (alive) {
          setMatched(new Set(results.filter(r => r.match).map(r => counterpartRefId(r.ref))));
        }
      })
      .catch(() => {
        // A hint only: a failure leaves the objects unmarked.
      });
    return () => {
      alive = false;
    };
  }, [signature, product]);

  return matched;
};

export default useCounterpartMatches;
