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
import { ArrowBackOutlined, ArrowForwardOutlined, DeleteOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type AxiosResponse } from 'axios';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { addAssetLink, deleteAssetLink } from '../../../../actions/assets/asset-link-actions';
import { searchAssetsAsOption } from '../../../../actions/assets/endpoint-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type AssetOptionOutput } from '../../../../utils/api-types';
import { ASSET_LINK_TYPES, type AssetLinkRow, type AssetLinkType, assetLinkTypeLabel, isLinkCandidate } from './asset-links';

interface Props {
  open: boolean;
  onClose: () => void;
  assetId: string;
  assetName: string;
  rows: AssetLinkRow[];
  /** Called after every add or removal, so the overview card re-reads the links. */
  onChange: () => void;
}

// The only place asset links are edited: the add form (type, asset) above the removable list. The
// overview keeps a read-only summary (Sam, 20 Nov 2026: "edition clearly separated, like in a drawer").
const AssetLinksDrawer: FunctionComponent<Props> = ({ open, onClose, assetId, assetName, rows, onChange }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const [linkType, setLinkType] = useState<AssetLinkType>('CONNECTS_TO');
  const [selected, setSelected] = useState<AssetOptionOutput | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [options, setOptions] = useState<AssetOptionOutput[]>([]);
  const [saving, setSaving] = useState(false);

  // Server-side search over the whole inventory, refreshed on each keystroke while the drawer is
  // open; a failure (no ASSETS access) leaves an empty list rather than an endless loading state.
  useEffect(() => {
    if (!open) {
      return;
    }
    searchAssetsAsOption(inputValue)
      .then((response: AxiosResponse<AssetOptionOutput[]>) => setOptions(response.data ?? []))
      .catch(() => setOptions([]));
  }, [inputValue, open]);

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
        onChange();
      })
      .finally(() => setSaving(false));
  };

  const handleRemove = (row: AssetLinkRow) => {
    deleteAssetLink(row.sourceId, row.targetId, row.type).then(() => onChange());
  };

  return (
    <Drawer open={open} handleClose={onClose} title={t('Manage links')} additionalTitle={assetName}>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
        pt: 1,
      }}
      >
        <Box
          component="section"
          aria-label={t('Add a link')}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
        >
          <Box data-testid="asset-link-type-field">
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
                  {/* A focusable trigger: the tooltip opens on keyboard focus too. */}
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span
                        tabIndex={0}
                        role="img"
                        aria-label={t('About link types')}
                        className="inline-flex rounded-sm text-icon-highlight focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-1 focus-visible:ring-offset-focus"
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
          <Box data-testid="asset-link-target-field">
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
          <Box sx={{
            display: 'flex',
            justifyContent: 'flex-end',
          }}
          >
            <Button type="button" onClick={handleAdd} disabled={!selected?.id || saving}>
              {t('Add link')}
            </Button>
          </Box>
        </Box>

        <Box component="section" aria-label={t('Linked assets')}>
          <Text variant="content-base-bold" as="h3" style={{ margin: 0 }}>
            {`${t('Linked assets')} (${rows.length})`}
          </Text>
          {rows.length === 0 && (
            <Text variant="content-base" className="text-default-secondary" as="p">
              {t('No linked asset')}
            </Text>
          )}
          {rows.length > 0 && (
            <Box
              component="ul"
              sx={{
                listStyle: 'none',
                m: 0,
                mt: 1,
                p: 0,
              }}
            >
              {rows.map(row => (
                <Box
                  component="li"
                  key={row.key}
                  data-testid="asset-link-drawer-row"
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: 'auto auto minmax(0, 1fr) auto',
                    gap: 1,
                    alignItems: 'center',
                    py: 0.5,
                    borderBottom: `1px solid ${theme.palette.divider}`,
                  }}
                >
                  {row.outgoing
                    ? <ArrowForwardOutlined fontSize="small" aria-label={t('Outgoing')} />
                    : <ArrowBackOutlined fontSize="small" aria-label={t('Incoming')} />}
                  <Chip label={t(assetLinkTypeLabel(row.type))} />
                  <Text variant="content-base" className="truncate">{row.otherName}</Text>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span className="inline-flex">
                        <IconButton
                          icon={<DeleteOutlined fontSize="small" />}
                          aria-label={`${t('Remove link')}: ${row.otherName}`}
                          onClick={() => handleRemove(row)}
                          priority="tertiary"
                          size="sm"
                        />
                      </span>
                    </TooltipTrigger>
                    <TooltipContent>{t('Remove link')}</TooltipContent>
                  </Tooltip>
                </Box>
              ))}
            </Box>
          )}
        </Box>
      </Box>
    </Drawer>
  );
};

export default AssetLinksDrawer;
