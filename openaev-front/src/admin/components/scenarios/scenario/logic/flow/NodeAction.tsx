import { LanguageOutlined, PlayArrowOutlined, PushPinOutlined } from '@mui/icons-material';
import { Chip, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import ButtonPopover from '../../../../../../components/common/ButtonPopover';
import FindingIcon from '../../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../../components/i18n';
import type { WorkflowStep } from '../../../../../../utils/api-types-custom';
import type { InputBinding } from '../logicUtils';
import { formatBinding } from '../logicUtils';
import InjectIcon from '../../../../common/injects/InjectIcon';

export type HighlightState = 'source' | 'highlighted' | 'dimmed' | null;

export type NodeActionData = {
  step: WorkflowStep;
  label: string;
  injectorType: string | null;
  attackPatternExternalIds: string[];
  outputTypes: string[];
  inputBindings: InputBinding[];
  fieldScopes: Record<string, string>;
  hasParentEvent: boolean;
  highlightState: HighlightState;
  onEdit: (step: WorkflowStep) => void;
  onDelete: (stepId: string) => void;
  onHighlight: (stepId: string) => void;
};

export type NodeActionType = Node<NodeActionData>;

const NodeAction = ({ data }: NodeProps<NodeActionType>) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const popoverEntries = [
    {
      label: t('Edit'),
      action: () => data.onEdit(data.step),
      userRight: true,
    },
    {
      label: t('Delete'),
      action: () => data.onDelete(data.step.step_id),
      userRight: true,
    },
  ];

  const hl = data.highlightState;
  const isDimmed = hl === 'dimmed';

  const borderStyle = hl === 'source'
    ? { border: `3px solid ${theme.palette.primary.main}`, boxShadow: theme.shadows[6] }
    : hl === 'highlighted'
      ? { border: `2px solid ${theme.palette.primary.light}`, boxShadow: theme.shadows[3] }
      : { border: `1px solid ${theme.palette.divider}` };

  return (
    <div
      onClick={() => data.onHighlight(data.step.step_id)}
      style={{
        width: 280,
        background: theme.palette.background.paper,
        ...borderStyle,
        borderLeft: hl === 'source'
          ? `4px solid ${theme.palette.primary.main}`
          : hl === 'highlighted'
            ? `4px solid ${theme.palette.primary.light}`
            : `4px solid ${theme.palette.primary.main}`,
        borderRadius: 4,
        padding: '10px 12px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        opacity: isDimmed ? 0.3 : 1,
        transition: 'opacity 0.2s, border 0.2s, box-shadow 0.2s',
        cursor: 'pointer',
      }}
    >
      <Handle type="target" position={Position.Top} style={{ visibility: 'hidden' }} />
      {data.injectorType
        ? <InjectIcon type={data.injectorType} size="small" tooltip={{}} />
        : <PlayArrowOutlined color="primary" sx={{ fontSize: 20 }} />
      }
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Typography variant="body2" noWrap fontWeight="bold" sx={{ maxWidth: 120 }}>
            {data.label}
          </Typography>
          <Chip size="small" label={t('Action')} color="primary" variant="outlined" sx={{ height: 18, fontSize: 10 }} />
          {data.attackPatternExternalIds.map(eid => (
            <Chip key={eid} size="small" label={eid} variant="outlined" color="secondary" sx={{ height: 18, fontSize: 10 }} />
          ))}
        </div>
        {data.inputBindings.some(b => b.bound) && (
          <div style={{ display: 'flex', gap: 4, marginTop: 4, flexWrap: 'wrap', alignItems: 'center' }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontSize: 9, mr: 0.5 }}>
              ⚡
            </Typography>
            {data.inputBindings.filter(b => b.bound).map(binding => (
              <Tooltip
                key={binding.argumentKey}
                title={`${binding.argumentKey} ← ${formatBinding(binding)} (${binding.scope.toLowerCase()} via ${binding.providers.map(p => p.eventLabel).join(', ')})`}
              >
                <Chip
                  size="small"
                  icon={<FindingIcon findingType={binding.inputType} />}
                  label={formatBinding(binding)}
                  variant="outlined"
                  color="success"
                  sx={{ height: 18, fontSize: 9 }}
                />
              </Tooltip>
            ))}
          </div>
        )}
        {data.outputTypes.length > 0 && (
          <div style={{ display: 'flex', gap: 4, marginTop: 6, flexWrap: 'wrap' }}>
            {data.outputTypes.map(type => {
              if (!data.hasParentEvent) {
                return (
                  <Chip
                    key={type}
                    size="small"
                    icon={<FindingIcon findingType={type} />}
                    label={type}
                    variant="outlined"
                    sx={{ height: 20, fontSize: 10 }}
                  />
                );
              }
              const isLocal = (data.fieldScopes[type] ?? 'GLOBAL') === 'LOCAL';
              return (
                <Tooltip
                  key={type}
                  title={isLocal ? t('Local scope') : t('Global scope')}
                >
                  <Chip
                    size="small"
                    icon={
                      <>
                        <FindingIcon findingType={type} />
                        {isLocal
                          ? <PushPinOutlined sx={{ fontSize: 10, ml: -0.5 }} />
                          : <LanguageOutlined sx={{ fontSize: 10, ml: -0.5 }} />
                        }
                      </>
                    }
                    label={type}
                    variant={isLocal ? 'filled' : 'outlined'}
                    color={isLocal ? 'primary' : 'default'}
                    sx={{ height: 20, fontSize: 10 }}
                  />
                </Tooltip>
              );
            })}
          </div>
        )}
      </div>
      <div className="nopan nodrag">
        <ButtonPopover entries={popoverEntries} variant="icon" size="small" />
      </div>
      <Handle type="source" position={Position.Bottom} style={{ visibility: 'hidden' }} />
    </div>
  );
};

export default memo(NodeAction);
