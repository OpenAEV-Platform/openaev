import { createContext } from 'react';

/**
 * Marks the surrounding simulation subtree as an autonomous (AI-driven) run so
 * shared simulation chrome can adapt without re-running run detection. The
 * autonomous cockpit reserves the right column for the always-open reasoning
 * panel, so the legacy Execution right menu (overview / mails / validations /
 * logs) must collapse to avoid overlapping it.
 */
export const AutonomousContext = createContext<{ isAutonomous: boolean }>({ isAutonomous: false });

export default AutonomousContext;
