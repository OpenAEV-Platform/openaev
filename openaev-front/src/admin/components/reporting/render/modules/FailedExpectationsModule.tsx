import { Typography } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { capitalize } from '../../../../../utils/String';
import expectationIconByType, { expectationTypeColor } from '../../../common/ExpectationIconByType';
import { type FailedExpectationRow, type ModuleDataState } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError, PrintChip, ReportCell, ReportRow, ReportTable } from './ModuleSection';

/**
 * Latest failed expectations: inject, expectation, type and validation date.
 * Uses the shared print table so headers and cells stay perfectly aligned in
 * both the PDF and the HTML flavor; the type tag follows the platform-wide
 * expectation identity color (type is a category, not a result).
 */

interface Props { failedExpectations: ModuleDataState<FailedExpectationRow[]> }

const FailedExpectationsModule: FunctionComponent<Props> = ({ failedExpectations }) => {
  const { t, nsdt } = useFormatter();

  if (failedExpectations.status === 'error') return <ModuleError />;
  const rows = failedExpectations.data ?? [];
  if (failedExpectations.status !== 'success' || rows.length === 0) {
    return <ModuleEmpty message={t('No failed expectation over the selected time range.')} />;
  }

  return (
    <ReportTable
      columns={[
        { label: t('Inject') },
        {
          label: t('Expectation'),
          width: '26%',
        },
        {
          label: t('Type'),
          width: 130,
        },
        {
          label: t('Date'),
          width: 170,
        },
      ]}
    >
      {rows.map((row, index) => (
        // Rows have no natural id in the flattened ES payload.
        // eslint-disable-next-line react/no-array-index-key
        <ReportRow key={index}>
          <ReportCell>
            <Typography sx={{
              fontSize: 12,
              fontWeight: 500,
              overflowWrap: 'anywhere',
            }}
            >
              {row.injectTitle}
            </Typography>
          </ReportCell>
          <ReportCell>
            <Typography sx={{
              fontSize: 12,
              color: 'text.secondary',
            }}
            >
              {row.expectationName}
            </Typography>
          </ReportCell>
          <ReportCell>
            <PrintChip
              label={t(capitalize(row.expectationType))}
              color={expectationTypeColor(row.expectationType)}
              icon={expectationIconByType(row.expectationType, { fontSize: 12 })}
            />
          </ReportCell>
          <ReportCell>
            <Typography sx={{
              fontSize: 11,
              color: 'text.secondary',
              whiteSpace: 'nowrap',
            }}
            >
              {row.date ? nsdt(row.date) : '-'}
            </Typography>
          </ReportCell>
        </ReportRow>
      ))}
    </ReportTable>
  );
};

export default FailedExpectationsModule;
