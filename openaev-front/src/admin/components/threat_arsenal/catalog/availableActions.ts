import createFacetFilter from '../../../../components/common/marketplace/filter/facetFilter';
import { useFormatter } from '../../../../components/i18n';
import {
  type AvailableActionOutput,
  type AvailableIntegrationActionOutput,
  type AvailablePayloadActionOutput,
} from '../../../../utils/api-types';

interface ByKind<R> {
  PAYLOAD: (action: AvailablePayloadActionOutput) => R;
  INTEGRATION: (action: AvailableIntegrationActionOutput) => R;
}

export const byKind = <R>(handlers: ByKind<R>) => (action: AvailableActionOutput): R => (
  action.action_kind === 'PAYLOAD' ? handlers.PAYLOAD(action) : handlers.INTEGRATION(action)
);

type ActionName = (action: Pick<AvailableActionOutput, 'action_id' | 'action_labels'>) => string;

export const useActionName = (): ActionName => {
  const { tPick } = useFormatter();
  return action => tPick(action.action_labels) || Object.values(action.action_labels)[0] || action.action_id;
};

const kindText = byKind<string>({
  PAYLOAD: action => action.action_payload_description ?? '',
  INTEGRATION: action => action.action_integration_title,
});

export const availableActionFilter = createFacetFilter<AvailableActionOutput, 'domains' | 'platforms'>(
  {
    domains: action => (action.action_domain ? [action.action_domain] : []),
    platforms: action => action.action_platforms,
  },
  action => [...Object.values(action.action_labels), kindText(action)].join(' '),
);

export const availableActionSorts = (nameOf: ActionName) => ({
  name: nameOf,
  publishedAt: byKind<string>({
    PAYLOAD: action => action.action_payload_published_at ?? '',
    INTEGRATION: () => '',
  }),
});
