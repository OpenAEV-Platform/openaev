import { createContext } from 'react';

import { type ThreatArsenalActionFormValues } from '../actionFormValues';

// Set while customizing: the catalogue values the variant starts from.
const VariantBaseContext = createContext<ThreatArsenalActionFormValues | null>(null);

export default VariantBaseContext;
