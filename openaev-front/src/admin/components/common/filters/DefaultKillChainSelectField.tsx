import { MenuItem } from '@mui/material';
import { type CSSProperties } from 'react';
import { type Control, type FieldValues, type Path } from 'react-hook-form';

import { fetchKillChainPhases } from '../../../../actions/KillChainPhase';
import SelectField from '../../../../components/fields/SelectField';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import killChainLabel from './killChainLabel';
import useKillChains from './useKillChains';

interface Props<T extends FieldValues> {
  name: Path<T>;
  control: Control<T>;
  defaultValue?: string;
  style?: CSSProperties;
}

/**
 * "Default kill chain" select for the scenario / simulation configuration forms: which kill
 * chain the overview's kill chain results section displays first. Empty means automatic
 * (MITRE ATT&CK first). The user's own selection on the overview, remembered in local storage,
 * still overrides this default.
 */
const DefaultKillChainSelectField = <T extends FieldValues>({ name, control, defaultValue, style }: Props<T>) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchKillChainPhases());
  });
  const { killChains } = useKillChains();
  // A stored default pointing to a kill chain that no longer exists on the platform stays
  // selectable (raw name) so the select never renders an out-of-range value.
  const options = defaultValue && !killChains.includes(defaultValue)
    ? [...killChains, defaultValue]
    : killChains;
  return (
    <SelectField
      variant="standard"
      fullWidth={true}
      name={name}
      label={t('Default kill chain')}
      style={style}
      control={control}
      defaultValue={defaultValue ?? ''}
      displayEmpty
    >
      <MenuItem value="">{t('Automatic')}</MenuItem>
      {options.map(chain => (
        <MenuItem key={chain} value={chain}>{killChainLabel(chain)}</MenuItem>
      ))}
    </SelectField>
  );
};

export default DefaultKillChainSelectField;
