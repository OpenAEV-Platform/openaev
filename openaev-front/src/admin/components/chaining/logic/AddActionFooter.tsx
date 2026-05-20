import { Close, InfoOutlined } from '@mui/icons-material';
import { Box, Button, IconButton, Typography } from '@mui/material';

import { useFormatter } from '../../../../components/i18n';

interface AddActionFooterProps {
  numberOfSelectedElements: number;
  onClear: () => void;
  onSubmit: () => void;
}

const AddActionFooter = ({ numberOfSelectedElements, onClear, onSubmit }: AddActionFooterProps) => {
  const { t } = useFormatter();

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        position: 'fixed',
        bottom: 0,
        right: 0,
        width: '50%',
        gap: 2,
        p: 2,
        backgroundColor: 'background.paper',
        boxShadow: '0 -2px 8px rgba(0, 0, 0, 0.3)',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          px: 1.5,
          py: 0.5,
          borderRadius: 1,
          backgroundColor: 'action.hover',
          whiteSpace: 'nowrap',
        }}
      >
        <Typography variant="body2">
          {`${numberOfSelectedElements} ${t('Selected')}`}
        </Typography>
        {numberOfSelectedElements > 0 && (
          <IconButton size="small" onClick={onClear}>
            <Close sx={{ fontSize: 14 }} />
          </IconButton>
        )}
      </Box>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          ml: 'auto',
        }}
      >
        <InfoOutlined fontSize="small" color="info" />
        <Typography variant="body2" color="text.secondary">
          {t('Bulk select lets you add multiple actions, which you will need to configure after adding them')}
        </Typography>
      </Box>
      <Button
        variant="contained"
        color="primary"
        onClick={onSubmit}
      >
        {t('Add Actions')}
      </Button>
    </Box>
  );
};

export default AddActionFooter;
