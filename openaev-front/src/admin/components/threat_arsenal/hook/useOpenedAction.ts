import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';

// 'move' opens the Versions tab ready to move the injects left on older versions.
export type OpenedTab = 'versions' | 'move';

/**
 * The action whose drawer is open, and the tab it opens on. A link to an action
 * (?action=<id>&tab=versions[&focus=move]) searches it by name, then opens it once the list shows it.
 */
const useOpenedAction = (actions: ThreatArsenalAction[], searchByName: (name: string) => void) => {
  const { tPick } = useFormatter();
  const [openedAction, setOpenedAction] = useState<ThreatArsenalAction | null>(null);
  const [openedTab, setOpenedTab] = useState<OpenedTab | undefined>(undefined);

  const [searchParams] = useSearchParams();
  const linkedActionId = searchParams.get('action');
  const linkedFocus: OpenedTab = searchParams.get('focus') === 'move' ? 'move' : 'versions';
  const linkedTab = searchParams.get('tab') === 'versions' ? linkedFocus : undefined;
  // A state, not a ref: an action already listed when the link arrives opens at once.
  const [toOpen, setToOpen] = useState<string | null>(null);

  useEffect(() => {
    if (!linkedActionId) return;
    setToOpen(linkedActionId);
    fetchThreatArsenalAction(linkedActionId).then(({ data }: { data: ThreatArsenalActionFullOutput }) => searchByName(tPick(data.action_labels)));
  }, [linkedActionId]);

  useEffect(() => {
    const linked = actions.find(action => action.injector_contract_id === toOpen);
    if (linked) {
      setToOpen(null);
      setOpenedTab(linkedTab);
      setOpenedAction(linked);
    }
  }, [actions, toOpen]);

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
