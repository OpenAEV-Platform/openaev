import { useTheme } from '@mui/material/styles';
import { type ReactNode } from 'react';

interface Props {
  locked: boolean;
  children: ReactNode;
}

// Disabled for native fields, inert for the pickers that are not.
const LockedFields = ({ locked, children }: Props) => {
  const theme = useTheme();
  if (!locked) {
    return <>{children}</>;
  }
  return (
    <fieldset
      disabled
      inert
      style={{
        border: 0,
        padding: 0,
        margin: 0,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
    >
      {children}
    </fieldset>
  );
};

export default LockedFields;
