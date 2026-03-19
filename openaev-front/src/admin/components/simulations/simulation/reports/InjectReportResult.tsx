import { Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import { useFormatter } from '../../../../../components/i18n';
import ItemTargets from '../../../../../components/ItemTargets';
import { type InjectResultOutput, type ReportInjectComment } from '../../../../../utils/api-types';
import AtomicTestingResult from '../../../atomic_testings/atomic_testing/AtomicTestingResult';
import InjectorContract from '../../../common/injects/InjectorContract';
import ReportComment from '../../../components/reports/ReportComment';

interface Props {
  style?: CSSProperties;
  injects: InjectResultOutput[];
  injectsComments?: ReportInjectComment[];
  canEditComment?: boolean;
  onCommentSubmit?: SubmitHandler<ReportInjectComment>;
}

const InjectReportResult: FunctionComponent<Props> = ({
  style,
  injects,
  injectsComments = [],
  canEditComment = false,
  onCommentSubmit = () => {},
}) => {
  // Standard hooks
  const { t, fldt, tPick } = useFormatter();
  const findInjectCommentsByInjectId = (injectId: InjectResultOutput['inject_id']) => (injectsComments ?? []).find(c => c.inject_id === injectId) ?? null;

  const saveComment = (injectId: ReportInjectComment['inject_id'], value: string) => {
    onCommentSubmit({
      inject_id: injectId,
      report_inject_comment: value,
    });
  };

  const columns = [
    {
      label: 'Type',
      render: (inject: InjectResultOutput) => {
        return inject.inject_injector_contract
          ? <InjectorContract variant="list" label={tPick(inject.inject_injector_contract?.injector_contract_labels)} />
          : <InjectorContract variant="list" label={t('Deleted')} deleted={true} />;
      },
    },
    {
      label: 'Title',
      render: (inject: InjectResultOutput) => inject.inject_title,
    },
    {
      label: 'Execution date',
      render: (inject: InjectResultOutput) => {
        const trackingDate = inject.inject_status?.tracking_sent_date;
        return <>{trackingDate ? fldt(trackingDate) : '-'}</>;
      },
    },
    {
      label: 'Scores',
      render: (inject: InjectResultOutput) => <AtomicTestingResult expectations={inject.inject_expectation_results} injectId={inject.inject_id} />,
    },
    {
      label: 'Targets',
      render: (inject: InjectResultOutput) => <ItemTargets targets={inject.inject_targets} />,
    },
    {
      label: 'Comments',
      render: (inject: InjectResultOutput) => {
        const currentInjectComment = findInjectCommentsByInjectId(inject.inject_id);
        return (
          <ReportComment
            canEditComment={canEditComment}
            initialComment={currentInjectComment?.report_inject_comment || ''}
            saveComment={value => saveComment(inject.inject_id, value)}
          />
        );
      },
    },
  ];

  return (
    <div style={style}>
      <Typography variant="h4" gutterBottom>
        {t('Injects results')}
      </Typography>

      <Paper variant="outlined">
        <TableContainer style={{
          maxHeight: 'none',
          overflow: 'visible',
        }}
        >
          <Table aria-label="injects results">
            <TableHead>
              <TableRow>
                {columns.map(col => (
                  <TableCell
                    sx={col.label === 'Comments' ? {
                      padding: '0px',
                      width: '35%',
                      flexGrow: 1,
                    } : {}}
                    key={col.label}
                  >
                    {' '}
                    {t(col.label)}
                    {' '}

                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {injects.map(inject => (
                <TableRow
                  key={inject.inject_id}
                >
                  {columns.map(col => (
                    <TableCell
                      sx={col.label === 'Comments' ? {
                        padding: '16px 0 16px 0',
                        width: '35%',
                        flexGrow: 1,
                        alignItems: 'flex-start',
                      } : { verticalAlign: 'top' }}
                      key={`${inject.inject_id}-${col.label}`}
                    >
                      {col.render(inject)}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </div>
  );
};

export default InjectReportResult;
