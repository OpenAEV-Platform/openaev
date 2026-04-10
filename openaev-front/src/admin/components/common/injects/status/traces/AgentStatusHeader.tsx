import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import ItemStatus from '../../../../../../components/ItemStatus';

interface Props {
  agentName?: string;
  statusName?: string;
}

const AgentStatusHeader: FunctionComponent<Props> = ({ agentName, statusName }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const translatedStatus = statusName ? t(statusName) : undefined;

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
    }}
    >
      <Typography
        variant="body1"
        sx={{
          fontWeight: 600,
          mr: theme.spacing(1.5),
        }}
      >
        {agentName || '-'}
      </Typography>
      {statusName
        && (
          <ItemStatus
            status={statusName}
            label={translatedStatus ?? statusName}
          />
        )}
    </div>
  );
};

export default AgentStatusHeader;
