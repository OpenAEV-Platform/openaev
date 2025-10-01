import { Box, Button } from '@mui/material';
import type React from 'react';

import { useFormatter } from '../../../../../components/i18n';

interface ProcessInstructionsProps {
  onContinue: () => void;
  instructionKey: string;
}

const XtmHubProcessInstructions: React.FC<ProcessInstructionsProps> = ({
  onContinue,
  instructionKey,
}) => {
  const { t } = useFormatter();

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        flexDirection: 'column',
      }}
    >
      <p style={{
        whiteSpace: 'pre-line',
        width: '100%',
      }}
      >
        {t(instructionKey)}
      </p>
      <div style={{
        display: 'flex',
        justifyContent: 'flex-end',
        width: '100%',
      }}
      >
        <Button onClick={onContinue}>
          {t('Continue')}
        </Button>
      </div>
    </Box>
  );
};

export default XtmHubProcessInstructions;
