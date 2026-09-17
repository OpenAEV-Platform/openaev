import { Chip } from '@filigran/design-system';
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
        <Chip label={t('Inactive')} severity="critical" />
      );
    case 'Agentless':
      return (
        <Chip label={t('Agentless')} severity="medium" />
      );
    default:
      return (
        <Chip label={t('Active')} severity="low" />
      );
  }
};

export default AssetStatus;
