import { useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';

import Tag from '../../../components/common/tag/Tag';
import { useFormatter } from '../../../components/i18n';

interface Props {
  variant?: string;
  status: 'Active' | 'Inactive' | 'Agentless';
}

const AssetStatus: FunctionComponent<Props> = ({ status = 'Active' }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const getColor = () => {
    switch (status) {
      case 'Inactive':
        return theme.palette.error.main;
      case 'Agentless':
        return theme.palette.severity?.medium ?? '#E1B823';
      default:
        return theme.palette.success.main;
    }
  };

  return (
    <Tag
      label={t(status)}
      color={getColor()}
    />
  );
};

export default AssetStatus;
