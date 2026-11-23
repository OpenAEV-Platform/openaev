import { Button, Chip, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type AxiosResponse } from 'axios';
import { type FunctionComponent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { fetchAssetOwners } from '../../../../actions/assets/asset-owner-actions';
import chipLinkClassName from '../../../../components/common/chips/chipLink';
import { SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type AssetOwnerOutput } from '../../../../utils/api-types';
import { assetOwnerRoleLabel, type AssetOwnerRow, groupAssetOwnerRows, ownerLink, toAssetOwnerRows } from './asset-owners';
import AssetOwnersDrawer from './AssetOwnersDrawer';

interface Props {
  assetId: string;
  assetName: string;
  canManage: boolean;
}

// The Owners card of the asset overview: read-only, one line per role (business owner, technical
// owner) with the persons and teams as chips; edited in the Manage owners drawer only.
const AssetOwners: FunctionComponent<Props> = ({ assetId, assetName, canManage }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [owners, setOwners] = useState<AssetOwnerOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const loadOwners = useCallback(() => {
    setLoading(true);
    fetchAssetOwners(assetId)
      .then((response: AxiosResponse<AssetOwnerOutput[]>) => setOwners(response.data ?? []))
      .catch(() => setOwners([]))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    loadOwners();
  }, [loadOwners]);

  const rows: AssetOwnerRow[] = useMemo(() => toAssetOwnerRows(owners), [owners]);
  const groups = useMemo(() => groupAssetOwnerRows(rows), [rows]);

  const title = rows.length > 0 ? `${t('Owners')} (${rows.length})` : t('Owners');

  return (
    <SectionBlock
      title={title}
      action={canManage
        ? (
            <Button type="button" priority="secondary" size="sm" onClick={() => setDrawerOpen(true)}>
              {t('Manage owners')}
            </Button>
          )
        : undefined}
    >
      {loading && <Loader variant="inElement" />}
      {!loading && rows.length === 0 && (
        <Text variant="content-base" className="text-default-secondary">
          {t('No owner')}
        </Text>
      )}
      {!loading && rows.length > 0 && (
        <dl
          aria-label={`${t('Owners')}: ${assetName}`}
          style={{
            margin: 0,
            display: 'grid',
            gridTemplateColumns: 'minmax(140px, max-content) minmax(0, 1fr)',
            columnGap: 16,
            rowGap: 8,
            alignItems: 'center',
          }}
        >
          {groups.map(group => (
            <div key={group.role} style={{ display: 'contents' }} data-testid="asset-owner-group">
              <dt style={{ color: theme.palette.text.secondary }}>
                <Text variant="content-base">{`${t(assetOwnerRoleLabel(group.role))} (${group.rows.length})`}</Text>
              </dt>
              <dd
                style={{
                  margin: 0,
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 6,
                  alignItems: 'center',
                  minWidth: 0,
                }}
              >
                {group.rows.map(row => (
                  <Link key={row.id} to={ownerLink(row)} className={chipLinkClassName}>
                    <Chip label={row.name} />
                  </Link>
                ))}
              </dd>
            </div>
          ))}
        </dl>
      )}
      {canManage && (
        <AssetOwnersDrawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          assetId={assetId}
          assetName={assetName}
          rows={rows}
          onChange={loadOwners}
        />
      )}
    </SectionBlock>
  );
};

export default AssetOwners;
