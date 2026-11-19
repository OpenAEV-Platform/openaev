import {
  Button,
  Chip,
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Icon,
  IconButton,
  Select,
  SelectContent,
  SelectHelperText,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  Text,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@filigran/design-system';
import { DeleteOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type AxiosResponse } from 'axios';
import { type FunctionComponent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { addAssetLink, deleteAssetLink, fetchAssetLinks } from '../../../../actions/assets/asset-link-actions';
import { searchAssetsAsOption } from '../../../../actions/assets/endpoint-actions';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type AssetLinkOutput, type AssetOptionOutput } from '../../../../utils/api-types';
import { ASSET_LINK_TYPES, type AssetLinkRow, type AssetLinkType, assetLinkTypeLabel, FIELD_LABEL_BLOCK_PX, isLinkCandidate, toAssetLinkRows } from './asset-links';

interface Props {
  assetId: string;
  assetName: string;
  canManage: boolean;
  onChange?: () => void;
}

// Asset-to-asset links of the detail page: what this asset hosts, uses, connects to... and what
// points at it. Each link is a LINKED_TO pivot in the graph, so onChange lets the page reload it.
const AssetLinks: FunctionComponent<Props> = ({ assetId, assetName, canManage, onChange }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const [links, setLinks] = useState<AssetLinkOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [linkType, setLinkType] = useState<AssetLinkType>('CONNECTS_TO');
  const [selected, setSelected] = useState<AssetOptionOutput | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [options, setOptions] = useState<AssetOptionOutput[]>([]);
  const [saving, setSaving] = useState(false);

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

  // Server-side search over the whole inventory, refreshed on each keystroke; failures (no ASSETS
  // access) leave an empty list rather than an endless loading state.
  useEffect(() => {
    if (!canManage) {
      return;
    }
    searchAssetsAsOption(inputValue)
      .then((response: AxiosResponse<AssetOptionOutput[]>) => setOptions(response.data ?? []))
      .catch(() => setOptions([]));
  }, [inputValue, canManage]);

  const candidates = useMemo(
    () => options.filter(option => option.id && isLinkCandidate(rows, assetId, option.id, linkType)),
    [options, rows, assetId, linkType],
  );

  const handleAdd = () => {
    if (!selected?.id) {
      return;
    }
    setSaving(true);
    addAssetLink(assetId, {
      asset_link_type: linkType,
      linked_asset_id: selected.id,
    })
      .then(() => {
        setSelected(null);
        setInputValue('');
        loadLinks();
        onChange?.();
      })
      .finally(() => setSaving(false));
  };

  const handleRemove = (row: AssetLinkRow) => {
    deleteAssetLink(row.sourceId, row.targetId, row.type).then(() => {
      loadLinks();
      onChange?.();
    });
  };

  const renderEnd = (id: string, name: string) => {
    if (id === assetId) {
      return <Text variant="content-base-bold">{name}</Text>;
    }
    return (
      <Text variant="content-base" className="truncate">
        <Link to={`/admin/assets/${id}`}>{name}</Link>
      </Text>
    );
  };

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      {canManage && (
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'minmax(200px, 1fr) minmax(280px, 2fr) auto',
          gap: 2,
          alignItems: 'start',
        }}
        >
          {/* One container per field: the DS label stays above its own field instead of taking a
              grid cell of its own (#9448, UX round 1 B2). */}
          <Box sx={{ minWidth: 0 }} data-testid="asset-link-type-field">
            <Select value={linkType} onValueChange={next => setLinkType(next as AssetLinkType)}>
              <SelectLabel>
                <Box
                  component="span"
                  sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 0.5,
                  }}
                >
                  {t('Link type')}
                  {/* A focusable trigger, as PlayerForm's info bubble: the tooltip opens on keyboard
                      focus too (#9448, UX round 2 B2). */}
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span
                        tabIndex={0}
                        role="img"
                        aria-label={t('About link types')}
                        className="inline-flex text-icon-highlight"
                        style={{ cursor: 'help' }}
                      >
                        <Icon name="info" size={16} aria-hidden />
                      </span>
                    </TooltipTrigger>
                    <TooltipContent className="max-w-xs">
                      {t('A link records how two assets relate. It becomes a Linked to edge in the graph, so attack paths can follow it. Example: a web server depends on a domain controller.')}
                    </TooltipContent>
                  </Tooltip>
                </Box>
              </SelectLabel>
              {/* The trigger shows the short label; the options carry their one-line meaning. */}
              <SelectTrigger className="w-full" aria-label={t('Link type')}>
                <span>{t(assetLinkTypeLabel(linkType))}</span>
              </SelectTrigger>
              <SelectContent style={{ maxWidth: 360 }}>
                {ASSET_LINK_TYPES.map(type => (
                  <SelectItem key={type.value} value={type.value}>
                    <span>
                      <Text variant="content-base" as="div">{t(type.label)}</Text>
                      <Text variant="content-caption" as="div" className="text-default-secondary" style={{ whiteSpace: 'normal' }}>
                        {t(type.description)}
                      </Text>
                    </span>
                  </SelectItem>
                ))}
              </SelectContent>
              <SelectHelperText>{t('How this asset relates to the asset you pick next.')}</SelectHelperText>
            </Select>
          </Box>
          <Box sx={{ minWidth: 0 }} data-testid="asset-link-target-field">
            <Combobox<AssetOptionOutput>
              options={candidates}
              value={selected}
              getOptionLabel={option => option.label ?? ''}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              inputValue={inputValue}
              onInputChange={(search, meta) => {
                if (meta.cause === 'type') {
                  setInputValue(search ?? '');
                }
              }}
              onValueChange={next => setSelected((next as AssetOptionOutput | null) ?? null)}
            >
              <ComboboxLabel>{t('Linked asset')}</ComboboxLabel>
              <ComboboxField>
                <ComboboxInput />
                <ComboboxControls>
                  <ComboboxTrigger />
                </ComboboxControls>
              </ComboboxField>
              <ComboboxContent emptyMessage={t('No available options')} />
              <ComboboxHelperText>{t('Any asset of the inventory, searched by name.')}</ComboboxHelperText>
            </Combobox>
          </Box>
          {/* On the field row: the top padding is the DS label block above each field (label line
              plus its gap, measured 26 px in the lab), not a spacing step (#9448, UX round 2 B1). */}
          <Box sx={{ pt: `${FIELD_LABEL_BLOCK_PX}px` }} data-testid="asset-link-add">
            <Button type="button" onClick={handleAdd} disabled={!selected?.id || saving}>
              {t('Add link')}
            </Button>
          </Box>
        </Box>
      )}

      {loading && <Loader variant="inElement" />}
      {!loading && rows.length === 0 && (
        <Text variant="content-base" className="text-default-secondary">
          {t('No linked asset')}
        </Text>
      )}
      {!loading && rows.length > 0 && (
        <Box
          component="ul"
          aria-label={`${t('Linked assets')}: ${assetName}`}
          sx={{
            listStyle: 'none',
            m: 0,
            p: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
          }}
        >
          {rows.map(row => (
            <Box
              component="li"
              key={row.key}
              sx={{
                display: 'grid',
                gridTemplateColumns: canManage ? 'minmax(0, 1fr) auto minmax(0, 1fr) auto' : 'minmax(0, 1fr) auto minmax(0, 1fr)',
                gap: 2,
                alignItems: 'center',
                padding: theme.spacing(0.5, 1),
                borderRadius: 1,
                backgroundColor: theme.palette.background.paper,
              }}
            >
              {renderEnd(row.sourceId, row.sourceName)}
              <Chip label={t(assetLinkTypeLabel(row.type))} />
              {renderEnd(row.targetId, row.targetName)}
              {canManage && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span className="inline-flex">
                      <IconButton
                        icon={<DeleteOutlined fontSize="small" />}
                        aria-label={t('Remove link')}
                        onClick={() => handleRemove(row)}
                        priority="tertiary"
                        size="sm"
                      />
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>{t('Remove link')}</TooltipContent>
                </Tooltip>
              )}
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
};

export default AssetLinks;
