import { Alert } from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalActionUsageItem, type ThreatArsenalActionUsageOutput } from '../../../../utils/api-types';

export type PayloadUsageWarningKind = 'reject' | 'edit';

interface Props {
  usage?: ThreatArsenalActionUsageOutput;
  kind: PayloadUsageWarningKind;
}

const MESSAGES: Record<PayloadUsageWarningKind, string> = {
  reject: 'This payload is used in {atomicTestings} atomic testings, {scenarios} scenarios, {simulations} simulations. Rejecting it blocks their launch.',
  edit: 'Saving will send this payload back to Pending approval and block the launch of {atomicTestings} atomic testings, {scenarios} scenarios, {simulations} simulations until it is approved again.',
};

const isPayloadUsed = (usage?: ThreatArsenalActionUsageOutput) => !!usage
  && (usage.usage_atomic_testings_count ?? 0) + (usage.usage_scenarios_count ?? 0) + (usage.usage_simulations_count ?? 0) > 0;

/**
 * Warning shown before an approval change blocks launches (rejecting a payload, or an edit that
 * sends it back to pending): the counts, and links to the first atomic testings, scenarios and
 * simulations when the user can open them. Renders nothing when the payload is not used.
 */
const PayloadUsageWarning: FunctionComponent<Props> = ({ usage, kind }) => {
  const { t } = useFormatter();
  if (!usage || !isPayloadUsed(usage)) {
    return null;
  }

  const groups: {
    label: string;
    count: number;
    items?: ThreatArsenalActionUsageItem[];
    path: (id: string) => string;
  }[] = [
    {
      label: t('Atomic testings'),
      count: usage.usage_atomic_testings_count ?? 0,
      items: usage.usage_atomic_testings,
      path: id => `/admin/atomic_testings/${id}`,
    },
    {
      label: t('Scenarios'),
      count: usage.usage_scenarios_count ?? 0,
      items: usage.usage_scenarios,
      path: id => `/admin/scenarios/${id}`,
    },
    {
      label: t('Simulations'),
      count: usage.usage_simulations_count ?? 0,
      items: usage.usage_simulations,
      path: id => `/admin/simulations/${id}`,
    },
  ];
  const listed = groups.filter(group => group.items && group.items.length > 0);

  return (
    <Alert
      severity="warning"
      title={t(MESSAGES[kind], {
        atomicTestings: usage.usage_atomic_testings_count ?? 0,
        scenarios: usage.usage_scenarios_count ?? 0,
        simulations: usage.usage_simulations_count ?? 0,
      })}
      description={listed.length > 0 && (
        <ul style={{
          margin: 0,
          paddingLeft: 16,
        }}
        >
          {listed.map(group => (
            <li key={group.label}>
              {`${group.label}: `}
              {group.items!.map((item, index) => (
                <span key={item.id}>
                  {index > 0 && ', '}
                  <Link to={group.path(item.id)} target="_blank">{item.name}</Link>
                </span>
              ))}
              {group.count > group.items!.length && ` ${t('and {count} more', { count: group.count - group.items!.length })}`}
            </li>
          ))}
        </ul>
      )}
    />
  );
};

export default PayloadUsageWarning;
