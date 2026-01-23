import { MoreVertOutlined } from '@mui/icons-material';
import {
  IconButton,
  Menu,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type MouseEvent as ReactMouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import {
  type Inject,
  type InjectExpectation,
  type InjectExpectationResult,
  type PayloadSimple,
} from '../../../../../utils/api-types';
import { isNotEmptyField } from '../../../../../utils/utils';
import InjectIcon from '../../../common/injects/InjectIcon';
import { getSourceLabel } from '../../../simulations/simulation/validation/expectations/ExpectationUtils';
import TargetResultAlertNumber from './TargetResultAlertNumber';

interface Props {
  injectExpectationId: InjectExpectation['inject_expectation_id'];
  injectExpectationResults: InjectExpectationResult[];
  injectExpectationAgent: InjectExpectation['inject_expectation_agent'];
  injectorContractPayload?: PayloadSimple;
  injectType: Inject['inject_type'];
  handleOpenEditResult: (result: InjectExpectationResult) => void;
  handleOpenDeleteResult: (result: InjectExpectationResult) => void;
  handleOpenSecurityPlatform: (result: InjectExpectationResult) => void;
}

const InjectExpectationResultList = ({
  injectExpectationId,
  injectExpectationResults,
  injectExpectationAgent,
  injectorContractPayload,
  injectType,
  handleOpenEditResult,
  handleOpenDeleteResult,
  handleOpenSecurityPlatform,
}: Props) => {
  const { nsdt, t } = useFormatter();
  const theme = useTheme();

  const [anchorEditButton, setAnchorEditButton] = useState<null | HTMLElement>(null);
  const [selectedExpectationResult, setSelectedExpectationResult] = useState<InjectExpectationResult>();

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

  const onOpenMenu = (event: ReactMouseEvent<HTMLButtonElement>, expectationResult: InjectExpectationResult) => {
    event.stopPropagation();
    setAnchorEditButton(event.currentTarget);
    setSelectedExpectationResult(expectationResult);
  };
  const onCloseMenu = () => {
    setAnchorEditButton(null);
    setSelectedExpectationResult(undefined);
  };

  const onOpenEditResult = () => {
    if (selectedExpectationResult) {
      handleOpenEditResult(selectedExpectationResult);
    }
    onCloseMenu();
  };
  const onOpenDeleteResult = () => {
    if (selectedExpectationResult) {
      handleOpenDeleteResult(selectedExpectationResult);
    }
    onCloseMenu();
  };

  const handleClickSecurityPlatformResult = (expectationResult: InjectExpectationResult) => {
    handleOpenSecurityPlatform(expectationResult);
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
                    handleClickSecurityPlatformResult(expectationResult);
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
                      <TargetResultAlertNumber expectationResult={expectationResult} injectExpectationId={injectExpectationId} />
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
                  <IconButton
                    color="primary"
                    onClick={event => onOpenMenu(event, expectationResult)}
                    aria-haspopup="true"
                    size="large"
                    disabled={['collector', 'media-pressure', 'challenge'].includes(expectationResult.sourceType ?? 'unknown')}
                  >
                    <MoreVertOutlined />
                  </IconButton>
                  <Menu
                    anchorEl={anchorEditButton}
                    open={Boolean(anchorEditButton)}
                    onClose={onCloseMenu}
                  >
                    <MenuItem onClick={onOpenEditResult}>
                      {t('Update')}
                    </MenuItem>
                    <MenuItem onClick={onOpenDeleteResult}>
                      {t('Delete')}
                    </MenuItem>
                  </Menu>
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
