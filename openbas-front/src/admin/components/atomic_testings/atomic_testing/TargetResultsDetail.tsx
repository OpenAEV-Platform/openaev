import { AddModeratorOutlined, InventoryOutlined, MoreVertOutlined } from '@mui/icons-material';
import { Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, GridLegacy, IconButton, Menu, MenuItem, Paper, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type Edge, MarkerType, ReactFlow, ReactFlowProvider, useEdgesState, useNodesState, useReactFlow } from '@xyflow/react';
import { type FunctionComponent, type SyntheticEvent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import {
  fetchInjectResultOverviewOutput,
  fetchTargetResultMerged,
} from '../../../../actions/atomic_testings/atomic-testing-actions';
import { deleteInjectExpectationResult } from '../../../../actions/Exercise';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemResult from '../../../../components/ItemResult';
import {
  type InjectExpectation,
  type InjectExpectationResult,
  type InjectResultOverviewOutput,
  type InjectTarget,
} from '../../../../utils/api-types';
import useAutoLayout, { type LayoutOptions } from '../../../../utils/flows/useAutoLayout';
import { useAppDispatch } from '../../../../utils/hooks';
import { emptyFilled, truncate } from '../../../../utils/String';
import { isNotEmptyField } from '../../../../utils/utils';
import { type InjectExpectationsStore } from '../../common/injects/expectations/Expectation';
import {
  HUMAN_EXPECTATION,
  isManualExpectation,
  isTechnicalExpectation,
} from '../../common/injects/expectations/ExpectationUtils';
import InjectIcon from '../../common/injects/InjectIcon';
import ExecutionStatusDetail from '../../common/injects/status/ExecutionStatusDetail';
import DetectionPreventionExpectationsValidationForm from '../../simulations/simulation/validation/expectations/DetectionPreventionExpectationsValidationForm';
import ManualExpectationsValidationForm from '../../simulations/simulation/validation/expectations/ManualExpectationsValidationForm';
import { InjectResultOverviewOutputContext, type InjectResultOverviewOutputContextType } from '../InjectResultOverviewOutputContext';
import ExpirationChip from './ExpirationChip';
import TargetResultAlertNumber from './TargetResultAlertNumber';
import TargetResultsSecurityPlatform from './TargetResultsSecurityPlatform';
import nodeTypes from './types/nodes';
import { type NodeResultStep } from './types/nodes/NodeResultStep';

interface Steptarget {
  label: string;
  type: string;
  status?: string;
  key?: string;
}

const useStyles = makeStyles()(theme => ({
  container: {
    margin: '20px 0 0 0',
    overflow: 'hidden',
  },
  tabs: { marginLeft: 'auto' },
  target: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-evenly',
    padding: '10px 20px 0 20px',
    textAlign: 'center',
  },
  resultCardDummy: {
    height: 120,
    border: `1px dashed ${theme.palette.divider}`,
    background: 0,
    backgroundColor: 'transparent',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-evenly',
    textAlign: 'center',
  },
  area: {
    width: '100%',
    height: '100%',
  },
  score: {
    fontSize: '0.75rem',
    height: '20px',
    padding: '0 4px',
  },
  duration: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    marginRight: 7,
    borderRadius: 4,
    width: 180,
    backgroundColor: 'rgba(0, 177, 255, 0.08)',
    color: '#00b1ff',
    border: '1px solid #00b1ff',
  },
  paper: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 16,
  },
  cardHeaderContent: { overflow: 'hidden' },
  flexContainer: {
    display: 'flex',
    alignItems: 'baseline',
  },
  paperResults: {
    position: 'relative',
    padding: 20,
    overflow: 'hidden',
    height: '100%',
  },
  tableFontSize: { fontSize: '12px' },
}));

interface Props {
  inject: InjectResultOverviewOutput;
  lastExecutionStartDate: string;
  lastExecutionEndDate: string;
  target: InjectTarget;
}

const TargetResultsDetailFlow: FunctionComponent<Props> = ({
  inject,
  lastExecutionStartDate,
  lastExecutionEndDate,
  target,
}) => {
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const { nsdt, t } = useFormatter();
  const [anchorEls, setAnchorEls] = useState<Record<string, Element | null>>({});
  const [selectedExpectationForCreation, setSelectedExpectationForCreation] = useState<{
    injectExpectation: InjectExpectationsStore;
    sourceIds: string[];
  } | null>(null);
  const [selectedResultEdition, setSelectedResultEdition] = useState<{
    injectExpectation: InjectExpectationsStore;
    expectationResult: InjectExpectationResult;
  } | null>(null);
  const [selectedResultDeletion, setSelectedResultDeletion] = useState<{
    injectExpectation: InjectExpectationsStore;
    expectationResult: InjectExpectationResult;
  } | null>(null);
  const [initialized, setInitialized] = useState(false);
  const [activeTab, setActiveTab] = useState(0);
  const [targetResults, setTargetResults] = useState<InjectExpectationsStore[]>([]);
  const [selectedResult, setSelectedResult] = useState<InjectExpectationResult | null>(null);
  const [selectedExpectationForResults, setSelectedExpectationForResults] = useState<InjectExpectationsStore | null>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<NodeResultStep>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  const initialSteps = [{
    label: t('Attack started'),
    type: '',
    key: 'attack-started',
  }, {
    label: t('Attack ended'),
    type: '',
    key: 'attack-ended',
  }];
  const sortOrder = ['PREVENTION', 'DETECTION', 'VULNERABILITY', 'MANUAL'];
  // Flow
  const layoutOptions: LayoutOptions = {
    algorithm: 'd3-hierarchy',
    direction: 'LR',
    spacing: [350, 350],
  };
  useAutoLayout(layoutOptions, targetResults);
  const { fitView } = useReactFlow();

  const handleOpenResultEdition = (injectExpectation: InjectExpectationsStore, expectationResult: InjectExpectationResult) => {
    setAnchorEls({
      ...anchorEls,
      [`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]: null,
    });
    setSelectedResultEdition({
      injectExpectation,
      expectationResult,
    });
  };
  const handleOpenResultDeletion = (injectExpectation: InjectExpectationsStore, expectationResult: InjectExpectationResult) => {
    setAnchorEls({
      ...anchorEls,
      [`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]: null,
    });
    setSelectedResultDeletion({
      injectExpectation,
      expectationResult,
    });
  };
  const getColor = (status: string | undefined) => {
    let color;
    let background;
    switch (status) {
      case 'SUCCESS':
        color = theme.palette.success.main;
        background = 'rgba(176, 211, 146, 0.21)';
        break;
      case 'FAILED':
        color = theme.palette.error.main;
        background = 'rgba(192, 113, 113, 0.29)';
        break;
      case 'PARTIAL':
        color = theme.palette.warning.main;
        background = 'rgba(255, 152, 0, 0.29)';
        break;
      case 'QUEUING':
        color = '#ffeb3b';
        background = 'rgba(255, 235, 0, 0.08)';
        break;
      case 'PENDING':
        color = theme.palette.text?.primary;
        background = theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)';
        break;
      default: // Unknown status fow unknown expectation score
        color = theme.palette.text?.primary;
        background = theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)';
        break;
    }
    return {
      color,
      background,
    };
  };

  const computeInitialSteps = (currentInitialSteps: Steptarget[]) => {
    return currentInitialSteps.map((step, index) => {
      if (index === 0) {
        return {
          ...step,
          // eslint-disable-next-line @typescript-eslint/no-use-before-define,no-nested-ternary
          status: injectResultOverviewOutput?.inject_status?.status_name === 'QUEUING' ? 'QUEUING' : lastExecutionStartDate ? 'SUCCESS' : 'PENDING',
        };
      }
      return {
        ...step,
        status: lastExecutionEndDate ? 'SUCCESS' : 'PENDING',
      };
    });
  };

  const computeExistingSourceIds = (results: InjectExpectationResult[]) => {
    const sourceIds: string[] = [];
    results.forEach((result) => {
      if (result.sourceId) {
        sourceIds.push(result.sourceId);
      }
    });
    return sourceIds;
  };

  // Fetching data
  const { injectResultOverviewOutput, updateInjectResultOverviewOutput } = useContext<InjectResultOverviewOutputContextType>(InjectResultOverviewOutputContext);
  useEffect(() => {
    if (target) {
      setInitialized(false);
      const steps = [...computeInitialSteps(initialSteps), ...[{
        label: t('Unknown result'),
        type: '',
        status: 'PENDING',
      }]];
      setNodes(steps.map((step: Steptarget, index) => ({
        id: `result-${index}`,
        type: 'result',
        data: {
          key: step.key ? step.key : '',
          label: step.label,
          start: index === 0,
          end: index === steps.length - 1,
          middle: index !== 0 && index !== steps.length - 1,
          color: getColor(step.status).color,
          background: getColor(step.status).background,
        },
        position: {
          x: 0,
          y: 0,
        },
      })));
      setEdges([...Array(steps.length - 1)].map((_, i) => ({
        id: `result-${i}->result-${i + 1}`,
        source: `result-${i}`,
        target: `result-${i + 1}`,
        label: i === 0 ? nsdt(lastExecutionStartDate) : nsdt(lastExecutionEndDate),
        labelShowBg: false,
        labelStyle: {
          fill: theme.palette.text?.primary,
          fontSize: 9,
        },
      })));
      fetchTargetResultMerged(inject.inject_id, target.target_id!, target.target_type!).then(
        (result: { data: InjectExpectationsStore[] }) => setTargetResults(result.data ?? []),
      );
      setActiveTab(0);
      setTimeout(() => setInitialized(true), 1000);
    }
  }, [injectResultOverviewOutput, target]);

  const getStatus = (status: string[]) => {
    if (status.includes('UNKNOWN')) {
      return 'UNKNOWN';
    }
    if (status.includes('PENDING')) {
      return 'PENDING';
    }
    if (status.includes('PARTIAL')) {
      return 'PARTIAL';
    }
    if (status.includes('FAILED')) {
      return 'FAILED';
    }
    return status.every(s => s === 'SUCCESS') ? 'SUCCESS' : 'FAILED';
  };
  const getStatusLabel = (type: string, status: string[]) => {
    switch (type) {
      case 'DETECTION':
        if (status.includes('UNKNOWN')) {
          return 'No Expectation for Detection';
        }
        if (status.includes('PENDING')) {
          return 'Waiting for Detection';
        }
        return status.every(s => s === 'SUCCESS') ? 'Attack Detected' : 'Attack Not Detected';
      case 'MANUAL':
      case 'ARTICLE':
      case 'CHALLENGE':
        if (status.includes('UNKNOWN')) {
          return 'No Expectation for Manual';
        }
        if (status.includes('PENDING')) {
          return 'Waiting for Validation';
        }
        return status.every(s => s === 'SUCCESS') ? 'Validation Success' : 'Validation Failed';
      case 'PREVENTION':
        if (status.includes('UNKNOWN')) {
          return 'No Expectation';
        }
        if (status.includes('PENDING')) {
          return 'Waiting for Prevention';
        }
        return status.every(s => s === 'SUCCESS') ? 'Attack Prevented' : 'Attack Not Prevented';
      default:
        return '';
    }
  };
  const getAvatar = (injectExpectation: InjectExpectation, expectationResult: InjectExpectationResult) => {
    if (expectationResult.sourceType === 'collector') {
      return (
        <img
          src={`/api/images/collectors/id/${expectationResult.sourceId}`}
          alt={expectationResult.sourceId}
          style={{
            width: 25,
            height: 25,
            borderRadius: 4,
          }}
        />
      );
    }
    if (expectationResult.sourceType === 'security-platform') {
      return (
        <img
          src={`/api/images/security_platforms/id/${expectationResult.sourceId}/${theme.palette.mode}`}
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
        isPayload={isNotEmptyField(inject.inject_injector_contract?.injector_contract_payload)}
        type={inject.inject_injector_contract?.injector_contract_payload
          ? inject.inject_injector_contract?.injector_contract_payload.payload_collector_type
          || inject.inject_injector_contract?.injector_contract_payload.payload_type
          : inject.inject_type}
      />
    );
  };

  const onUpdateValidation = () => {
    fetchInjectResultOverviewOutput(inject.inject_id).then((result: { data: InjectResultOverviewOutput }) => {
      updateInjectResultOverviewOutput(result.data);
      setSelectedExpectationForCreation(null);
      setSelectedResultEdition(null);
    });
  };

  const onDelete = () => {
    dispatch(deleteInjectExpectationResult(selectedResultDeletion?.injectExpectation.inject_expectation_id, selectedResultDeletion?.expectationResult.sourceId)).then(() => {
      fetchInjectResultOverviewOutput(inject.inject_id).then((result: { data: InjectResultOverviewOutput }) => {
        updateInjectResultOverviewOutput(result.data);
        setSelectedResultDeletion(null);
      });
    });
  };

  const groupedByExpectationType = (es: InjectExpectationsStore[]) => {
    return es.reduce((group, expectation) => {
      const { inject_expectation_type } = expectation;
      if (inject_expectation_type) {
        const values = group.get(inject_expectation_type) ?? [];
        values.push(expectation);
        group.set(inject_expectation_type, values);
      }
      return group;
    }, new Map());
  };

  // Define steps
  useEffect(() => {
    if (initialized && targetResults && targetResults.length > 0) {
      const groupedBy = groupedByExpectationType(targetResults);
      const newSteps = Array.from(groupedBy).flatMap(([targetType, results]) => results.sort((a: InjectExpectationsStore, b: InjectExpectationsStore) => {
        if (a.inject_expectation_name && b.inject_expectation_name) {
          return a.inject_expectation_name.localeCompare(b.inject_expectation_name);
        }
        if (a.inject_expectation_name && !b.inject_expectation_name) {
          return -1; // a comes before b
        }
        if (!a.inject_expectation_name && b.inject_expectation_name) {
          return 1; // b comes before a
        }
        return a.inject_expectation_id.localeCompare(b.inject_expectation_id);
      }).map((expectation: InjectExpectation) => ({
        key: 'result',
        label: (
          <span>
            {getStatusLabel(targetType, [expectation.inject_expectation_status ?? 'UNKNOWN'])}
            <br />
            {truncate(expectation.inject_expectation_name, 20)}
          </span>
        ),
        type: targetType,
        status: getStatus([expectation.inject_expectation_status ?? 'UNKNOWN']),
      })));
      const mergedSteps: Steptarget[] = [...computeInitialSteps(initialSteps), ...newSteps];
      // Custom sorting function
      mergedSteps.sort((a, b) => {
        const typeAIndex = sortOrder.indexOf(a.type);
        const typeBIndex = sortOrder.indexOf(b.type);
        return typeAIndex - typeBIndex;
      });
      setNodes(mergedSteps.map((step, index) => ({
        id: `result-${index}`,
        type: 'result',
        data: {
          key: step.key ? step.key : '',
          label: step.label,
          start: index === 0,
          end: index === mergedSteps.length - 1,
          middle: index !== 0 && index !== mergedSteps.length - 1,
          color: getColor(step.status).color,
          background: getColor(step.status).background,
        },
        position: {
          x: 0,
          y: 0,
        },

      })));
      setEdges([...Array(mergedSteps.length - 1)].map((_, i) => ({
        id: `result-${i}->result-${i + 1}`,
        source: `result-${i}`,
        target: `result-${i + 1}`,
        label: i === 0 ? nsdt(lastExecutionStartDate) : nsdt(lastExecutionEndDate),
        labelShowBg: false,
        labelStyle: {
          fill: theme.palette.text?.primary,
          fontSize: 9,
        },
      })));
    }
  }, [targetResults, initialized]);

  // Define Tabs
  const groupedResults: Record<string, InjectExpectationsStore[]> = {};
  targetResults.forEach((result) => {
    const type = result.inject_expectation_type;
    if (!groupedResults[type]) {
      groupedResults[type] = [];
    }
    groupedResults[type].push(result);
  });
  const sortedKeys = Object.keys(groupedResults).sort((a, b) => {
    return sortOrder.indexOf(a) - sortOrder.indexOf(b);
  });
  const sortedGroupedResults: Record<string, InjectExpectationsStore[]> = {};
  sortedKeys.forEach((key) => {
    sortedGroupedResults[key] = groupedResults[key];
  });
  const handleTabChange = (_event: SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };
  const proOptions = {
    account: 'paid-pro',
    hideAttribution: true,
  };
  const defaultEdgeOptions = {
    type: 'straight',
    markerEnd: { type: MarkerType.ArrowClosed },
  };
  const getLabelOfValidationType = (injectExpectation: InjectExpectationsStore): string => {
    // eslint-disable-next-line no-nested-ternary
    return isTechnicalExpectation(injectExpectation.inject_expectation_type)
      ? injectExpectation.inject_expectation_group
        ? t('At least one asset (per group) must validate the expectation')
        : t('All assets (per group) must validate the expectation')
      : injectExpectation.inject_expectation_group
        ? t('At least one player (per team) must validate the expectation')
        : t('All players (per team) must validate the expectation');
  };

  const handleClickSecurityPlatformResult = (injectExpectation: InjectExpectationsStore, expectationResult: InjectExpectationResult) => {
    setSelectedResult(expectationResult);
    setSelectedExpectationForResults(injectExpectation);
  };

  const handleCloseSecurityPlatformResult = () => {
    setSelectedResult(null);
    setSelectedExpectationForResults(null);
  };

  const canShowExecutionTab = target.target_type !== 'ASSETS_GROUPS';

  return (
    <>
      <div className={classes.target}>
        <div>
          <Typography variant="h3" gutterBottom>
            {t('Name')}
          </Typography>
          {target.target_name}
        </div>
        <div>
          <Typography variant="h3" gutterBottom>
            {t('Type')}
          </Typography>
          {target.target_type}
        </div>
        <div>
          <Typography variant="h3" gutterBottom>
            {t('Platform')}
          </Typography>
          {target.target_subtype ?? t('N/A')}
        </div>
      </div>
      <div
        className={classes.container}
        style={{
          width: '100%',
          height: 150,
        }}
      >
        <ReactFlow
          colorMode={theme.palette.mode}
          nodes={nodes}
          edges={edges}
          onNodesChange={(changes) => {
            fitView();
            onNodesChange(changes);
          }}
          onEdgesChange={onEdgesChange}
          nodeTypes={nodeTypes}
          nodesDraggable={false}
          nodesConnectable={false}
          nodesFocusable={false}
          elementsSelectable={false}
          maxZoom={1}
          zoomOnScroll
          zoomOnPinch={false}
          zoomOnDoubleClick={false}
          panOnDrag
          defaultEdgeOptions={defaultEdgeOptions}
          proOptions={proOptions}
        />
      </div>
      <Box
        sx={{
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          indicatorColor="primary"
          textColor="primary"
          className={classes.tabs}
        >
          {Object.keys(sortedGroupedResults).length > 0
            && Object.keys(sortedGroupedResults).map((type, index) => (
              <Tab key={index} label={t(`TYPE_${type}`)} />
            ))}
          {canShowExecutionTab && <Tab label={t('Execution')} />}
        </Tabs>
      </Box>
      {Object.keys(sortedGroupedResults).map((targetResult, targetResultIndex) => (
        <div key={targetResultIndex} hidden={activeTab !== targetResultIndex}>
          {sortedGroupedResults[targetResult]
            .toSorted((a, b) => {
              if (a.inject_expectation_name && b.inject_expectation_name) {
                return a.inject_expectation_name.localeCompare(b.inject_expectation_name);
              }
              if (a.inject_expectation_name && !b.inject_expectation_name) {
                return -1; // a comes before b
              }
              if (!a.inject_expectation_name && b.inject_expectation_name) {
                return 1; // b comes before a
              }
              return a.inject_expectation_id.localeCompare(b.inject_expectation_id);
            })
            .map((injectExpectation) => {
              return (
                <div key={injectExpectation.inject_expectation_id} style={{ marginTop: 20 }}>
                  <Paper variant="outlined" classes={{ root: classes.paperResults }}>
                    <GridLegacy container={true} spacing={2} style={{ alignItems: 'baseline' }}>
                      <GridLegacy item={true} xs={6}>
                        <Typography variant="h5">
                          {injectExpectation.inject_expectation_name}
                        </Typography>
                      </GridLegacy>
                      {injectExpectation.inject_expectation_results && injectExpectation.inject_expectation_results.length > 0 ? (
                        <GridLegacy item={true} xs={5} sx={{ textAlign: 'end' }}>
                          {
                            injectExpectation.inject_expectation_status === 'SUCCESS' && injectExpectation.inject_expectation_type === 'PREVENTION' && (
                              <ItemResult label={t('Prevented')} status="Prevented" />
                            )
                          }
                          {
                            injectExpectation.inject_expectation_status === 'SUCCESS' && injectExpectation.inject_expectation_type === 'DETECTION' && (
                              <ItemResult label={t('Detected')} status="Detected" />
                            )
                          }
                          {
                            injectExpectation.inject_expectation_status === 'FAILED' && injectExpectation.inject_expectation_type === 'PREVENTION' && (
                              <ItemResult label={t('Not Prevented')} status="Not Prevented" />
                            )
                          }
                          {
                            injectExpectation.inject_expectation_status === 'FAILED' && injectExpectation.inject_expectation_type === 'DETECTION' && (
                              <ItemResult label={t('Not Detected')} status="Not Detected" />
                            )
                          }
                          {injectExpectation.inject_expectation_status && HUMAN_EXPECTATION.includes(injectExpectation.inject_expectation_type) && (
                            <ItemResult label={t(injectExpectation.inject_expectation_status)} status={injectExpectation.inject_expectation_status} />
                          )}
                          <Tooltip title={t('Score')}><Chip classes={{ root: classes.score }} label={injectExpectation.inject_expectation_score} /></Tooltip>
                        </GridLegacy>
                      )
                        : (
                            <GridLegacy item={true} xs={5} sx={{ textAlign: 'end' }}>
                              {
                                injectExpectation.inject_expectation_created_at && (
                                  <ExpirationChip
                                    expirationTime={injectExpectation.inject_expiration_time}
                                    startDate={injectExpectation.inject_expectation_created_at}
                                  />
                                )
                              }
                            </GridLegacy>
                          )}
                      {
                        isManualExpectation(injectExpectation.inject_expectation_type)
                        && injectExpectation.inject_expectation_results
                        && injectExpectation.inject_expectation_results.map((expectationResult) => {
                          return (
                            <GridLegacy key={injectExpectation.inject_expectation_id} item={true} xs={1} style={{ textAlign: 'end' }}>
                              <IconButton
                                color="primary"
                                onClick={(ev) => {
                                  ev.stopPropagation();
                                  setAnchorEls({
                                    ...anchorEls,
                                    [`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]: ev.currentTarget,
                                  });
                                }}
                                aria-haspopup="true"
                                size="large"
                                disabled={['collector', 'media-pressure', 'challenge'].includes(expectationResult.sourceType ?? 'unknown')}
                              >
                                <MoreVertOutlined />
                              </IconButton>
                              <Menu
                                anchorEl={anchorEls[`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]}
                                open={Boolean(anchorEls[`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`])}
                                onClose={() => setAnchorEls({
                                  ...anchorEls,
                                  [`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]: null,
                                })}
                              >
                                <MenuItem onClick={() => handleOpenResultEdition(injectExpectation, expectationResult)}>
                                  {t('Update')}
                                </MenuItem>
                                <MenuItem onClick={() => handleOpenResultDeletion(injectExpectation, expectationResult)}>
                                  {t('Delete')}
                                </MenuItem>
                              </Menu>
                            </GridLegacy>
                          );
                        })
                      }
                      {(['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type)
                        || (isManualExpectation(injectExpectation.inject_expectation_type)
                          && injectExpectation.inject_expectation_results
                          && injectExpectation.inject_expectation_results.length === 0))
                        && (
                          <GridLegacy item={true} xs={1} style={{ textAlign: 'end' }}>
                            <Tooltip title={t('Add a result')}>
                              <IconButton
                                aria-label="Add"
                                onClick={() => setSelectedExpectationForCreation({
                                  injectExpectation,
                                  sourceIds: computeExistingSourceIds(injectExpectation.inject_expectation_results ?? []),
                                })}
                              >
                                {
                                  ['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type) && (
                                    <AddModeratorOutlined fontSize="medium" />
                                  )
                                }
                                {
                                  isManualExpectation(injectExpectation.inject_expectation_type) && (
                                    <InventoryOutlined fontSize="medium" />
                                  )
                                }

                              </IconButton>
                            </Tooltip>

                          </GridLegacy>
                        )}
                    </GridLegacy>
                    <div className={classes.flexContainer}>
                      <div>
                        <Typography variant="h4">
                          {t('Validation rule:')}
                        </Typography>
                      </div>
                      <div style={{ marginLeft: theme.spacing(1) }}>
                        {emptyFilled(getLabelOfValidationType(injectExpectation))}
                      </div>
                    </div>
                    {isTechnicalExpectation(injectExpectation.inject_expectation_type) && injectExpectation.inject_expectation_type !== 'VULNERABILITY' && (
                      <TableContainer>
                        <Table
                          size="small"
                        >
                          <TableHead>
                            <TableRow sx={{ textTransform: 'uppercase' }}>
                              <TableCell>{t('Security platforms')}</TableCell>
                              <TableCell>{t('Status')}</TableCell>
                              <TableCell>{t('Detection time')}</TableCell>
                              <TableCell>{t('Alerts')}</TableCell>

                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {injectExpectation.inject_expectation_results && injectExpectation.inject_expectation_results.map((expectationResult, index) => {
                              const isResultSecurityPlatform: boolean = !!(
                                injectExpectation.inject_expectation_agent
                                && injectExpectation.inject_expectation_status === 'SUCCESS'
                                && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected')
                                && expectationResult.sourceType === 'collector'
                              );
                              return (
                                <TableRow
                                  key={index}
                                  hover={isResultSecurityPlatform}
                                  onClick={() => {
                                    if (isResultSecurityPlatform) {
                                      handleClickSecurityPlatformResult(injectExpectation, expectationResult);
                                    }
                                  }}
                                  sx={{ cursor: `${isResultSecurityPlatform ? 'pointer' : 'default'}` }}
                                  selected={expectationResult.sourceId === selectedResult?.sourceId}
                                >
                                  <TableCell className={classes.tableFontSize}>
                                    <div className={classes.flexContainer}>
                                      <div>
                                        {getAvatar(injectExpectation, expectationResult)}
                                      </div>
                                      <div style={{
                                        marginLeft: theme.spacing(1),
                                        alignSelf: 'center',
                                      }}
                                      >
                                        {expectationResult.sourceName ? t(expectationResult.sourceName) : t('Unknown')}
                                      </div>
                                    </div>
                                  </TableCell>
                                  <TableCell>
                                    <ItemResult label={t(expectationResult.result)} status={expectationResult.result} />
                                  </TableCell>
                                  <TableCell className={classes.tableFontSize}>
                                    {
                                      (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected' || expectationResult.result === 'SUCCESS') ? nsdt(expectationResult.date) : '-'
                                    }
                                  </TableCell>
                                  <TableCell>
                                    {
                                      expectationResult.sourceId && injectExpectation.inject_expectation_agent && expectationResult.sourceType === 'collector' && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected') && (
                                        <TargetResultAlertNumber expectationResult={expectationResult} injectExpectationId={injectExpectation.inject_expectation_id} />
                                      )
                                    }
                                    {
                                      !injectExpectation.inject_expectation_agent && (
                                        '-'
                                      )
                                    }
                                    {
                                      injectExpectation.inject_expectation_agent && (expectationResult.result === 'Not Detected' || expectationResult.result === 'Not Prevented') && (
                                        '-'
                                      )
                                    }
                                    {
                                      injectExpectation.inject_expectation_agent && expectationResult.sourceType !== 'collector' && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected') && (
                                        '-'
                                      )
                                    }
                                  </TableCell>
                                  <TableCell>
                                    <IconButton
                                      color="primary"
                                      onClick={(ev) => {
                                        ev.stopPropagation();
                                        setAnchorEls({
                                          ...anchorEls,
                                          [`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]: ev.currentTarget,
                                        });
                                      }}
                                      aria-haspopup="true"
                                      size="large"
                                      disabled={['collector', 'media-pressure', 'challenge'].includes(expectationResult.sourceType ?? 'unknown')}
                                    >
                                      <MoreVertOutlined />
                                    </IconButton>
                                    <Menu
                                      anchorEl={anchorEls[`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]}
                                      open={Boolean(anchorEls[`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`])}
                                      onClose={() => setAnchorEls({
                                        ...anchorEls,
                                        [`${injectExpectation.inject_expectation_id}-${expectationResult.sourceId}`]: null,
                                      })}
                                    >
                                      <MenuItem onClick={() => handleOpenResultEdition(injectExpectation, expectationResult)}>
                                        {t('Update')}
                                      </MenuItem>
                                      <MenuItem onClick={() => handleOpenResultDeletion(injectExpectation, expectationResult)}>
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
                    )}
                    {
                      selectedResult !== null && selectedResult.sourceId !== undefined && selectedExpectationForResults !== null
                      && (
                        <TargetResultsSecurityPlatform
                          injectExpectation={selectedExpectationForResults}
                          sourceId={selectedResult.sourceId}
                          expectationResult={selectedResult}
                          open={true}
                          handleClose={() => handleCloseSecurityPlatformResult()}
                        />
                      )
                    }
                  </Paper>

                </div>
              );
            })}
          <Dialog
            open={selectedExpectationForCreation !== null}
            TransitionComponent={Transition}
            onClose={() => setSelectedExpectationForCreation(null)}
            PaperProps={{ elevation: 1 }}
            fullWidth={true}
            maxWidth="md"
          >
            <DialogContent>
              {selectedExpectationForCreation && (
                <>
                  {isManualExpectation(selectedExpectationForCreation.injectExpectation.inject_expectation_type)
                    && <ManualExpectationsValidationForm expectation={selectedExpectationForCreation.injectExpectation} onUpdate={onUpdateValidation} />}
                  {['DETECTION', 'PREVENTION'].includes(selectedExpectationForCreation.injectExpectation.inject_expectation_type)
                    && (
                      <DetectionPreventionExpectationsValidationForm
                        expectation={selectedExpectationForCreation.injectExpectation}
                        sourceIds={selectedExpectationForCreation.sourceIds}
                        onUpdate={onUpdateValidation}
                      />
                    )}
                </>
              )}
            </DialogContent>
          </Dialog>
          <Dialog
            open={selectedResultEdition !== null}
            TransitionComponent={Transition}
            onClose={() => setSelectedResultEdition(null)}
            PaperProps={{ elevation: 1 }}
            fullWidth={true}
            maxWidth="md"
          >
            <DialogContent>
              {selectedResultEdition && selectedResultEdition.injectExpectation && (
                <>
                  {isManualExpectation(selectedResultEdition.injectExpectation.inject_expectation_type)
                    && (
                      <ManualExpectationsValidationForm
                        expectation={selectedResultEdition.injectExpectation}
                        onUpdate={onUpdateValidation}
                      />
                    )}
                  {['DETECTION', 'PREVENTION'].includes(selectedResultEdition.injectExpectation.inject_expectation_type)
                    && (
                      <DetectionPreventionExpectationsValidationForm
                        expectation={selectedResultEdition.injectExpectation}
                        result={selectedResultEdition.expectationResult}
                        onUpdate={onUpdateValidation}
                      />
                    )}
                </>
              )}
            </DialogContent>
          </Dialog>
          <Dialog
            open={selectedResultDeletion !== null}
            TransitionComponent={Transition}
            onClose={() => setSelectedResultDeletion(null)}
            PaperProps={{ elevation: 1 }}
          >
            <DialogContent>
              <DialogContentText>
                {t('Do you want to delete this expectation result?')}
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setSelectedResultDeletion(null)}>
                {t('Cancel')}
              </Button>
              <Button color="secondary" onClick={onDelete}>
                {t('Delete')}
              </Button>
            </DialogActions>
          </Dialog>
        </div>
      ))}
      {(initialized && activeTab === Object.keys(sortedGroupedResults).length && canShowExecutionTab) && (
        <div style={{ paddingTop: theme.spacing(3) }}>
          <ExecutionStatusDetail
            target={{
              id: target.target_id,
              name: target.target_name,
              targetType: target.target_type,
              platformType: target.target_subtype,
            }}
            injectId={inject.inject_id}
          />
        </div>
      )}
    </>
  );
};

const TargetResultsDetail: FunctionComponent<Props> = (props) => {
  return (
    <ReactFlowProvider>
      <TargetResultsDetailFlow {...props} />
    </ReactFlowProvider>
  );
};

export default TargetResultsDetail;
