import { Checkbox, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, type MouseEvent } from 'react';
import { makeStyles } from 'tss-react/mui';

import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useFormatter } from '../../../components/i18n';
import ItemDomains from '../../../components/ItemDomains';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import PayloadStatusComponent from '../payloads/PayloadStatusComponent';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';
import { THREAT_ARSENAL_LIST_INLINE_STYLES } from './threatArsenalListConfig';

const useStyles = makeStyles()(() => ({ item: { height: 50 } }));

interface Props {
  action: ThreatArsenalAction;
  checked: boolean;
  onSelect: () => void;
  onToggleEntity: (event: MouseEvent<HTMLElement>) => void;
  onUpdate: (result: ThreatArsenalAction) => void;
  onDuplicate: (result: ThreatArsenalAction) => void;
  onDelete: () => void;
  disableUpdate: boolean;
  disableDuplicate: boolean;
  disableJsonExport: boolean;
  disableDelete: boolean;
}

const ThreatArsenalListRow: FunctionComponent<Props> = ({
  action,
  checked,
  onSelect,
  onToggleEntity,
  onUpdate,
  onDuplicate,
  onDelete,
  disableUpdate,
  disableDuplicate,
  disableJsonExport,
  disableDelete,
}) => {
  const { classes } = useStyles();
  const { tPick, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const name = tPick(action.action_labels);

  return (
    <ListItem
      divider
      disablePadding
      secondaryAction={(
        <ThreatArsenalActionPopover
          actionId={action.injector_contract_id}
          payloadId={action.action_payload?.payload_id ?? ''}
          name={name}
          onUpdate={onUpdate}
          onDuplicate={onDuplicate}
          onDelete={onDelete}
          disableUpdate={disableUpdate}
          disableDuplicate={disableDuplicate}
          disableJsonExport={disableJsonExport}
          disableDelete={disableDelete}
        />
      )}
    >
      <ListItemButton classes={{ root: classes.item }} onClick={onSelect}>
        <ListItemIcon
          style={{ minWidth: 38 }}
          onClick={(event) => {
            event.stopPropagation();
            onToggleEntity(event);
          }}
        >
          <Checkbox
            edge="start"
            checked={checked}
            disableRipple
            size="small"
            slotProps={{ input: { 'aria-label': name } }}
          />
        </ListItemIcon>
        <ListItemIcon style={{ minWidth: 40 }}>
          <InjectIcon
            type={
              action.action_payload != null
                ? action.action_payload.payload_collector_type ?? action.action_payload.payload_type
                : action.action_injector_type
            }
            isPayload={action.action_payload != null}
            variant="list"
          />
        </ListItemIcon>
        <ListItemText
          primary={(
            <div style={bodyItemsStyles.bodyItems}>
              <Tooltip title={name} enterDelay={500}>
                <div style={{
                  ...bodyItemsStyles.bodyItem,
                  ...THREAT_ARSENAL_LIST_INLINE_STYLES.action_name,
                }}
                >
                  {name}
                </div>
              </Tooltip>
              <div style={{
                ...bodyItemsStyles.bodyItem,
                ...THREAT_ARSENAL_LIST_INLINE_STYLES.action_domains,
              }}
              >
                <ItemDomains domains={action.action_domains_ids ?? []} variant="reduced-view" />
              </div>
              <div style={{
                ...bodyItemsStyles.bodyItem,
                ...THREAT_ARSENAL_LIST_INLINE_STYLES.action_platforms,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
              >
                {(action.action_platforms ?? []).slice(0, 4).map(platform => (
                  <PlatformIcon key={platform} width={18} platform={platform} tooltip />
                ))}
                {(action.action_platforms?.length ?? 0) > 4 && (
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    {`+${(action.action_platforms?.length ?? 0) - 4}`}
                  </Typography>
                )}
              </div>
              <div style={{
                ...bodyItemsStyles.bodyItem,
                ...THREAT_ARSENAL_LIST_INLINE_STYLES.action_tags,
              }}
              >
                <ItemTags variant="reduced-view" tags={action.action_tags_ids} />
              </div>
              <div style={{
                ...bodyItemsStyles.bodyItem,
                ...THREAT_ARSENAL_LIST_INLINE_STYLES.action_status,
              }}
              >
                <PayloadStatusComponent status={action.action_payload?.payload_status} />
              </div>
              <Typography
                component="div"
                variant="body2"
                sx={{
                  ...bodyItemsStyles.bodyItem,
                  ...THREAT_ARSENAL_LIST_INLINE_STYLES.action_updated,
                  color: 'text.secondary',
                }}
              >
                {nsdt(action.injector_contract_updated_at)}
              </Typography>
            </div>
          )}
        />
      </ListItemButton>
    </ListItem>
  );
};

export default ThreatArsenalListRow;
