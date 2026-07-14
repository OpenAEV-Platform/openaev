import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import Tag from '../../../../components/common/tag/Tag';
import { useFormatter } from '../../../../components/i18n';

interface Props { children?: ReactNode }

const DefaultTenantDangerZone: FunctionComponent<Props> = ({ children }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Box
      data-testid="default-tenant-danger-zone"
      sx={{
        border: `1px solid ${theme.palette.dangerZone.main}`,
        borderRadius: 1,
        padding: theme.spacing(2),
      }}
    >
      <Box sx={{ marginBottom: theme.spacing(2) }}>
        <Tag
          label={t('Default Tenant / Danger Zone')}
          color={theme.palette.dangerZone.main}
          disableTooltip
        />
      </Box>
      {children}
    </Box>
  );
};

export default DefaultTenantDangerZone;
