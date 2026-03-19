import { createContextualCan } from '@casl/react';
import { createContext } from 'react';

import { type AppAbility } from './ability';

export const AbilityContext = createContext<AppAbility>({} as AppAbility);
export const Can = createContextualCan<AppAbility>(AbilityContext.Consumer);
