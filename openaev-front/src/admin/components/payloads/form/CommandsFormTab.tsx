import { Add, DeleteOutlined } from '@mui/icons-material';
import { Button, IconButton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect } from 'react';
import { Controller, useFieldArray, useFormContext } from 'react-hook-form';

import FileLoader from '../../../../components/fields/FileLoader';
import PlatformFieldController from '../../../../components/fields/PlatformFieldController';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadArgument } from '../../../../utils/api-types';
import PayloadArgumentsField from './PayloadArgumentsField';

interface Props { disabledPayloadType?: boolean }

const CommandsFormTab = ({ disabledPayloadType = false }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control, setValue, getValues, watch } = useFormContext();
  const payloadType = watch('payload_type');

  const { fields: argumentsFields, append: argumentsAppend, remove: argumentsRemove } = useFieldArray({
    control,
    name: 'payload_arguments',
  });
  const { fields: prerequisitesFields, append: prerequisitesAppend, remove: prerequisitesRemove } = useFieldArray({
    control,
    name: 'payload_prerequisites',
  });

  useEffect(() => {
    if (payloadType != 'Command') {
      const args = getValues('payload_arguments') ?? [];
      const argToRemoveIndex: number[] = [];
      (args as unknown as PayloadArgument[]).forEach((arg, index) => {
        if (arg.type == 'targeted-asset') {
          argToRemoveIndex.push(index);
        }
      });

      argumentsRemove(argToRemoveIndex);
    }
    if (!(payloadType == 'Command' || payloadType == 'Executable')) {
      setValue('payload_execution_arch', 'ALL_ARCHITECTURES'); // Automatically set arch to 'all'
    } else if (!disabledPayloadType && payloadType === 'Executable' && getValues('payload_execution_arch') === 'ALL_ARCHITECTURES') {
      setValue('payload_execution_arch', '');
    }
  }, [payloadType]);

  const payloadTypesItems = [
    {
      value: 'Command',
      label: t('Command Line'),
    },
    {
      value: 'Executable',
      label: t('Executable'),
    },
    {
      value: 'FileDrop',
      label: t('File Drop'),
    },
    {
      value: 'DnsResolution',
      label: t('DNS Resolution'),
    },
  ];

  const architecturesItems = [
    {
      value: 'x86_64',
      label: t('x86_64'),
    },
    {
      value: 'arm64',
      label: t('arm64'),
    },
    ...(payloadType !== 'Executable'
      ? [{
          value: 'ALL_ARCHITECTURES',
          label: t('ALL_ARCHITECTURES'),
        }]
      : []),
  ];

  const executorsItems = [
    {
      value: 'psh',
      label: t('PowerShell'),
    }, {
      value: 'cmd',
      label: t('Command Prompt'),
    }, {
      value: 'bash',
      label: t('Bash'),
    }, {
      value: 'sh',
      label: t('Sh'),
    },
  ];

  return (
    <>
      <SelectFieldController name="payload_type" label={t('Type')} items={payloadTypesItems} required disabled={disabledPayloadType} />
      {payloadType && payloadType != '' && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: theme.spacing(2),
        }}
        >
          <SelectFieldController disabled={!(payloadType == 'Command' || payloadType == 'Executable')} name="payload_execution_arch" label={t('Architecture')} items={architecturesItems} required />
          <PlatformFieldController name="payload_platforms" label={t('Platforms')} required />
        </div>
      )}

      {payloadType === 'Command' && (
        <>
          <Typography variant="h5" marginTop={theme.spacing(3)}>{t('Attack command')}</Typography>
          <SelectFieldController name="command_executor" label={t('Executor')} items={executorsItems} required />
          <TextFieldController variant="outlined" multiline rows={3} name="command_content" required />
        </>
      )}

      {payloadType === 'Executable' && (
        <Controller
          control={control}
          name="executable_file"
          render={({ field: { onChange, value }, fieldState: { error } }) => (
            <FileLoader
              name="executable_file"
              label={t('Executable file')}
              setFieldValue={(_name, document) => {
                onChange(document?.id);
              }}
              initialValue={{ id: value }}
              InputLabelProps={{ required: true }}
              error={!!error}
            />
          )}
        />
      )}

      {payloadType === 'FileDrop' && (
        <Controller
          control={control}
          name="file_drop_file"
          render={({ field: { onChange, value }, fieldState: { error } }) => (
            <FileLoader
              name="file_drop_file"
              label={t('File to drop')}
              setFieldValue={(_name, document) => {
                onChange(document?.id);
              }}
              initialValue={{ id: value }}
              InputLabelProps={{ required: true }}
              error={!!error}
            />
          )}
        />
      )}

      {payloadType === 'DnsResolution' && (
        <TextFieldController name="dns_resolution_hostname" label={t('Hostname')} required />
      )}

      {payloadType && payloadType != '' && (
        <>
          {/* ARGUMENTS */}
          <Typography variant="h5" marginTop={theme.spacing(3)}>{t('Arguments')}</Typography>
          {argumentsFields.map((argsField, argIndex) => (
            <PayloadArgumentsField
              key={argsField.id}
              canSelectTargetAsset={payloadType == 'Command'}
              argumentName={`payload_arguments.${argIndex}`}
              onArgumentRemoveClick={() => argumentsRemove(argIndex)}
            />
          ))}
          <Button
            variant="outlined"
            onClick={() => {
              argumentsAppend({
                type: 'text',
                key: '',
                default_value: '',
              });
            }}
            style={{
              width: '100%',
              height: theme.spacing(4),
            }}
          >
            <Add fontSize="small" />
            {t('New argument')}
          </Button>

          {/* PREREQUISITE */}
          <Typography variant="h5" marginTop={theme.spacing(3)}>{t('Prerequisites')}</Typography>
          {prerequisitesFields.map((prerequisitesField, prerequisitesIndex) => (
            <div
              style={{
                display: 'flex',
                gap: theme.spacing(1),
              }}
              key={prerequisitesField.id}
            >
              <SelectFieldController name={`payload_prerequisites.${prerequisitesIndex}.executor` as const} label={t('Executor')} items={executorsItems} required />
              <TextFieldController name={`payload_prerequisites.${prerequisitesIndex}.get_command` as const} label={t('Get command')} required />
              <TextFieldController name={`payload_prerequisites.${prerequisitesIndex}.check_command` as const} label={t('Check command')} />
              <IconButton
                onClick={() => prerequisitesRemove(prerequisitesIndex)}
                size="small"
                color="primary"
              >
                <DeleteOutlined />
              </IconButton>
            </div>
          ))}
          <Button
            variant="outlined"
            onClick={() => {
              prerequisitesAppend({
                executor: 'psh',
                get_command: '',
                check_command: '',
              });
            }}
            style={{
              width: '100%',
              height: theme.spacing(4),
            }}
          >
            <Add fontSize="small" />
            {t('New prerequisite')}
          </Button>

          {/* CLEANUP */}
          <Typography variant="h5" marginTop={theme.spacing(3)}>{t('Cleanup command')}</Typography>
          <SelectFieldController name="payload_cleanup_executor" label={t('Executor')} items={executorsItems} />
          <TextFieldController variant="outlined" multiline rows={3} name="payload_cleanup_command" />
        </>
      )}
    </>
  );
};

export default CommandsFormTab;
