import { Skeleton, SkeletonGroup } from '@filigran/design-system';
import { Box, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { Fragment, type FunctionComponent, type MouseEvent, type ReactNode } from 'react';

import groupSections from '../../../../components/common/marketplace/results/groupSections';
import MarketplaceSectionHeader from '../../../../components/common/marketplace/results/MarketplaceSectionHeader';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { THREAT_ARSENAL_LIST_HEADERS, THREAT_ARSENAL_LIST_INLINE_STYLES } from './threatArsenalListConfig';
import ThreatArsenalListRow from './ThreatArsenalListRow';

interface Props {
  actions: ThreatArsenalAction[];
  loading: boolean;
  emptyState: ReactNode;
  sectionKey: (action: ThreatArsenalAction) => string | undefined;
  sectionLabel: (key: string | undefined) => string;
  sectionIcon: (key: string | undefined) => ReactNode;
  sortHelpers: SortHelpers;
  isChecked: (action: ThreatArsenalAction) => boolean;
  onSelect: (action: ThreatArsenalAction) => void;
  onToggle: (action: ThreatArsenalAction, event: MouseEvent<HTMLElement>) => void;
  onUpdate: (result: ThreatArsenalAction, replacedId?: string) => void;
  onDuplicate: (result: ThreatArsenalAction) => void;
  onDelete: (actionId: string) => void;
  onShowVersions: (action: ThreatArsenalAction) => void;
}

const ThreatArsenalList: FunctionComponent<Props> = ({
  actions,
  loading,
  emptyState,
  sectionKey,
  sectionLabel,
  sectionIcon,
  sortHelpers,
  isChecked,
  onSelect,
  onToggle,
  onUpdate,
  onDuplicate,
  onDelete,
  onShowVersions,
}) => {
  const { t } = useFormatter();
  if (!loading && actions.length === 0) {
    return <>{emptyState}</>;
  }
  return (
    <List disablePadding>
      <ListItem secondaryAction={<>&nbsp;</>} sx={{ paddingLeft: 2 }}>
        <ListItemIcon />
        <ListItemIcon />
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={THREAT_ARSENAL_LIST_HEADERS}
              inlineStylesHeaders={THREAT_ARSENAL_LIST_INLINE_STYLES}
              sortHelpers={sortHelpers}
            />
          )}
        />
      </ListItem>
      {loading
        ? (
            <SkeletonGroup loading label={t('Loading')}>
              {Array.from({ length: 10 }).map((_, idx) => (
                <ListItem key={idx} divider>
                  <ListItemText primary={(
                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                    }}
                    >
                      {THREAT_ARSENAL_LIST_HEADERS.map(header => (
                        <Box key={header.field} style={THREAT_ARSENAL_LIST_INLINE_STYLES[header.field]} sx={{ paddingRight: 1 }}>
                          <Skeleton />
                        </Box>
                      ))}
                    </Box>
                  )}
                  />
                </ListItem>
              ))}
            </SkeletonGroup>
          )
        : groupSections(actions, sectionKey).map(section => (
            <Fragment key={section.key ?? 'other'}>
              <Box sx={{ paddingBlock: 2 }}>
                <MarketplaceSectionHeader icon={sectionIcon(section.key)} label={sectionLabel(section.key)} count={section.items.length} />
              </Box>
              {section.items.map(action => (
                <ThreatArsenalListRow
                  key={action.injector_contract_id}
                  action={action}
                  checked={isChecked(action)}
                  onSelect={() => onSelect(action)}
                  onToggleEntity={event => onToggle(action, event)}
                  onUpdate={onUpdate}
                  onDuplicate={onDuplicate}
                  onDelete={() => onDelete(action.injector_contract_id)}
                  onShowVersions={onShowVersions}
                />
              ))}
            </Fragment>
          ))}
    </List>
  );
};

export default ThreatArsenalList;
