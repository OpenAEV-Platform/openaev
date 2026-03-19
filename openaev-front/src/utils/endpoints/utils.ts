import type { ExecutorHelper } from '../../actions/executors/executor-helper';
import { type AgentExecutorOutput, type EndpointOutput } from '../api-types';

export const getActiveMsgTooltip = (activity_map: boolean[], activeMessage: string, inactiveMessage: string, agentlessMessage: string): {
  status: 'Active' | 'Inactive' | 'Agentless';
  activeMsgTooltip: string;
} => {
  if (activity_map.length > 0) {
    const activeCount = activity_map.filter(activity => activity).length;
    const inactiveCount = activity_map.length - activeCount;
    const isActive = activeCount > 0;
    if (isActive) {
      return {
        status: 'Active',
        activeMsgTooltip: activeMessage + ' : ' + activeCount + ' | ' + inactiveMessage + ' : ' + inactiveCount,
      };
    } else {
      return {
        status: 'Inactive',
        activeMsgTooltip: activeMessage + ' : ' + activeCount + ' | ' + inactiveMessage + ' : ' + inactiveCount,
      };
    }
  } else {
    return {
      status: 'Agentless',
      activeMsgTooltip: agentlessMessage,
    };
  }
};

export const getExecutorsCount = (endpoint: EndpointOutput, executorsMap: ReturnType<ExecutorHelper['getExecutorsMap']>) => {
  const executors = endpoint.asset_agents.map(agent => agent.agent_executor);
  return executors?.reduce((acc, executor) => {
    const type = executor?.executor_id ? executorsMap[executor.executor_id]?.executor_type : undefined;
    if (type && executor) {
      acc[type] = acc[type] || [];
      acc[type].push(executor);
    } else {
      acc['Unknown'] = acc['Unknown'] || [];
    }
    return acc;
  }, {} as Record<string, AgentExecutorOutput[]>);
};
