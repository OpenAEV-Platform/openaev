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
import { DeleteOutlined, GroupsOutlined, PersonOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type AxiosResponse } from 'axios';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { addAssetOwner, deleteAssetOwner } from '../../../../actions/assets/asset-owner-actions';
import { searchTeamsAsOption } from '../../../../actions/teams/team-actions';
import { searchPlayersAsOption } from '../../../../actions/users/User';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type Option } from '../../../../utils/api-types';
import {
  ASSET_OWNER_ROLES,
  type AssetOwnerKind,
  type AssetOwnerRole,
  assetOwnerRoleLabel,
  type AssetOwnerRow,
  isOwnerCandidate,
  relationSourceLabel,
} from './asset-owners';

interface Props {
  open: boolean;
  onClose: () => void;
  assetId: string;
  assetName: string;
  rows: AssetOwnerRow[];
  /** Called after every add or removal, so the overview card re-reads the owners. */
  onChange: () => void;
}

// The only place owners are edited, like the links: the add form (role, person or team, who)
// above the removable list. The overview keeps a read-only summary.
const AssetOwnersDrawer: FunctionComponent<Props> = ({ open, onClose, assetId, assetName, rows, onChange }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const [role, setRole] = useState<AssetOwnerRole>('OWNED_BY');
  const [kind, setKind] = useState<AssetOwnerKind>('person');
  const [selected, setSelected] = useState<Option | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [options, setOptions] = useState<Option[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    const search = kind === 'team' ? searchTeamsAsOption(inputValue) : searchPlayersAsOption(inputValue);
    search
      .then((response: AxiosResponse<Option[]>) => setOptions(response.data ?? []))
      .catch(() => setOptions([]));
  }, [inputValue, open, kind]);

  const candidates = useMemo(
    () => options.filter(option => option.id && isOwnerCandidate(rows, role, kind, option.id)),
    [options, rows, role, kind],
  );

  const handleKind = (next: AssetOwnerKind) => {
    setKind(next);
    setSelected(null);
    setInputValue('');
  };

  const handleAdd = () => {
    if (!selected?.id) {
      return;
    }
    setSaving(true);
    addAssetOwner(assetId, {
      asset_owner_role: role,
      ...(kind === 'team' ? { asset_owner_team_id: selected.id } : { asset_owner_user_id: selected.id }),
    })
      .then(() => {
        setSelected(null);
        setInputValue('');
        onChange();
      })
      .finally(() => setSaving(false));
  };

  const handleRemove = (row: AssetOwnerRow) => {
    deleteAssetOwner(assetId, row.id).then(() => onChange());
  };

  return (
    <Drawer open={open} handleClose={onClose} title={t('Manage owners')} additionalTitle={assetName}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        paddingTop: 8,
      }}
      >
        <section
          aria-label={t('Add an owner')}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}
        >
          <div data-testid="asset-owner-role-field">
            <Select value={role} onValueChange={next => setRole(next as AssetOwnerRole)}>
              <SelectLabel>{t('Ownership')}</SelectLabel>
              <SelectTrigger className="w-full" aria-label={t('Ownership')}>
                <span>{t(assetOwnerRoleLabel(role))}</span>
              </SelectTrigger>
              <SelectContent style={{ maxWidth: 360 }}>
                {ASSET_OWNER_ROLES.map(ownerRole => (
                  <SelectItem key={ownerRole.value} value={ownerRole.value}>
                    <span>
                      <Text variant="content-base" as="div">{t(ownerRole.label)}</Text>
                      <Text variant="content-caption" as="div" className="text-default-secondary" style={{ whiteSpace: 'normal' }}>
                        {t(ownerRole.description)}
                      </Text>
                    </span>
                  </SelectItem>
                ))}
              </SelectContent>
              <SelectHelperText>{t('An asset can have several owners of each kind.')}</SelectHelperText>
            </Select>
          </div>
          <div data-testid="asset-owner-kind-field">
            <Select value={kind} onValueChange={next => handleKind(next as AssetOwnerKind)}>
              <SelectLabel>{t('Owner type')}</SelectLabel>
              <SelectTrigger className="w-full" aria-label={t('Owner type')}>
                <span>{kind === 'team' ? t('Team') : t('Person')}</span>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="person">{t('Person')}</SelectItem>
                <SelectItem value="team">{t('Team')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div data-testid="asset-owner-target-field">
            <Combobox<Option>
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
              onValueChange={next => setSelected((next as Option | null) ?? null)}
            >
              <ComboboxLabel>{kind === 'team' ? t('Team') : t('Person')}</ComboboxLabel>
              <ComboboxField>
                <ComboboxInput />
                <ComboboxControls>
                  <ComboboxTrigger />
                </ComboboxControls>
              </ComboboxField>
              <ComboboxContent emptyMessage={t('No available options')} />
              <ComboboxHelperText>{t('Searched by name.')}</ComboboxHelperText>
            </Combobox>
          </div>
          <div style={{
            display: 'flex',
            justifyContent: 'flex-end',
          }}
          >
            <Button type="button" onClick={handleAdd} disabled={!selected?.id || saving}>
              {t('Add owner')}
            </Button>
          </div>
        </section>

        <section aria-label={t('Owners')}>
          <Text variant="content-base-bold" as="h3" style={{ margin: 0 }}>
            {`${t('Owners')} (${rows.length})`}
          </Text>
          {rows.length === 0 && (
            <Text variant="content-base" className="text-default-secondary" as="p">
              {t('No owner')}
            </Text>
          )}
          {rows.length > 0 && (
            <ul
              style={{
                listStyle: 'none',
                margin: 0,
                marginTop: 8,
                padding: 0,
              }}
            >
              {rows.map((row) => {
                const source = relationSourceLabel(row.source);
                return (
                  <li
                    key={row.id}
                    data-testid="asset-owner-drawer-row"
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'auto auto minmax(0, 1fr) auto auto',
                      gap: 8,
                      alignItems: 'center',
                      paddingTop: 4,
                      paddingBottom: 4,
                      borderBottom: `1px solid ${theme.palette.divider}`,
                    }}
                  >
                    {row.kind === 'team'
                      ? <GroupsOutlined fontSize="small" aria-label={t('Team')} />
                      : <PersonOutlined fontSize="small" aria-label={t('Person')} />}
                    <Chip label={t(assetOwnerRoleLabel(row.role))} />
                    <Text variant="content-base" className="truncate">{row.name}</Text>
                    {source ? <Chip label={t(source)} /> : <span />}
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span className="inline-flex">
                          <IconButton
                            icon={<DeleteOutlined fontSize="small" />}
                            aria-label={`${t('Remove owner')}: ${row.name}`}
                            onClick={() => handleRemove(row)}
                            priority="tertiary"
                            size="sm"
                          />
                        </span>
                      </TooltipTrigger>
                      <TooltipContent>{t('Remove owner')}</TooltipContent>
                    </Tooltip>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>
    </Drawer>
  );
};

export default AssetOwnersDrawer;
