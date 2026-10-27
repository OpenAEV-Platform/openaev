import { Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchCatalogPayloadVersions, installCatalogPayload } from '../../../../actions/catalog/payload-catalog-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogItemOutput } from '../../../../utils/api-types';
import PayloadVersionSelect from '../PayloadVersionSelect';
import CatalogPayloadVersionSummary from './CatalogPayloadVersionSummary';

interface Props {
  item: CatalogItemOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstalled: () => void;
}

// A payload of the Available tab: its latest version first, any other version on demand.
const ThreatArsenalCatalogDrawer: FunctionComponent<Props> = ({ item, canInstall, onClose, onInstalled }) => {
  const { t } = useFormatter();
  const [versions, setVersions] = useState<CatalogItemOutput[]>([]);
  const [shownVersion, setShownVersion] = useState('');
  const [installing, setInstalling] = useState(false);
  const [versionsLoading, setVersionsLoading] = useState(false);

  useEffect(() => {
    setVersions(item ? [item] : []);
    setShownVersion(item?.catalog_item_version ?? '');
    if (item) {
      setVersionsLoading(true);
      fetchCatalogPayloadVersions(item.catalog_item_external_id)
        .then(setVersions)
        .finally(() => setVersionsLoading(false));
    }
  }, [item]);

  const shown = versions.find(version => version.catalog_item_version === shownVersion);

  const install = () => {
    if (!item || !shownVersion) return;
    setInstalling(true);
    installCatalogPayload(item.catalog_item_external_id, shownVersion)
      .then(() => {
        onInstalled();
        onClose();
      })
      .finally(() => setInstalling(false));
  };

  return (
    <Drawer open={item !== null} handleClose={onClose} title={t('Action information')}>
      {item === null ? <></> : (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 16,
          }}
          >
            <Text variant="title-sm" as="h2" style={{ margin: 0 }}>{shown?.catalog_item_name ?? item.catalog_item_name}</Text>
            <PayloadVersionSelect
              value={shownVersion}
              options={versions.map((version, index) => ({
                version: version.catalog_item_version,
                latest: index === 0,
                installed: (version.catalog_item_installed_versions ?? []).includes(version.catalog_item_version),
              }))}
              onChange={setShownVersion}
              loading={versionsLoading}
            />
          </div>
          {shown && <CatalogPayloadVersionSummary item={shown} canInstall={canInstall} installing={installing} onInstall={install} />}
        </div>
      )}
    </Drawer>
  );
};

export default ThreatArsenalCatalogDrawer;
