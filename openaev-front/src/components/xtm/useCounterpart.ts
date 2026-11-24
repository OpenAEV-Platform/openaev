import { useEffect, useState } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import { type CounterpartOutput, type XtmConnectionOutput } from '../../utils/api-types';
import { type CounterpartAnswer, getXtmConnections, resolveCounterparts, toAnswer } from './counterpartStore';
import useXtmCounterparts from './useXtmCounterparts';

export const OPENCTI_PRODUCT = 'opencti';

/**
 * The counterpart of one object in one connected product. The lookup runs only when the product
 * is connected and the object has a key; several components of the page asking at the same time
 * share one batch call (counterpartStore). With the XTM_COUNTERPARTS feature off nothing is asked
 * and the product reads as not connected, so every pivot draws nothing.
 */
const useCounterpart = (
  kind: CounterpartKindPath,
  key: string | null | undefined,
  product: string = OPENCTI_PRODUCT,
): CounterpartAnswer & { connectionUrl?: string } => {
  const [connections, setConnections] = useState<XtmConnectionOutput[] | null>(null);
  const [answers, setAnswers] = useState<CounterpartOutput[] | null>(null);
  const enabled = useXtmCounterparts();

  useEffect(() => {
    let alive = true;
    if (!enabled) {
      return undefined;
    }
    getXtmConnections().then((value) => {
      if (alive) {
        setConnections(value);
      }
    });
    return () => {
      alive = false;
    };
  }, [enabled]);

  const connected = enabled && !!connections?.find(c => c.xtm_connection_product === product)?.xtm_connection_connected;

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

  if (!enabled) {
    return {
      state: 'not_connected',
      product,
    };
  }
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
