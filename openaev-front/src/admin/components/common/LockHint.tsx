import { LockOutlined } from '@mui/icons-material';
import { Alert } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props { children: ReactNode }

/** States a lock rule once, above the list it governs, rather than behind a hover on each row. */
const LockHint: FunctionComponent<Props> = ({ children }) => (
  <Alert
    severity="info"
    icon={<LockOutlined />}
    sx={{
      mb: 2,
      alignItems: 'center',
    }}
  >
    {children}
  </Alert>
);

export default LockHint;
