import { createContext } from 'react';

import type { GroupOption } from '../../../../utils/Option';

export interface FilterContextType { defaultValues: Map<string, GroupOption[]> }

export const FilterContext = createContext<FilterContextType>({ defaultValues: new Map() });
