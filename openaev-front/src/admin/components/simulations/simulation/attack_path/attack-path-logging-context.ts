import { createContext, useContext } from 'react';

// Whether Logging verdicts are in play on this attack path: the preview feature is on, or the graph
// holds a logged-only verdict recorded while it was on. A yellow node then always has its legend entry.
const AttackPathLoggingContext = createContext(false);

export const useAttackPathShowsLogging = (): boolean => useContext(AttackPathLoggingContext);

export default AttackPathLoggingContext;
