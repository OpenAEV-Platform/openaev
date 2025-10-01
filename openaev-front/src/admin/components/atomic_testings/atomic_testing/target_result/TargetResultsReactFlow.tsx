import { useTheme } from '@mui/material/styles';
import {
  type Edge,
  MarkerType,
  ReactFlow, type ReactFlowInstance,
  useEdgesState,
  useNodesState,
} from '@xyflow/react';
import { useCallback, useEffect } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { truncate } from '../../../../../utils/String';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import nodeTypes from '../types/nodes';
import type { NodeResultStep } from '../types/nodes/NodeResultStep';

interface Props {
  className?: string;
  targetResultsByType: Record<string, InjectExpectationsStore[]>;
  lastExecutionStartDate: string;
  injectStatusName?: string;
  lastExecutionEndDate: string;
}

interface Steptarget {
  label: string | React.JSX.Element;
  status?: string;
  key?: string;
  type?: string;
}

const TargetResultsReactFlow = ({ className = '', injectStatusName, targetResultsByType, lastExecutionStartDate, lastExecutionEndDate }: Props) => {
  const theme = useTheme();
  const { nsdt, t } = useFormatter();
  const [nodes, setNodes] = useNodesState<NodeResultStep>([]);
  const [edges, setEdges] = useEdgesState<Edge>([]);

  const defaultEdgeOptions = {
    type: 'straight',
    markerEnd: { type: MarkerType.ArrowClosed },
  };

  const proOptions = {
    account: 'paid-pro',
    hideAttribution: true,
  };

  const initialSteps = [{
    label: t('Attack started'),
    key: 'attack-started',
  }, {
    label: t('Attack ended'),
    key: 'attack-ended',
  }];

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
  const computeInitialSteps = (currentInitialSteps: Steptarget[]) => {
    return currentInitialSteps.map((step, index) => {
      let status = 'PENDING';
      if (index === 0 && injectStatusName === 'QUEUING') {
        status = 'QUEUING';
      } else if ((index === 0 && lastExecutionStartDate) || lastExecutionEndDate) {
        status = 'SUCCESS';
      }
      return {
        ...step,
        status,
      };
    });
  };

  const createNode = (step: Steptarget, stepsSize: number, index: number) => {
    return {
      id: `result-${index}`,
      type: 'result',
      data: {
        key: step.key ?? '',
        label: step.label,
        start: index === 0,
        end: index === stepsSize - 1,
        middle: index !== 0 && index !== stepsSize - 1,
        color: getColor(step.status).color,
        background: getColor(step.status).background,
      },
      position: {
        x: 350 * index,
        y: 0,
      },
    };
  };

  const createEdge = (index: number) => {
    return {
      id: `result-${index}->result-${index + 1}`,
      source: `result-${index}`,
      target: `result-${index + 1}`,
      label: index === 0 ? nsdt(lastExecutionStartDate) : nsdt(lastExecutionEndDate),
      labelShowBg: false,
      labelStyle: {
        fill: theme.palette.text?.primary,
        fontSize: 9,
      },
    };
  };

  useEffect(() => {
    let steps: Steptarget[] = [];
    if (targetResultsByType === undefined || Object.keys(targetResultsByType).length == 0) {
      steps = [...computeInitialSteps(initialSteps), ...[{
        label: t('Unknown result'),
        status: 'PENDING',
      }]];
    } else {
      const newSteps: Steptarget[] = Object.entries(targetResultsByType).flatMap(([type, expectations]) => {
        return expectations.map((expectation: InjectExpectationsStore) => ({
          key: 'result',
          label: (
            <span>
              {getStatusLabel(type, [expectation.inject_expectation_status ?? 'UNKNOWN'])}
              <br />
              {truncate(expectation.inject_expectation_name, 20)}
            </span>
          ),
          type: type,
          status: getStatus([expectation.inject_expectation_status ?? 'UNKNOWN']),
        }));
      });
      steps = [...computeInitialSteps(initialSteps), ...newSteps];
    }

    setEdges([...Array(steps.length - 1)].map((_, i) => createEdge(i)));
    setNodes(steps.map((step, index) => createNode(step, steps.length, index)));
  }, [targetResultsByType]);

  const onInit = useCallback((instance: ReactFlowInstance<NodeResultStep, Edge>) => {
    instance.fitView({
      padding: 0.1,
      includeHiddenNodes: false,
    });
  }, []);

  return (
    <div
      style={{
        width: '100%',
        height: 150,
      }}
      className={className}
    >
      <ReactFlow
        onInit={onInit}
        colorMode={theme.palette.mode}
        nodes={nodes}
        edges={edges}
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
  );
};

export default TargetResultsReactFlow;
