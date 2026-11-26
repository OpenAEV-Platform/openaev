import { createContext } from 'react';

import { type ActionFormValues } from '../utils/actionFormValues';

// Set while editing a customized copy: the values of the Filigran version it is built from.
const FiligranValuesContext = createContext<ActionFormValues | null>(null);

export default FiligranValuesContext;
