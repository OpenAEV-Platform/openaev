import { Button, Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  installed: boolean;
  installing: boolean;
  canInstall: boolean;
  onInstall: () => void;
  label?: string;
  size?: 'sm';
}

// The install button of a catalogue payload, then the chip saying it is installed.
const CatalogPayloadInstallAction: FunctionComponent<Props> = ({ installed, installing, canInstall, onInstall, label, size }) => {
  const { t } = useFormatter();
  if (installed) {
    return <Chip label={t('Installed')} severity="low" />;
  }
  return canInstall ? <Button size={size} loading={installing} onClick={onInstall}>{label ?? t('Install')}</Button> : null;
};

export default CatalogPayloadInstallAction;
