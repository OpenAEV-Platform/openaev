import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  IconButton,
} from '@filigran/design-system';
import { AddOutlined, RouteOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import * as R from 'ramda';
import { useContext, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../actions/attack_patterns/attackpattern-helper';
import { addAttackPattern } from '../../actions/AttackPattern';
import { type UserHelper } from '../../actions/helper';
import { type KillChainPhaseHelper } from '../../actions/kill_chain_phases/killchainphase-helper';
import AttackPatternForm from '../../admin/components/settings/attack_patterns/AttackPatternForm';
import { useHelper } from '../../store';
import { type AttackPattern, type AttackPatternCreateInput } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import { type Option } from '../../utils/Option';
import { AbilityContext, Can } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';

const useStyles = makeStyles()(theme => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: theme.spacing(1),
  },
  autoCompleteIndicator: { display: 'none' },
}));

interface Props {
  name: string;
  label: string;
  hideAddButton?: boolean;
  required?: boolean;
}

type AttackPatternCreateInputForm = Omit<AttackPatternCreateInput, 'attack_pattern_kill_chain_phases'> & { attack_pattern_kill_chain_phases?: Option[] };

const AttackPatternFieldController = ({ name, label, hideAddButton = false, required = false }: Props) => {
  const { control } = useFormContext();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  // Fetching data
  const { attackPatterns, killChainPhasesMap } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper & UserHelper) => ({
    attackPatterns: helper.getAttackPatterns(),
    killChainPhasesMap: helper.getKillChainPhasesMap(),
  }));

  const [attackPatternCreation, setAttackPatternCreation] = useState(false);

  const handleOpenAttackPatternCreation = () => {
    setAttackPatternCreation(true);
  };

  const handleCloseAttackPatternCreation = () => {
    setAttackPatternCreation(false);
  };

  const onSubmit = (data: AttackPatternCreateInputForm, values: string[], onChange: (id: string[]) => void) => {
    const inputValues: AttackPatternCreateInput = {
      ...data,
      attack_pattern_kill_chain_phases: data.attack_pattern_kill_chain_phases?.map(k => k.id),
    };
    dispatch(addAttackPattern(inputValues)).then((result: {
      result: string;
      entities: { attackpatterns: Record<string, AttackPattern> };
    }) => {
      if (result.result) {
        const newAttackPattern = result.entities.attackpatterns[result.result];
        const newAttackPatterns = [...values, newAttackPattern.attack_pattern_id];
        onChange(newAttackPatterns);
        return handleCloseAttackPatternCreation();
      }
      return result;
    });
  };

  const attackPatternsOptions = attackPatterns.map(
    (n: AttackPattern) => {
      const killChainPhase = R.head(n.attack_pattern_kill_chain_phases);
      const killChainName = killChainPhase ? killChainPhasesMap[killChainPhase]?.phase_kill_chain_name ?? null : null;
      return {
        id: n.attack_pattern_id,
        label: killChainName ? `[${killChainName}] [${n.attack_pattern_external_id}] ${n.attack_pattern_name}` : `[${n.attack_pattern_external_id}] ${n.attack_pattern_name}`,
      };
    },
  );

  const openCreate = () => {
    if (ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS)) {
      handleOpenAttackPatternCreation();
    }
  };
  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => (
        <>
          <Combobox<Option>
            multiple
            options={attackPatternsOptions}
            openOnFocus
            value={attackPatternsOptions.filter((a: Option) => (value ?? [])?.includes(a.id)) ?? []}
            onValueChange={(pattern) => {
              onChange((pattern as Option[]).map(p => p.id));
            }}
            getOptionLabel={option => option.label}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            clearable={false}
            error={!!error}
            renderOption={option => (
              <>
                <div className={classes.icon}>
                  <RouteOutlined />
                </div>
                <div className={classes.text}>{option.label}</div>
              </>
            )}
          >
            <ComboboxLabel>
              {label}
              {required ? ' *' : ''}
            </ComboboxLabel>
            <ComboboxField
              adornment={ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS) && !hideAddButton
                ? (
                    <IconButton
                      size="sm"
                      priority="tertiary"
                      onClick={() => openCreate()}
                      aria-label={t('Create a new attack pattern')}
                      icon={<AddOutlined fontSize="small" />}
                    />
                  )
                : undefined}
            >
              <ComboboxChips />
              <ComboboxInput />
              <ComboboxControls>
                <ComboboxClear />
                <ComboboxTrigger />
              </ComboboxControls>
            </ComboboxField>
            <ComboboxContent emptyMessage={t('No available options')} />
            {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
          </Combobox>
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
            <Dialog
              open={attackPatternCreation}
              onClose={handleCloseAttackPatternCreation}
              PaperProps={{ elevation: 1 }}
            >
              <DialogTitle>{t('Create a new attack pattern')}</DialogTitle>
              <DialogContent>
                <AttackPatternForm
                  onSubmit={(data: AttackPatternCreateInputForm) => onSubmit(data, value ?? [], onChange)}
                  handleClose={handleCloseAttackPatternCreation}
                  initialValues={{ attack_pattern_kill_chain_phases: [] }}
                />
              </DialogContent>
            </Dialog>
          </Can>
        </>
      )}
    />
  );
};

export default AttackPatternFieldController;
