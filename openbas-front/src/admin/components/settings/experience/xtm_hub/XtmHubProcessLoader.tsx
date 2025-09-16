import { Box, Button } from '@mui/material';
import type React from 'react';

import Loader from '../../../../../components/Loader';

interface ProcessLoaderProps {
  onFocusTab: () => void;
  buttonText: string;
}

const ProcessLoader: React.FC<ProcessLoaderProps> = ({
  onFocusTab,
  buttonText,
}) => {
  return (
    <Box
      sx={{
        position: 'absolute',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        zIndex: 1,
      }}
    >
      <Loader variant="inElement" />
      <Button sx={{ marginTop: 4 }} variant="contained" onClick={onFocusTab}>
        {buttonText}
      </Button>
    </Box>
  );
};

export default ProcessLoader;
