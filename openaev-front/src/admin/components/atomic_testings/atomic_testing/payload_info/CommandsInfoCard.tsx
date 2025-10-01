import { Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import ItemCopy from '../../../../../components/ItemCopy';
import type {
  PayloadArgument,
  PayloadCommandBlock,
  PayloadPrerequisite,
  StatusPayloadOutput,
} from '../../../../../utils/api-types';
import { emptyFilled } from '../../../../../utils/String';

const useStyles = makeStyles()(theme => ({
  paperContainer: { '& > *:nth-child(even)': { marginBottom: theme.spacing(2) } },
  tableContainer: { backgroundColor: theme.palette.background.paperInCard },
}));

interface Props { payloadOutput?: StatusPayloadOutput }

const CommandsInfoCard = ({ payloadOutput }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  if (!payloadOutput) {
    return (
      <Paper className="paper" variant="outlined">
        <Typography variant="body1">{t('No data available')}</Typography>
      </Paper>
    );
  }

  return (
    <Paper className={`paper ${classes.paperContainer}`} variant="outlined">
      {payloadOutput.payload_type === 'Command' && (
        <>
          <Typography variant="h3" gutterBottom>{t('Command executor')}</Typography>
          {!payloadOutput.payload_command_blocks?.length ? '-' : payloadOutput.payload_command_blocks?.map((commandBlock: PayloadCommandBlock) =>
            (
              <Typography key={commandBlock.command_executor} variant="body2">
                {emptyFilled(commandBlock.command_executor)}
              </Typography>
            ))}
          {payloadOutput.payload_obfuscator && (
            <>
              <Typography variant="h3" gutterBottom>{t('Obfuscator')}</Typography>
              <Typography key="obfuscator" variant="body2">{payloadOutput.payload_obfuscator}</Typography>
            </>
          )}
          <Typography variant="h3" gutterBottom>{t('Attack command')}</Typography>
          {!payloadOutput.payload_command_blocks?.length ? '-' : payloadOutput.payload_command_blocks?.map((commandBlock: PayloadCommandBlock) => (
            <pre key={commandBlock.command_content}>
              <ItemCopy content={commandBlock.command_content ?? ' '} />
            </pre>
          ))}
        </>
      )}

      {payloadOutput.payload_type === 'Executable' && (
        <>
          <Typography variant="h3" gutterBottom>{t('Executable files')}</Typography>
          <Typography variant="body1">
            {payloadOutput.executable_file?.document_name ?? '-'}
          </Typography>
          <Typography variant="h3" gutterBottom>{t('Architecture')}</Typography>
          <Typography variant="body1">
            {payloadOutput.executable_arch}
          </Typography>
        </>
      )}

      {payloadOutput.payload_type === 'FileDrop' && (
        <>
          <Typography variant="h3" gutterBottom>{t('Executable files')}</Typography>
          <Typography variant="body1">
            {payloadOutput.file_drop_file?.document_name ?? '-'}
          </Typography>
        </>
      )}

      {payloadOutput.payload_type === 'DnsResolution' && (
        <>
          <Typography variant="h3" gutterBottom>{t('Dns resolution hostname')}</Typography>
          <Typography variant="body1">
            {payloadOutput.dns_resolution_hostname ?? '-'}
          </Typography>
        </>
      )}

      <Typography variant="h3" gutterBottom>{t('Arguments')}</Typography>
      {payloadOutput.payload_arguments?.length === 0 ? '-' : (
        <TableContainer className={classes.tableContainer} component={Paper}>
          <Table sx={{ minWidth: 650 }} aria-label="Table to show payload's arguments">
            <TableHead sx={{
              textTransform: 'uppercase',
              fontWeight: 'bold',
            }}
            >
              <TableCell width="30%">{t('Type')}</TableCell>
              <TableCell width="30%">{t('Key')}</TableCell>
              <TableCell width="30%">{t('Default value')}</TableCell>
            </TableHead>
            <TableBody>
              {payloadOutput.payload_arguments?.map((argument: PayloadArgument) => (
                <>
                  <TableRow key={argument.key}>
                    <TableCell>{argument.type}</TableCell>
                    <TableCell>{argument.key}</TableCell>
                    <TableCell>
                      <pre><ItemCopy content={argument.default_value} /></pre>
                    </TableCell>
                  </TableRow>
                </>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Typography variant="h3" gutterBottom>{t('Prerequisites')}</Typography>
      {payloadOutput.payload_prerequisites?.length === 0 ? '-' : (
        <TableContainer className={classes.tableContainer} component={Paper}>
          <Table sx={{ minWidth: 650 }} aria-label="Table to show payload's prerequisites">
            <TableHead sx={{
              textTransform: 'uppercase',
              fontWeight: 'bold',
            }}
            >
              <TableCell width="30%">{t('Command executor')}</TableCell>
              <TableCell width="30%">{t('Get command')}</TableCell>
              <TableCell width="30%">{t('Check command')}</TableCell>
            </TableHead>
            <TableBody>
              {payloadOutput.payload_prerequisites?.map((prerequisite: PayloadPrerequisite) => (
                <>
                  <TableRow key={prerequisite.executor}>
                    <TableCell>{prerequisite.executor}</TableCell>
                    <TableCell>
                      <pre>
                        <ItemCopy content={prerequisite.get_command} />
                      </pre>
                    </TableCell>
                    <TableCell>
                      {prerequisite.check_command !== undefined && prerequisite.check_command !== '' ? (
                        <pre><ItemCopy content={prerequisite.check_command} /></pre>) : '-'}
                    </TableCell>
                  </TableRow>
                </>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Typography variant="h3" gutterBottom>{t('Cleanup executor')}</Typography>
      <Typography variant="body1">
        {emptyFilled(payloadOutput.payload_cleanup_executor)}
      </Typography>

      <Typography variant="h3" gutterBottom>{t('Cleanup command')}</Typography>
      {payloadOutput.payload_command_blocks?.map((commandBlock: PayloadCommandBlock) => (
        !commandBlock.payload_cleanup_command?.length
          ? '-'
          : (
              <pre key={commandBlock.command_content}>
                { commandBlock.payload_cleanup_command?.map((cleanupCommand: string) => (
                  <ItemCopy
                    key={cleanupCommand}
                    content={
                      cleanupCommand
                    }
                  />
                ))}
              </pre>
            )
      ))}
    </Paper>
  );
};

export default CommandsInfoCard;
