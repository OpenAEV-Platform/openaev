import { Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@filigran/design-system';
import { type FunctionComponent, useState } from 'react';

import MarketplaceItemDrawer from '../../../../components/common/marketplace/drawer/MarketplaceItemDrawer';
import { useFormatter } from '../../../../components/i18n';
import { type AvailablePayloadActionOutput } from '../../../../utils/api-types';
import CatalogPayloadInstallAction from './CatalogPayloadInstallAction';
import CatalogPayloadVersionPreview from './CatalogPayloadVersionPreview';
import useCatalogPayloadVersions from './useCatalogPayloadVersions';

interface Props {
  action: AvailablePayloadActionOutput;
  name: string;
  installed: boolean;
  installing: boolean;
  canInstall: boolean;
  onClose: () => void;
  onInstall: (version: string) => void;
}

// A payload of the Available tab: its latest version first, any other offered version on demand.
const ThreatArsenalCatalogDrawer: FunctionComponent<Props> = ({ action, name, installed, installing, canInstall, onClose, onInstall }) => {
  const { t } = useFormatter();
  const versions = useCatalogPayloadVersions(action.action_id);
  const [shownVersion, setShownVersion] = useState(action.action_payload_version);
  const shown = versions.find(version => version.catalog_payload_version === shownVersion);

  return (
    <MarketplaceItemDrawer
      title={name}
      onClose={onClose}
      actions={shown && (
        <CatalogPayloadInstallAction
          installed={installed}
          installing={installing}
          canInstall={canInstall}
          onInstall={() => onInstall(shown.catalog_payload_version)}
          label={t('Install this version')}
        />
      )}
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
    </MarketplaceItemDrawer>
  );
};

export default ThreatArsenalCatalogDrawer;
