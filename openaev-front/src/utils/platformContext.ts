import { createContext, useContext } from 'react';

/**
 * Whether the current app instance is running in platform mode
 * (no tenant UUID in the URL) or tenant mode.
 */
const PlatformModeContext = createContext<boolean>(false);

export const PlatformModeProvider = PlatformModeContext.Provider;

export const useIsCurrentPlatformRoute = (): boolean => useContext(PlatformModeContext);
