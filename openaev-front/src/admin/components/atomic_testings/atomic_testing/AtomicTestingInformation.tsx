import { Chip, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchDocuments } from '../../../../actions/Document';
import type { DocumentHelper } from '../../../../actions/helper';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import { type InjectResultOverviewOutput, type KillChainPhaseSimple } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { isNotEmptyField } from '../../../../utils/utils';
import InjectIcon from '../../common/injects/InjectIcon';

const useStyles = makeStyles()(() => ({
  chip: {
    fontSize: 12,
    height: 25,
    margin: '0 7px 7px 0',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 180,
  },
  paper: { minHeight: '100%' },
}));

interface Props { injectResultOverviewOutput: InjectResultOverviewOutput }

const AtomicTestingInformation: FunctionComponent<Props> = ({ injectResultOverviewOutput }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t, tPick, fldt } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();

  const { documentMap } = useHelper((helper: DocumentHelper) => ({ documentMap: helper.getDocumentsMap() }));
  useDataLoader(() => {
    dispatch(fetchDocuments());
  });

  // utils
  const type = useMemo(() => {
    const payload = injectResultOverviewOutput.inject_injector_contract?.injector_contract_payload;
    return payload?.payload_collector_type ?? payload?.payload_type ?? injectResultOverviewOutput.inject_type;
  }, [injectResultOverviewOutput]);

  const documentNames = useMemo(() => {
    const docs = injectResultOverviewOutput.injects_documents ?? [];
    if (docs.length === 0) return ['-'];
    return docs.map(docId => documentMap[docId]?.document_name ?? '-');
  }, [injectResultOverviewOutput.injects_documents, documentMap]);

  return (
    <Paper sx={{ p: theme.spacing(2) }} classes={{ root: classes.paper }} variant="outlined">
      <div style={{
        display: 'grid',
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr 1fr',
        gridTemplateAreas: `
        "description description description"
        "type execution documents"
        "tags platforms killchainphases"
        `,
        rowGap: theme.spacing(2),
      }}
      >
        <div style={{ gridArea: 'description' }}>
          <Typography variant="h3" gutterBottom>
            {t('Description')}
          </Typography>
          <ExpandableMarkdown source={injectResultOverviewOutput.inject_description} limit={300} />
        </div>
        <div style={{
          gridArea: 'type',
          minWidth: 0,
        }}
        >
          <Typography variant="h3" gutterBottom>
            {t('Type')}
          </Typography>
          <div style={{
            display: 'flex',
            gap: theme.spacing(1),
            marginRight: theme.spacing(1),
          }}
          >
            <InjectIcon
              isPayload={isNotEmptyField(injectResultOverviewOutput.inject_injector_contract?.injector_contract_payload)}
              type={type}
            />
            <Tooltip title={tPick(injectResultOverviewOutput.inject_injector_contract?.injector_contract_labels)}>
              <div style={{
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
              >
                {tPick(injectResultOverviewOutput.inject_injector_contract?.injector_contract_labels)}
              </div>
            </Tooltip>
          </div>
        </div>
        <div style={{ gridArea: 'execution' }}>
          <Typography variant="h3" gutterBottom>
            {t('Last execution date')}
          </Typography>
          {fldt(injectResultOverviewOutput?.inject_status?.tracking_end_date)}
        </div>
        <div style={{ gridArea: 'documents' }}>
          <Typography variant="h3" gutterBottom>
            {t('Documents')}
          </Typography>
          {documentNames.map((documentName) => {
            return (
              <Typography key={documentName} variant="body1">
                {documentName ?? '-'}
              </Typography>
            );
          })}
        </div>
        <div style={{ gridArea: 'tags' }}>
          <Typography variant="h3" gutterBottom>
            {t('Tags')}
          </Typography>
          <ItemTags tags={injectResultOverviewOutput.injects_tags} limit={10} />
        </div>
        <div style={{ gridArea: 'platforms' }}>
          <Typography variant="h3" gutterBottom>
            {t('Platforms')}
          </Typography>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '10px',
          }}
          >
            {injectResultOverviewOutput.inject_injector_contract?.injector_contract_platforms?.map((platform: string) => (
              <div
                key={platform}
                style={{
                  display: 'flex',
                  marginRight: 15,
                }}
              >
                <PlatformIcon width={20} platform={platform} marginRight={theme.spacing(1)} />
                {platform}
              </div>
            ))}
          </div>
        </div>
        <div style={{ gridArea: 'killchainphases' }}>
          <Typography variant="h3" gutterBottom>
            {t('Kill Chain Phases')}
          </Typography>
          {(injectResultOverviewOutput.inject_kill_chain_phases ?? []).length === 0 && '-'}
          {injectResultOverviewOutput.inject_kill_chain_phases?.map((killChainPhase: KillChainPhaseSimple) => (
            <Chip
              key={killChainPhase.phase_id}
              variant="outlined"
              classes={{ root: classes.chip }}
              color="error"
              label={killChainPhase.phase_name}
            />
          ))}
        </div>
      </div>
    </Paper>
  );
};

export default AtomicTestingInformation;
