import { useEffect, useState } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import { type CounterpartOutput, type XtmConnectionOutput } from '../../utils/api-types';
import { type CounterpartAnswer, getXtmConnections, resolveCounterparts, toAnswer } from './counterpartStore';

export const OPENCTI_PRODUCT = 'opencti';

/**
 * The counterpart of one object in one connected product. The lookup runs only when the product
 * is connected and the object has a key; several components of the page asking at the same time
 * share one batch call (counterpartStore).
 */
const useCounterpart = (
  kind: CounterpartKindPath,
  key: string | null | undefined,
  product: string = OPENCTI_PRODUCT,
): CounterpartAnswer & { connectionUrl?: string } => {
  const [connections, setConnections] = useState<XtmConnectionOutput[] | null>(null);
  const [answers, setAnswers] = useState<CounterpartOutput[] | null>(null);

  useEffect(() => {
    let alive = true;
    getXtmConnections().then((value) => {
      if (alive) {
        setConnections(value);
      }
    });
    return () => {
      alive = false;
    };
  }, []);

  const connected = !!connections?.find(c => c.xtm_connection_product === product)?.xtm_connection_connected;

  useEffect(() => {
    let alive = true;
    setAnswers(null);
    if (!connected || !key) {
      return undefined;
    }
    resolveCounterparts(kind, key).then((value) => {
      if (alive) {
        setAnswers(value);
      }
    });
    return () => {
      alive = false;
    };
  }, [connected, kind, key]);

  if (connections === null) {
    return {
      state: 'loading',
      product,
    };
  }
  const connectionUrl = connections.find(c => c.xtm_connection_product === product)?.xtm_connection_url;
  if (connected && !key) {
    return {
      state: 'no_match',
      product,
      connectionUrl,
    };
  }
  return {
    ...toAnswer(product, connections, answers),
    connectionUrl,
  };
};

export default useCounterpart;
