import { Box } from '@mui/material';
import { type ReactNode } from 'react';

import Empty from '../../../../components/Empty';
import SecurityMenu from '../SecurityMenu';

// Shared not-found shell for Security detail pages. Prevents the infinite
// Loader when a fetch-by-id fails (e.g. a platform entity that does not exist
// in the current scope), keeping the right-hand SecurityMenu in place.
export const SecurityDetailNotFound = ({ children }: { children: ReactNode }) => (
  <div style={{ display: 'flex' }}>
    <div style={{ flexGrow: 1 }}>
      <Box sx={{ paddingTop: 6 }}>
        <Empty message={children} />
      </Box>
    </div>
    <SecurityMenu />
  </div>
);

export default SecurityDetailNotFound;
