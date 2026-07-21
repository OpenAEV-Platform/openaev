import { MenuItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import logoFiligran from '../../../../static/images/logo_filigran_full.svg';
import { fileUri } from '../../../../utils/Environment';
import { useFormatter } from '../../../i18n';

interface Props {
  navOpen: boolean;
  onClick: () => void;
}

// "Made by Filigran" footer, aligned with OpenCTI's LeftBar bottom block: a
// compact row with the muted "Made by" label (expanded only) followed by the
// single Filigran wordmark SVG at 0.8 opacity.
const MenuItemLogo: FunctionComponent<Props> = ({ navOpen, onClick }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <MenuItem
      aria-label="By Filigran"
      dense
      onClick={onClick}
      sx={{
        'minHeight': 28,
        'gap': 0.5,
        'paddingLeft': 2.5,
        '&:hover': { backgroundColor: theme.palette.leftBar?.hover },
      }}
    >
      {navOpen && (
        <Typography
          component="span"
          sx={{
            fontSize: 10,
            lineHeight: '16px',
            opacity: 0.8,
            color: theme.palette.text.tertiary,
          }}
        >
          {t('Made by')}
        </Typography>
      )}
      <img
        alt="Filigran"
        src={fileUri(logoFiligran)}
        width={navOpen ? 48 : 12}
        height={12}
        style={{
          opacity: 0.8,
          objectFit: 'cover',
          objectPosition: 'left center',
        }}
      />
    </MenuItem>
  );
};

export default MenuItemLogo;
