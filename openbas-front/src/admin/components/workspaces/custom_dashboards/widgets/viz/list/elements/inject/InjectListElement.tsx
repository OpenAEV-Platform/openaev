import { Description } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

import type { AttackPatternHelper } from '../../../../../../../../../actions/attack_patterns/attackpattern-helper';
import AttackPatternChip from '../../../../../../../../../components/AttackPatternChip';
import TagsFragment from '../../../../../../../../../components/common/list/fragments/TagsFragment';
import useBodyItemsStyles from '../../../../../../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../../../../../../components/i18n';
import ItemStatus from '../../../../../../../../../components/ItemStatus';
import PlatformIcon from '../../../../../../../../../components/PlatformIcon';
import { useHelper } from '../../../../../../../../../store';
import {
  type AttackPattern,
  type EsInject,
  type EsScenario,
  type EsSimulation,
} from '../../../../../../../../../utils/api-types';
import buildStyles from '../ColumnStyles';
import InjectElementStyles from './InjectElementStyles';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

type Props = {
  columns: string[];
  element: EsInject | EsSimulation | EsScenario;
};

const InjectListElement = (props: Props) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();

  const { attackPatterns }: { attackPatterns: AttackPattern[] } = useHelper((helper: AttackPatternHelper) => {
    return { attackPatterns: helper.getAttackPatterns() };
  });

  const tooltip = (text: string) => {
    return (
      <Tooltip title={text} placement="bottom-start">
        <span>{text}</span>
      </Tooltip>
    );
  };

  /* eslint-disable react/display-name */
  // eslint doesn't seem to be able to infer the display names of subcomponents but react can
  const elementsFromColumn = (column: string) => {
    switch (column) {
      case 'base_attack_patterns_side':
        return (esElement: EsInject) => {
          if (esElement.base_attack_patterns_side && attackPatterns) {
            return esElement.base_attack_patterns_side.map((id: string) => {
              const attackPattern = attackPatterns.find(ap => ap.attack_pattern_id === id);
              return attackPattern && (
                <AttackPatternChip key={attackPattern.attack_pattern_id} attackPattern={attackPattern}></AttackPatternChip>
              );
            },
            );
          } else {
            return (<></>);
          }
        };
      case 'base_tags_side':
        return (esElement: EsInject | EsScenario | EsSimulation) => <TagsFragment tags={esElement.base_tags_side ?? []} />;
      case 'status':
      case 'inject_status':
        return (esElement: EsInject | EsScenario | EsSimulation) => {
          const isInject = esElement.base_entity === 'inject';
          let status;
          if ('inject_status' in esElement) {
            status = esElement.inject_status;
          } else if ('status' in esElement) {
            status = esElement.status;
          }
          return (<ItemStatus isInject={isInject} status={status} label={t(status || '-')} variant="inList" />);
        };
      case 'base_platforms_side_denormalized':
        return (esElement: EsInject | EsScenario | EsSimulation) => {
          return esElement.base_platforms_side_denormalized?.map(
            (platform: string) => <PlatformIcon key={platform} platform={platform} tooltip width={20} marginRight={theme.spacing(1)} />,
          );
        };
      case 'base_created_at':
        return (esElement: EsInject | EsScenario | EsSimulation) => {
          return tooltip(nsdt(esElement.base_created_at));
        };
      case 'base_updated_at':
        return (esElement: EsInject | EsScenario | EsSimulation) => {
          return tooltip(nsdt(esElement.base_updated_at));
        };
      case 'inject_execution_date':
        return (esElement: EsInject) => {
          return tooltip(nsdt(esElement.inject_execution_date));
        };
      default: return (esElement: EsInject | EsScenario | EsSimulation) => {
        const key = column as keyof typeof esElement;
        const text = esElement[key]?.toString() || '';
        return tooltip(text);
      };
    }
  };
    /* eslint-enable react/display-name */

  return (
  // TODO #3524 see EndpointListElement
    <ListItemButton classes={{ root: classes.item }} className="noDrag">
      <ListItemIcon>
        <Description color="primary" />
      </ListItemIcon>
      <ListItemText
        primary={(
          <div style={bodyItemsStyles.bodyItems}>
            {props.columns.map(col => (
              <div
                key={col}
                style={{
                  ...bodyItemsStyles.bodyItem,
                  ...buildStyles(props.columns, InjectElementStyles)[col],
                }}
              >
                {elementsFromColumn(col)(props.element)}
              </div>
            ))}
          </div>
        )}
      />
    </ListItemButton>
  );
};

export default InjectListElement;
