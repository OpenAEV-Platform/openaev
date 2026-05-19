import { Box } from '@mui/material';
import { type ReactNode } from 'react';

interface Props {
  value: number;
  index: number;
  children: ReactNode;
}

const TabPanel = ({
  value,
  index,
  children,
}: Props) => {
  const isActive = value === index;
  return (
    <div
      role="tabpanel"
      hidden={!isActive}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      style={{ display: isActive ? undefined : 'none' }}
    >
      <Box
        display="flex"
        flexDirection="column"
        gap={2}
        sx={{ mt: 2 }}
      >
        {children}
      </Box>
    </div>
  );
};

export default TabPanel;
