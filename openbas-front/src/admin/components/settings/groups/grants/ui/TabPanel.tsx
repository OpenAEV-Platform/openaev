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
  if (value !== index) return null;
  return (
    <div
      role="tabpanel"
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
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
