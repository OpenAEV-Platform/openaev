import { Button, Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { installCatalogPayload } from '../../../../actions/catalog/payload-catalog-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type AvailablePayloadActionOutput } from '../../../../utils/api-types';
import CatalogPayloadVersionPreview from './CatalogPayloadVersionPreview';
import useCatalogPayloadVersions from './useCatalogPayloadVersions';

interface Props {
  action: AvailablePayloadActionOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstalled: (version: string) => void;
}

// A payload of the Available tab: its latest version first, any other offered version on demand.
const ThreatArsenalCatalogDrawer: FunctionComponent<Props> = ({ action, canInstall, onClose, onInstalled }) => {
  const { t } = useFormatter();
  const versions = useCatalogPayloadVersions(action?.action_id);
  const [shownVersion, setShownVersion] = useState('');
  const [installing, setInstalling] = useState(false);

  useEffect(() => setShownVersion(action?.action_payload_version ?? ''), [action]);

  const shown = versions.find(version => version.catalog_payload_version === shownVersion);

  const install = () => {
    if (!shown) return;
    setInstalling(true);
    installCatalogPayload(shown.catalog_payload_external_id, shown.catalog_payload_version)
      .then(() => {
        onInstalled(shown.catalog_payload_version);
        onClose();
      })
      .catch(() => undefined)
      .finally(() => setInstalling(false));
  };

  return (
    <Drawer
      open={action !== null}
      handleClose={onClose}
      title={t('Action information')}
      headerActions={canInstall && shown && (
        <Button loading={installing} onClick={install}>{t('Install this version')}</Button>
      )}
    >
      {action && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            maxWidth: 240,
          }}
          >
            <Select value={shownVersion} onValueChange={setShownVersion}>
              <SelectLabel>{t('Version')}</SelectLabel>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {versions.map((version, index) => (
                  <SelectItem key={version.catalog_payload_version} value={version.catalog_payload_version}>
                    {index === 0
                      ? `v${version.catalog_payload_version} (${t('latest')})`
                      : `v${version.catalog_payload_version}`}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {shown && <CatalogPayloadVersionPreview version={shown} />}
        </div>
      )}
    </Drawer>
  );
};

export default ThreatArsenalCatalogDrawer;
