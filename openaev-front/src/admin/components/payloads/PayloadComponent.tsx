import { AttachmentOutlined } from '@mui/icons-material';
import { Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, Fragment, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import AttackPatternChip from '../../../components/AttackPatternChip';
import { useFormatter } from '../../../components/i18n';
import ItemCopy from '../../../components/ItemCopy';
import ItemDomains from '../../../components/ItemDomains';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import {
  type AttackPattern,
  type Command,
  type DnsResolution,
  type Document,
  type Executable,
  type FileDrop,
  type Payload as PayloadType,
  type PayloadArgument,
  type PayloadPrerequisite,
} from '../../../utils/api-types';
import { emptyFilled } from '../../../utils/String';
import DocumentType from '../components/documents/DocumentType';

const useStyles = makeStyles()(theme => ({
  payloadContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
  },
  allWidth: { gridColumn: 'span 2' },
  payloadInfo: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: theme.spacing(2),
  },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },

}));
const inlineStyles: Record<string, CSSProperties> = {
  document_icon: {
    float: 'left',
    width: '5%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_name: {
    float: 'left',
    width: '45%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_type: {
    float: 'left',
    width: '20%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

interface Props {
  selectedPayload: PayloadType | null;
  documentsMap: Record<string, Document> | null;
}

const PayloadComponent: FunctionComponent<Props> = ({ selectedPayload, documentsMap }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  const { attackPatternsMap }: { attackPatternsMap: ReturnType<AttackPatternHelper['getAttackPatternsMap']> } = useHelper((helper: AttackPatternHelper) => ({ attackPatternsMap: helper.getAttackPatternsMap() }));
  const getAttackCommand = (payload: PayloadType | null): string => {
    if (!payload) return '';

    switch (payload.payload_type) {
      case 'Command':
        return (payload as Command).command_content || '';
      case 'DnsResolution':
        return (payload as DnsResolution).dns_resolution_hostname || '';
      case 'FileDrop':
        return (payload as FileDrop).file_drop_file || '';
      case 'Executable':
        return (payload as Executable).executable_file || '';
      default:
        return '';
    }
  };

  const getArgumentContent = (argument: PayloadArgument): string => {
    if (argument?.type === 'document' && documentsMap && documentsMap[argument.default_value]) {
      return documentsMap[argument.default_value].document_name;
    }
    return argument.default_value;
  };

  return (
    <div className={classes.payloadContainer}>
      <Typography variant="h2" gutterBottom>{selectedPayload?.payload_name}</Typography>
      <Typography
        variant="body2"
        gutterBottom
      >
        {emptyFilled(selectedPayload?.payload_description)}
      </Typography>

      <div className={classes.payloadInfo}>
        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('Platforms')}
          </Typography>
          {(selectedPayload?.payload_platforms ?? []).length === 0 ? (
            <PlatformIcon platform={t('No inject in this scenario')} tooltip width={25} />
          ) : selectedPayload?.payload_platforms?.map(
            platform => <PlatformIcon key={platform} platform={platform} tooltip width={25} marginRight={theme.spacing(2)} />,
          )}
        </div>

        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('Attack patterns')}
          </Typography>

          {selectedPayload?.payload_attack_patterns && selectedPayload?.payload_attack_patterns.length === 0 ? '-' : selectedPayload?.payload_attack_patterns?.map((attackPatternId: string) => attackPatternsMap?.[attackPatternId]).map((attackPattern: AttackPattern) => (
            <AttackPatternChip key={attackPattern.attack_pattern_id} attackPattern={attackPattern}></AttackPatternChip>
          ))}
        </div>

        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('Architecture')}
          </Typography>
          <Typography
            variant="body2"
            gutterBottom
          >
            {selectedPayload && t(selectedPayload?.payload_execution_arch)}
          </Typography>
        </div>

        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('External Id')}
          </Typography>

          <Typography
            variant="body2"
            gutterBottom
          >
            {emptyFilled(selectedPayload?.payload_external_id)}
          </Typography>
        </div>
        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('Tags')}
          </Typography>
          <ItemTags
            variant="reduced-view"
            tags={selectedPayload?.payload_tags}
          />
        </div>
        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('payload_domains')}
          </Typography>
          {selectedPayload && (
            <ItemDomains
              variant="list"
              domains={selectedPayload?.payload_domains}
            />
          )}
        </div>
        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('Type')}
          </Typography>

          <Typography
            variant="body2"
            gutterBottom
          >
            {t(emptyFilled(selectedPayload?.payload_type))}
          </Typography>
        </div>

      </div>
      {selectedPayload?.payload_type === 'Command' && (
        <>
          <div>
            <Typography
              variant="h3"
              gutterBottom
            >
              {t('Command executor')}
            </Typography>
            <Typography
              variant="body2"
              gutterBottom
            >
              {emptyFilled(selectedPayload.command_executor)}
            </Typography>
          </div>
          <div>
            <Typography
              variant="h3"
              gutterBottom
            >
              {t('Attack command')}
            </Typography>
            <pre>
              <ItemCopy content={getAttackCommand(selectedPayload)} />
            </pre>
          </div>
        </>
      )}
      {documentsMap && selectedPayload?.payload_type === 'FileDrop' && (
        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('File to drop')}
          </Typography>

          <div style={inlineStyles.document_icon}><AttachmentOutlined /></div>
          <div className={classes.bodyItem} style={inlineStyles.document_name}>
            {documentsMap[selectedPayload.file_drop_file]?.document_name}
          </div>
          <div className={classes.bodyItem} style={inlineStyles.document_type}>
            <DocumentType type={documentsMap[selectedPayload.file_drop_file]?.document_type} variant="list" />
          </div>

        </div>
      )}
      {selectedPayload?.payload_type === 'DnsResolution' && (
        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('Hostname')}
          </Typography>

          <Typography
            variant="body2"
            gutterBottom
          >
            {selectedPayload.dns_resolution_hostname}

          </Typography>
        </div>
      )}
      {documentsMap && selectedPayload?.payload_type === 'Executable' && (
        <div>
          <Typography
            variant="h3"
            gutterBottom
          >
            {t('Executable file')}
          </Typography>

          <div style={inlineStyles.document_icon}><AttachmentOutlined /></div>
          <div className={classes.bodyItem} style={inlineStyles.document_name}>
            {documentsMap[selectedPayload.executable_file]?.document_name}
          </div>
          <div className={classes.bodyItem} style={inlineStyles.document_type}>
            <DocumentType type={documentsMap[selectedPayload.executable_file]?.document_type} variant="list" />
          </div>

        </div>
      )}
      <div>
        <Typography
          variant="h3"
          gutterBottom
        >
          {t('Arguments')}
        </Typography>
        {
          !selectedPayload?.payload_arguments?.length ? (<div>-</div>)
            : (
                <TableContainer component={Paper}>
                  <Table sx={{ minWidth: 650 }}>
                    <TableHead>
                      <TableRow sx={{
                        textTransform: 'uppercase',
                        fontWeight: 'bold',
                      }}
                      >
                        <TableCell width="30%">{t('Type')}</TableCell>
                        <TableCell width="30%">{t('Key')}</TableCell>
                        <TableCell width="30%">{t('Default value')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {selectedPayload?.payload_arguments?.map((argument: PayloadArgument) => {
                        return (
                          <Fragment key={argument.key}>
                            <TableRow>
                              <TableCell>
                                {argument.type}
                              </TableCell>
                              <TableCell>
                                {argument.key}
                              </TableCell>
                              <TableCell>
                                <pre>
                                  <ItemCopy content={getArgumentContent(argument)} />
                                </pre>
                              </TableCell>
                            </TableRow>
                          </Fragment>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )
        }
      </div>
      <div>
        <Typography
          variant="h3"
          gutterBottom
        >
          {t('Prerequisites')}
        </Typography>
        {
          selectedPayload?.payload_prerequisites && selectedPayload?.payload_prerequisites.length === 0 ? (<div>-</div>)
            : (
                <TableContainer component={Paper}>
                  <Table sx={{
                    minWidth: 650,
                    justifyContent: 'center',
                  }}
                  >
                    <TableHead>
                      <TableRow sx={{
                        textTransform: 'uppercase',
                        fontWeight: 'bold',
                      }}
                      >
                        <TableCell width="30%">{t('Command executor')}</TableCell>
                        <TableCell width="30%">{t('Get command')}</TableCell>
                        <TableCell width="30%">{t('Check command')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {selectedPayload?.payload_prerequisites?.map((prerequisite: PayloadPrerequisite) => (
                        <TableRow
                          key={prerequisite.executor}
                        >
                          <TableCell>
                            {prerequisite.executor}
                          </TableCell>
                          <TableCell>
                            <pre>
                              <ItemCopy content={prerequisite.get_command} />
                            </pre>
                          </TableCell>
                          <TableCell>
                            {prerequisite.check_command !== undefined
                              ? (
                                  <pre>
                                    <ItemCopy content={prerequisite.check_command} />
                                  </pre>
                                ) : '-'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )
        }
      </div>
      <div>
        <Typography
          variant="h3"
          gutterBottom
        >
          {t('Cleanup executor')}
        </Typography>
        <Typography
          variant="body2"
          gutterBottom
        >
          {emptyFilled(selectedPayload?.payload_cleanup_executor)}
        </Typography>
      </div>
      <div>
        <Typography
          variant="h3"
          gutterBottom
        >
          {t('Cleanup command')}
        </Typography>
        {selectedPayload?.payload_cleanup_command && selectedPayload?.payload_cleanup_command.length > 0
          ? <pre><ItemCopy content={selectedPayload?.payload_cleanup_command} /></pre> : (<div>-</div>)}

      </div>
    </div>
  );
};

export default PayloadComponent;
