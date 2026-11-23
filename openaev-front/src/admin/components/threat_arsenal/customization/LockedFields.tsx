import { type ReactNode, useContext } from 'react';

import FiligranValuesContext from './FiligranValuesContext';

// A customized copy keeps these fields as Filigran sets them.
const LockedFields = ({ children }: { children: ReactNode }) => {
  const filigranValues = useContext(FiligranValuesContext);
  if (!filigranValues) {
    return children;
  }
  return (
    <fieldset
      disabled
      inert
      style={{
        display: 'contents',
        border: 0,
        margin: 0,
        padding: 0,
      }}
    >
      {children}
    </fieldset>
  );
};

export default LockedFields;
