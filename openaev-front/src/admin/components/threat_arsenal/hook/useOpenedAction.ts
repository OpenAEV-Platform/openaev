import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';

import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';

type OpenedTab = 'versions' | undefined;

/**
 * The action whose drawer is open, and the tab it opens on. A link to an action
 * (?action=<id>&tab=versions) searches it by name, then opens it once the list shows it.
 */
const useOpenedAction = (actions: ThreatArsenalAction[], searchByName: (name: string) => void) => {
  const { tPick } = useFormatter();
  const [openedAction, setOpenedAction] = useState<ThreatArsenalAction | null>(null);
  const [openedTab, setOpenedTab] = useState<OpenedTab>(undefined);

  const [searchParams] = useSearchParams();
  const linkedActionId = searchParams.get('action');
  const linkedTab: OpenedTab = searchParams.get('tab') === 'versions' ? 'versions' : undefined;
  const toOpen = useRef<string | null>(null);

  useEffect(() => {
    if (!linkedActionId) return;
    toOpen.current = linkedActionId;
    fetchThreatArsenalAction(linkedActionId).then(({ data }: { data: ThreatArsenalActionFullOutput }) => searchByName(tPick(data.action_labels)));
  }, [linkedActionId]);

  useEffect(() => {
    const linked = actions.find(action => action.injector_contract_id === toOpen.current);
    if (linked) {
      toOpen.current = null;
      setOpenedTab(linkedTab);
      setOpenedAction(linked);
    }
  }, [actions]);

  const openAction = (action: ThreatArsenalAction, tab?: OpenedTab) => {
    setOpenedTab(tab);
    setOpenedAction(action);
  };
  const showVersions = (action: ThreatArsenalAction) => openAction(action, 'versions');
  const closeAction = () => {
    setOpenedAction(null);
    setOpenedTab(undefined);
  };

  return {
    openedAction,
    openedTab,
    openAction,
    showVersions,
    closeAction,
  };
};

export default useOpenedAction;
