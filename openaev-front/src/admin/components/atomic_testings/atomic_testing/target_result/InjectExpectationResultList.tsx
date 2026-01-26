import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';

import ButtonPopover from '../../../../../components/common/ButtonPopover';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import {
  type Inject,
  type InjectExpectation,
  type InjectExpectationResult,
  type PayloadSimple,
} from '../../../../../utils/api-types';
import { isNotEmptyField } from '../../../../../utils/utils';
import { type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import InjectIcon from '../../../common/injects/InjectIcon';
import { getSourceLabel } from '../../../simulations/simulation/validation/expectations/ExpectationUtils';
import InjectExpectationContext from '../context/InjectExpectationContext';
import TargetResultAlertNumber from './TargetResultAlertNumber';

interface Props {
  injectExpectation: InjectExpectationsStore;
  injectExpectationResults: InjectExpectationResult[];
  injectExpectationAgent: InjectExpectation['inject_expectation_agent'];
  injectorContractPayload?: PayloadSimple;
  injectType: Inject['inject_type'];
}

const InjectExpectationResultList = ({
  injectExpectation,
  injectExpectationResults,
  injectExpectationAgent,
  injectorContractPayload,
  injectType,
}: Props) => {
  const { nsdt, t } = useFormatter();
  const theme = useTheme();

  const { onOpenDeleteInjectExpectationResult, onOpenEditInjectExpectationResultResult, onOpenSecurityPlatform } = useContext(InjectExpectationContext);

  const getAvatar = (expectationResult: InjectExpectationResult) => {
    if (expectationResult.sourceType === 'collector' || expectationResult.sourceType === 'security-platform') {
      return (
        <img
          src={expectationResult.sourceType === 'collector'
            ? `/api/images/collectors/id/${expectationResult.sourceId}`
            : `/api/images/security_platforms/id/${expectationResult.sourceId}/${theme.palette.mode}`}
          alt={expectationResult.sourceId}
          style={{
            width: 25,
            height: 25,
            borderRadius: 4,
          }}
        />
      );
    }

    return (
      <InjectIcon
        isPayload={isNotEmptyField(injectorContractPayload)}
        type={injectorContractPayload
          ? injectorContractPayload.payload_collector_type
          ?? injectorContractPayload.payload_type
          : injectType}
      />
    );
  };

  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow sx={{ textTransform: 'uppercase' }}>
            <TableCell>{t('Security platforms')}</TableCell>
            <TableCell>{t('Status')}</TableCell>
            <TableCell>{t('Detection time')}</TableCell>
            <TableCell>{t('Alerts')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {injectExpectationResults.map((expectationResult, index) => {
            const isResultSecurityPlatform: boolean = !!(
              injectExpectationAgent
              && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected')
              && expectationResult.sourceType === 'collector'
            );

            return (
              <TableRow
                key={`${expectationResult.sourceName}-${index}`}
                hover={isResultSecurityPlatform}
                sx={{ cursor: `${isResultSecurityPlatform ? 'pointer' : 'default'}` }}
                onClick={() => {
                  if (isResultSecurityPlatform) {
                    onOpenSecurityPlatform(expectationResult, injectExpectation);
                  }
                }}
              >
                <TableCell>
                  <div style={{
                    display: 'flex',
                    gap: theme.spacing(1),
                  }}
                  >
                    {getAvatar(expectationResult)}
                    {getSourceLabel(expectationResult)}
                  </div>
                </TableCell>
                <TableCell>
                  {expectationResult.result && (
                    <ItemStatus
                      label={t(expectationResult.result)}
                      status={expectationResult.result}
                    />
                  )}
                </TableCell>
                <TableCell>
                  {(expectationResult.result === 'Prevented' || expectationResult.result === 'Detected' || expectationResult.result === 'SUCCESS')
                    ? nsdt(expectationResult.date) : '-' }
                </TableCell>
                <TableCell>
                  {
                    expectationResult.sourceId && injectExpectationAgent && expectationResult.sourceType === 'collector' && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected') && (
                      <TargetResultAlertNumber expectationResult={expectationResult} injectExpectationId={injectExpectation.inject_expectation_id} />
                    )
                  }
                  {(!injectExpectationAgent
                    || (injectExpectationAgent && (expectationResult.result === 'Not Detected' || expectationResult.result === 'Not Prevented'))
                    || (injectExpectationAgent && expectationResult.sourceType !== 'collector' && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected'))
                  ) && (
                    '-'
                  )}
                </TableCell>
                <TableCell>
                  <ButtonPopover
                    disabled={['collector', 'media-pressure', 'challenge'].includes(expectationResult.sourceType ?? 'unknown')}
                    entries={[{
                      label: t('Update'),
                      action: () => onOpenEditInjectExpectationResultResult(expectationResult, injectExpectation),
                      disabled: false,
                      userRight: true,
                    },
                    {
                      label: t('Delete'),
                      action: () => onOpenDeleteInjectExpectationResult(expectationResult, injectExpectation),
                      disabled: false,
                      userRight: true,
                    }]}
                    variant="icon"
                  />
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

    </TableContainer>
  );
};
export default InjectExpectationResultList;
