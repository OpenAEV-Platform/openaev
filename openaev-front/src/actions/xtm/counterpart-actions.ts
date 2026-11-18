import { simpleCall, simplePostCall } from '../../utils/Action';
import { type CounterpartLookupInput, type CounterpartOutput, type XtmConnectionOutput } from '../../utils/api-types';

// Cross-platform awareness (OpenAEV x OpenCTI): which XTM products the tenant reaches, and which of
// our objects they also hold. Both calls are silent on error: a pivot never raises a page toast,
// the caller degrades to "unavailable" (the backend already answers per key, never a 5xx for a
// product that is down).

export type CounterpartKindPath = 'assets' | 'attack-patterns' | 'vulnerabilities' | 'threats';

const XTM_URI = '/api/xtm';

export const fetchXtmConnections = () => simpleCall(`${XTM_URI}/connections`, undefined, false)
  .then((response: { data: XtmConnectionOutput[] }) => response.data ?? []);

export const lookupCounterparts = (kind: CounterpartKindPath, keys: string[]) => {
  const input: CounterpartLookupInput = { counterpart_keys: keys };
  return simplePostCall(`${XTM_URI}/counterparts/${kind}`, input, undefined, false)
    .then((response: { data: CounterpartOutput[] }) => response.data ?? []);
};
