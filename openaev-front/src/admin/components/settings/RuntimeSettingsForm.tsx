import { Button, Chip, Icon, Switch, Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, type SyntheticEvent, useEffect, useMemo, useState } from 'react';

import TextFieldFds from '../../../components/fields/TextFieldFds';
import { useFormatter } from '../../../components/i18n';
import type { RuntimeSettingOutput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import validateRuntimeValue from './runtimeSettingsValidation';

/** What each runtime setting reads as, in the order the card shows them. */
const DESCRIPTORS: Record<string, {
  label: string;
  unit?: string;
  help: string;
}> = {
  inject_execution_threshold_minutes: {
    label: 'Inject execution threshold',
    unit: 'minutes',
    help: 'Time a pending inject waits for its result before it ends in error, in minutes (1 to 1440). Example: 60 for an Nmap scan of more than 100 hosts. Used by inject finalization and, at the next injector registration, by the implant timeout.',
  },
  agent_max_simultaneous_jobs: {
    label: 'Jobs per agent poll',
    unit: 'jobs',
    help: 'Maximum number of jobs an agent receives at each poll (1 to 100). Lower it to spare busy endpoints, raise it to run large simulations faster.',
  },
  agent_queue_threshold: {
    label: 'Agent queue threshold',
    unit: 'jobs',
    help: 'Pending jobs after which an agent is considered overloaded and is skipped by new injects (0 disables the check, up to 10000).',
  },
  agent_auto_update_enabled: {
    label: 'Agent auto-update',
    help: 'When off, agents are not upgraded when a new platform version is deployed. Enterprise Edition only.',
  },
};

const RUNTIME_SETTING_ORDER = Object.keys(DESCRIPTORS);

interface RuntimeSettingsFormProps {
  settings: RuntimeSettingOutput[];
  onSubmit: (values: Record<string, string | number | boolean>) => Promise<unknown> | void;
  canNotManage: boolean;
}

type Values = Record<string, string>;

const toValues = (settings: RuntimeSettingOutput[]): Values => Object.fromEntries(settings.map(s => [s.key, s.value]));

const RuntimeSettingsForm: FunctionComponent<RuntimeSettingsFormProps> = ({ settings, onSubmit, canNotManage }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ordered = useMemo(
    () => [...settings]
      .filter(s => DESCRIPTORS[s.key])
      .sort((a, b) => RUNTIME_SETTING_ORDER.indexOf(a.key) - RUNTIME_SETTING_ORDER.indexOf(b.key)),
    [settings],
  );
  const initial = useMemo(() => toValues(ordered), [ordered]);
  const [values, setValues] = useState<Values>(initial);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => setValues(initial), [initial]);

  const errors = Object.fromEntries(ordered.map(s => [s.key, validateRuntimeValue(s, values[s.key] ?? '', t)]));
  const dirtyKeys = ordered.filter(s => !s.locked && values[s.key] !== initial[s.key]).map(s => s.key);
  const hasError = dirtyKeys.some(key => errors[key] !== undefined);

  const handleSubmit = async (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (dirtyKeys.length === 0 || hasError) {
      return;
    }
    setSubmitting(true);
    try {
      const payload: Record<string, string | number | boolean> = {};
      ordered.filter(s => dirtyKeys.includes(s.key)).forEach((s) => {
        payload[s.key] = s.type === 'boolean' ? values[s.key] === 'true' : Number(values[s.key]);
      });
      await onSubmit(payload);
    } finally {
      setSubmitting(false);
    }
  };

  const infoTooltip = (text: string, label: string): ReactNode => (
    <Tooltip>
      <TooltipTrigger asChild>
        <span
          tabIndex={0}
          aria-label={`${t('Information')}: ${label}`}
          className="text-icon-highlight inline-flex rounded-sm focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-1 focus-visible:ring-offset-focus"
        >
          <Icon name="info" size={16} aria-hidden />
        </span>
      </TooltipTrigger>
      <TooltipContent style={{ maxWidth: 360 }}>{t(text)}</TooltipContent>
    </Tooltip>
  );

  const envTile = (setting: RuntimeSettingOutput): ReactNode => (
    <Tooltip>
      <TooltipTrigger asChild>
        <span
          tabIndex={0}
          aria-label={t('Set by the environment variable {name}, read-only', { name: setting.env_var })}
          className="inline-flex rounded-sm focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-1 focus-visible:ring-offset-focus"
          data-testid={`runtime-setting-env-${setting.key}`}
        >
          <Chip label="env" size="sm" severity="info" />
        </span>
      </TooltipTrigger>
      <TooltipContent>{t('Set by the environment variable {name}, read-only', { name: setting.env_var })}</TooltipContent>
    </Tooltip>
  );

  return (
    <TooltipProvider delayDuration={200}>
      <form
        noValidate
        id="runtimeSettingsForm"
        onSubmit={handleSubmit}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
          height: '100%',
        }}
      >
        {ordered.map((setting) => {
          const descriptor = DESCRIPTORS[setting.key];
          const readOnly = canNotManage || setting.locked;
          const label = t(descriptor.label);
          if (setting.type === 'boolean') {
            return (
              <div
                key={setting.key}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: theme.spacing(1),
                  minHeight: 36,
                }}
              >
                <span style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: theme.spacing(1),
                }}
                >
                  <span id={`runtime-setting-label-${setting.key}`} style={{ fontSize: 14 }}>{label}</span>
                  {infoTooltip(descriptor.help, label)}
                  {setting.locked && envTile(setting)}
                </span>
                <Switch
                  aria-labelledby={`runtime-setting-label-${setting.key}`}
                  disabled={readOnly}
                  checked={values[setting.key] === 'true'}
                  onCheckedChange={checked => setValues(v => ({
                    ...v,
                    [setting.key]: String(checked === true),
                  }))}
                />
              </div>
            );
          }
          return (
            <div
              key={setting.key}
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: theme.spacing(1),
              }}
            >
              <TextFieldFds
                className="w-full"
                type="number"
                min={setting.min}
                max={setting.max}
                step={1}
                name={setting.key}
                label={label}
                endText={descriptor.unit ? t(descriptor.unit) : undefined}
                infoTooltip={infoTooltip(descriptor.help, label)}
                value={values[setting.key] ?? ''}
                readOnly={readOnly}
                disabled={canNotManage}
                error={!!errors[setting.key]}
                helperText={errors[setting.key] ?? (setting.source === 'default' ? t('Default value') : undefined)}
                onChange={e => setValues(v => ({
                  ...v,
                  [setting.key]: e.target.value,
                }))}
              />
              {setting.locked && <div style={{ paddingTop: 26 }}>{envTile(setting)}</div>}
            </div>
          );
        })}
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          marginTop: 'auto',
        }}
        >
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
            <Button type="submit" disabled={dirtyKeys.length === 0 || hasError || submitting}>
              {t('Update')}
            </Button>
          </Can>
        </div>
      </form>
    </TooltipProvider>
  );
};

export default RuntimeSettingsForm;
