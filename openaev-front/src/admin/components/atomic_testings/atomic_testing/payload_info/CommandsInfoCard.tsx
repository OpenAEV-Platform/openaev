import { TerminalOutlined } from '@mui/icons-material';
import { Box, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment } from 'react';

import CodeBlock from '../../../../../components/common/overview/CodeBlock';
import Field from '../../../../../components/common/overview/Field';
import KeyValueChip from '../../../../../components/common/overview/KeyValueChip';
import Section from '../../../../../components/common/overview/Section';
import { useFormatter } from '../../../../../components/i18n';
import ItemCopy from '../../../../../components/ItemCopy';
import type {
  PayloadArgument,
  PayloadCommandBlock,
  PayloadPrerequisite,
  StatusPayloadOutput,
} from '../../../../../utils/api-types';
import { emptyFilled, formatPrimitiveTypeLabel } from '../../../../../utils/String';

interface Props { payloadOutput?: StatusPayloadOutput }

const CommandsInfoCard = ({ payloadOutput }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const headerCellSx = {
    fontWeight: 700,
    textTransform: 'uppercase',
    fontSize: 10.5,
    color: 'text.secondary',
    letterSpacing: '0.06em',
  } as const;

  if (!payloadOutput) {
    return (
      <Section title={t('Commands')} icon={<TerminalOutlined fontSize="small" />}>
        <Typography variant="body1">{t('No data available')}</Typography>
      </Section>
    );
  }
  return (
    <Section title={t('Commands')} icon={<TerminalOutlined fontSize="small" />}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        {payloadOutput.payload_type === 'Command' && (
          <>
            <Field label="Command executor">
              {!payloadOutput.payload_command_blocks?.length ? '-' : payloadOutput.payload_command_blocks?.map((commandBlock: PayloadCommandBlock) =>
                (
                  <Typography key={commandBlock.command_executor} variant="body2">
                    {emptyFilled(commandBlock.command_executor)}
                  </Typography>
                ))}
            </Field>
            {payloadOutput.payload_obfuscator && (
              <Field label="Obfuscator">
                <Typography key="obfuscator" variant="body2">{payloadOutput.payload_obfuscator}</Typography>
              </Field>
            )}
            <Field label="Attack command">
              {!payloadOutput.payload_command_blocks?.length ? '-' : payloadOutput.payload_command_blocks?.map((commandBlock: PayloadCommandBlock) => (
                <CodeBlock key={commandBlock.command_content} content={commandBlock.command_content ?? ' '} />
              ))}
            </Field>
          </>
        )}

        {payloadOutput.payload_type === 'Executable' && (
          <>
            <Field label="Executable files">
              <Typography variant="body1">
                {payloadOutput.executable_file?.document_name ?? '-'}
              </Typography>
            </Field>
            <Field label="Architecture">
              <Typography variant="body1">
                {payloadOutput.executable_arch}
              </Typography>
            </Field>
          </>
        )}

        {payloadOutput.payload_type === 'FileDrop' && (
          <Field label="Executable files">
            <Typography variant="body1">
              {payloadOutput.file_drop_file?.document_name ?? '-'}
            </Typography>
          </Field>
        )}

        {payloadOutput.payload_type === 'DnsResolution' && (
          <Field label="Dns resolution hostname">
            <Typography variant="body1">
              {payloadOutput.dns_resolution_hostname ?? '-'}
            </Typography>
          </Field>
        )}

        <Field label="Arguments">
          {payloadOutput.payload_arguments?.length === 0 ? '-' : (
            <Box sx={{
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
              overflow: 'auto',
            }}
            >
              <Table
                size="small"
                aria-label="Table to show payload's arguments"
                sx={{
                  'minWidth': 650,
                  '& .MuiTableCell-root': {
                    fontSize: 12,
                    borderColor: theme.palette.divider,
                  },
                }}
              >
                <TableHead>
                  <TableRow>
                    <TableCell sx={headerCellSx}>{t('Type')}</TableCell>
                    <TableCell sx={headerCellSx}>{t('Key')}</TableCell>
                    <TableCell sx={headerCellSx}>{t('Default value')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {payloadOutput.payload_arguments?.map((argument: PayloadArgument) => (
                    <TableRow key={argument.key}>
                      <TableCell><KeyValueChip label="" value={formatPrimitiveTypeLabel(argument.type)} /></TableCell>
                      <TableCell sx={{
                        fontFamily: 'Consolas, monaco, monospace',
                        fontWeight: 500,
                      }}
                      >
                        {argument.key}
                      </TableCell>
                      <TableCell><ItemCopy content={argument.default_value} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </Field>

        <Field label="Prerequisites">
          {payloadOutput.payload_prerequisites?.length === 0 ? '-' : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(1.5),
            }}
            >
              {payloadOutput.payload_prerequisites?.map((prerequisite: PayloadPrerequisite, index) => (
                <Box
                  key={`${prerequisite.executor}-${index}`}
                  sx={{
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 1,
                    padding: 1.5,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1,
                  }}
                >
                  {prerequisite.executor && (
                    <div>
                      <KeyValueChip label={t('Executor')} value={prerequisite.executor} />
                    </div>
                  )}
                  <Field label="Get command">
                    <CodeBlock content={prerequisite.get_command} />
                  </Field>
                  {prerequisite.check_command !== undefined && prerequisite.check_command !== '' && (
                    <Field label="Check command">
                      <CodeBlock content={prerequisite.check_command} />
                    </Field>
                  )}
                </Box>
              ))}
            </div>
          )}
        </Field>

        <Field label="Cleanup executor">
          <Typography variant="body1">
            {emptyFilled(payloadOutput.payload_cleanup_executor)}
          </Typography>
        </Field>

        <Field label="Cleanup command">
          {payloadOutput.payload_command_blocks?.map((commandBlock: PayloadCommandBlock) => (
            !commandBlock.payload_cleanup_command?.length
              ? '-'
              : (
                  <Fragment key={commandBlock.command_content}>
                    {commandBlock.payload_cleanup_command?.map((cleanupCommand: string) => (
                      <CodeBlock
                        key={cleanupCommand}
                        content={cleanupCommand}
                      />
                    ))}
                  </Fragment>
                )
          ))}
        </Field>
      </div>
    </Section>
  );
};

export default CommandsInfoCard;
