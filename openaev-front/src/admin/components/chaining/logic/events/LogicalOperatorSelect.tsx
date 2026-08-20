import { ArrowDropDown } from '@mui/icons-material';
import { Button, Menu, MenuItem } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import type { LogicalOperator } from './event-types';

interface Props {
  value: LogicalOperator;
  onChange: (value: LogicalOperator) => void;
  readOnly?: boolean;
}

const LogicalOperatorSelect: FunctionComponent<Props> = ({ value, onChange, readOnly = false }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);

  const handleOpen = (e: MouseEvent<HTMLButtonElement>) => setAnchorEl(e.currentTarget);
  const handleClose = () => setAnchorEl(null);
  const handleSelect = (operator: LogicalOperator) => {
    onChange(operator);
    handleClose();
  };

  return (
    <>
      <Button
        size="small"
        disabled={readOnly}
        onClick={handleOpen}
        endIcon={<ArrowDropDown />}
        sx={{
          'background': `${theme.palette.primary.main}18`,
          'color': 'primary.main',
          'fontWeight': 700,
          'px': 1.5,
          'borderRadius': 1,
          'textTransform': 'none',
          '&:hover': { background: `${theme.palette.primary.main}28` },
          '& .MuiButton-endIcon': { ml: 0.5 },
        }}
      >
        {value === 'AND' ? t('And') : t('Or')}
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleClose}
        slotProps={{
          paper: {
            sx: {
              background: theme.palette.background.paper,
              minWidth: 80,
            },
          },
        }}
      >
        <MenuItem
          selected={value === 'AND'}
          disabled={readOnly}
          onClick={() => handleSelect('AND')}
        >
          {t('And')}
        </MenuItem>
        <MenuItem
          selected={value === 'OR'}
          disabled={readOnly}
          onClick={() => handleSelect('OR')}
        >
          {t('Or')}
        </MenuItem>
      </Menu>
    </>
  );
};

export default LogicalOperatorSelect;
