import { Chip } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';

interface Props {
  variant: string;
  status: 'Active' | 'Inactive' | 'Agentless';
}

const AssetStatus: FunctionComponent<Props> = ({ status = 'Active' }) => {
  const { t } = useFormatter();

  switch (status) {
    case 'Inactive':
      return (
        <Chip label={t('Inactive')} />
      );
    case 'Agentless':
      return (
        <Chip label={t('Agentless')} />
      );
    default:
      return (
        <Chip label={t('Active')} />
      );
  }
};

export default AssetStatus;
