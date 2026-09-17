import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  variant: string;
  privilege: string;
}

const AgentPrivilege: FunctionComponent<Props> = ({ privilege }) => {
  const { t } = useFormatter();

  switch (privilege) {
    case 'admin':
      return (
        <Chip label={t('Admin')} severity="low" />
      );
    default:
      return (
        <Chip label={t('Standard')} severity="medium" />
      );
  }
};

export default AgentPrivilege;
