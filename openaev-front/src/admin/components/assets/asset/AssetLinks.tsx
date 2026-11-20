import { Button, Chip, Text } from '@filigran/design-system';
import { ArrowBackOutlined, ArrowForwardOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type AxiosResponse } from 'axios';
import { type FunctionComponent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { fetchAssetLinks } from '../../../../actions/assets/asset-link-actions';
import chipLinkClassName from '../../../../components/common/chips/chipLink';
import { SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type AssetLinkOutput } from '../../../../utils/api-types';
import { type AssetLinkRow, assetLinkTypeLabel, groupAssetLinkRows, previewRows, RELATIONSHIP_PREVIEW, toAssetLinkRows } from './asset-links';
import AssetLinksDrawer from './AssetLinksDrawer';

interface Props {
  assetId: string;
  assetName: string;
  canManage: boolean;
  onChange?: () => void;
}

// The Relationships card of the asset overview: read-only and compact, one line per link type and
// direction with the linked assets as chips. Every edit happens in the Manage links drawer, never in
// the overview (Sam, 20 Nov 2026: "overview is overview, edition is edition").
const AssetLinks: FunctionComponent<Props> = ({ assetId, assetName, canManage, onChange }) => {
  const { t } = useFormatter();

  const [links, setLinks] = useState<AssetLinkOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const loadLinks = useCallback(() => {
    setLoading(true);
    fetchAssetLinks(assetId)
      .then((response: AxiosResponse<AssetLinkOutput[]>) => setLinks(response.data ?? []))
      .catch(() => setLinks([]))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    loadLinks();
  }, [loadLinks]);

  const rows: AssetLinkRow[] = useMemo(() => toAssetLinkRows(links, assetId), [links, assetId]);
  const groups = useMemo(() => groupAssetLinkRows(rows), [rows]);

  const handleChange = () => {
    loadLinks();
    onChange?.();
  };

  const toggle = (key: string) => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const title = rows.length > 0 ? `${t('Relationships')} (${rows.length})` : t('Relationships');

  return (
    <SectionBlock
      title={title}
      action={canManage
        ? (
            <Button type="button" priority="secondary" size="sm" onClick={() => setDrawerOpen(true)}>
              {t('Manage links')}
            </Button>
          )
        : undefined}
    >
      {loading && <Loader variant="inElement" />}
      {!loading && rows.length === 0 && (
        <Text variant="content-base" className="text-default-secondary">
          {t('No linked asset')}
        </Text>
      )}
      {!loading && rows.length > 0 && (
        <Box
          component="dl"
          aria-label={`${t('Relationships')}: ${assetName}`}
          sx={{
            m: 0,
            display: 'grid',
            gridTemplateColumns: 'minmax(140px, max-content) minmax(0, 1fr)',
            columnGap: 2,
            rowGap: 1,
            alignItems: 'center',
          }}
        >
          {groups.map((group) => {
            const isExpanded = expanded.has(group.key);
            const { shown, hidden } = previewRows(group.rows, isExpanded);
            return (
              <Box key={group.key} sx={{ display: 'contents' }} data-testid="asset-relationship-group">
                <Box
                  component="dt"
                  sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 0.5,
                    color: 'text.secondary',
                  }}
                >
                  {group.outgoing
                    ? <ArrowForwardOutlined fontSize="small" aria-label={t('Outgoing')} />
                    : <ArrowBackOutlined fontSize="small" aria-label={t('Incoming')} />}
                  <Text variant="content-base">{`${t(assetLinkTypeLabel(group.type))} (${group.rows.length})`}</Text>
                </Box>
                <Box
                  component="dd"
                  sx={{
                    m: 0,
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 0.75,
                    alignItems: 'center',
                    minWidth: 0,
                  }}
                >
                  {shown.map(row => (
                    <Link key={row.key} to={`/admin/assets/${row.otherId}`} className={chipLinkClassName}>
                      <Chip label={row.otherName} />
                    </Link>
                  ))}
                  {(hidden > 0 || (isExpanded && group.rows.length > RELATIONSHIP_PREVIEW)) && (
                    <Button type="button" priority="tertiary" size="sm" onClick={() => toggle(group.key)}>
                      {isExpanded ? t('Show less') : t('+{count} more', { count: hidden })}
                    </Button>
                  )}
                </Box>
              </Box>
            );
          })}
        </Box>
      )}
      {canManage && (
        <AssetLinksDrawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          assetId={assetId}
          assetName={assetName}
          rows={rows}
          onChange={handleChange}
        />
      )}
    </SectionBlock>
  );
};

export default AssetLinks;
