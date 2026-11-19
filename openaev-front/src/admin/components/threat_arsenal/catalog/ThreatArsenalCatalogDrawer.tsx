import { Button, Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchCatalogPayloadVersions, installCatalogPayload } from '../../../../actions/catalog/payload-catalog-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogPayloadOutput } from '../../../../utils/api-types';
import CatalogPayloadVersionPreview from './CatalogPayloadVersionPreview';

interface Props {
  payload: CatalogPayloadOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstalled: (version: CatalogPayloadOutput) => void;
}

// A payload of the Available tab: its latest version first, any other offered version on demand.
const ThreatArsenalCatalogDrawer: FunctionComponent<Props> = ({ payload, canInstall, onClose, onInstalled }) => {
  const { t } = useFormatter();
  const [versions, setVersions] = useState<CatalogPayloadOutput[]>([]);
  const [shownVersion, setShownVersion] = useState('');
  const [installing, setInstalling] = useState(false);

  useEffect(() => {
    setVersions(payload ? [payload] : []);
    setShownVersion(payload?.catalog_payload_version ?? '');
    if (payload) {
      fetchCatalogPayloadVersions(payload.catalog_payload_external_id)
        .then(setVersions)
        .catch(() => undefined);
    }
  }, [payload]);

  const shown = versions.find(version => version.catalog_payload_version === shownVersion);

  const install = () => {
    if (!shown) return;
    setInstalling(true);
    installCatalogPayload(shown.catalog_payload_external_id, shown.catalog_payload_version)
      .then(() => {
        onInstalled(shown);
        onClose();
      })
      .catch(() => undefined)
      .finally(() => setInstalling(false));
  };

  return (
    <Drawer
      open={payload !== null}
      handleClose={onClose}
      title={t('Action information')}
      headerActions={canInstall && shown && (
        <Button loading={installing} onClick={install}>{t('Install this version')}</Button>
      )}
    >
      {payload && (
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
