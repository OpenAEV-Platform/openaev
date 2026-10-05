import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  variant: string;
  mode: string;
}

const AgentDeploymentMode: FunctionComponent<Props> = ({ mode }) => {
  const { t } = useFormatter();

  switch (mode) {
    case 'session':
      return (
        <Chip label={t('Session')} severity="info" />
      );
    default:
      return (
        <Chip label={t('Service')} severity="info" />
      );
  }
};

export default AgentDeploymentMode;
