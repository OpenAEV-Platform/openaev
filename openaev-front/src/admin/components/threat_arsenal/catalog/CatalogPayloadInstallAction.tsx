import { Button, Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  installed: boolean;
  installing: boolean;
  canInstall: boolean;
  onInstall: () => void;
}

// The install button of a catalogue payload, then the chip saying it is installed.
const CatalogPayloadInstallAction: FunctionComponent<Props> = ({ installed, installing, canInstall, onInstall }) => {
  const { t } = useFormatter();
  if (installed) {
    return <Chip label={t('Installed')} severity="low" />;
  }
  return canInstall ? <Button size="sm" loading={installing} onClick={onInstall}>{t('Install')}</Button> : null;
};

export default CatalogPayloadInstallAction;
