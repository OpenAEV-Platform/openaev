import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

import { fetchThreatArsenalAction, fetchThreatArsenalActionOfPayload } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useFormatter } from '../../../../components/i18n';
import { THREAT_ARSENAL_BASE_URL } from '../../../../constants/BaseUrls';
import { type ThreatArsenalAction, type ThreatArsenalActionFullOutput, type ThreatArsenalActionIdOutput } from '../../../../utils/api-types';

// 'move' opens the Versions tab ready to move the injects left on older versions; 'saved' does the same right
// after a save that added a version, saying so, with nothing ticked: the user picks what moves.
export type OpenedTab = 'versions' | 'move' | 'saved';

export const versionsTabLink = (actionId: string, tab: OpenedTab = 'versions') => `${THREAT_ARSENAL_BASE_URL}?action=${encodeURIComponent(actionId)}&tab=versions${tab === 'move' ? '&focus=move' : ''}`;

/**
 * The action whose drawer is open, and the tab it opens on. A link to an action
 * (?action=<id>&tab=versions[&focus=move], or ?payload=<id>) searches it by name, then opens it once the list shows it.
 */
const useOpenedAction = (actions: ThreatArsenalAction[], searchByName: (name: string) => void) => {
  const { tPick } = useFormatter();
  const [openedAction, setOpenedAction] = useState<ThreatArsenalAction | null>(null);
  const [openedTab, setOpenedTab] = useState<OpenedTab | undefined>(undefined);

  const [searchParams] = useSearchParams();
  const linkedActionId = searchParams.get('action');
  const linkedPayloadId = searchParams.get('payload');
  const linkedFocus: OpenedTab = searchParams.get('focus') === 'move' ? 'move' : 'versions';
  const linkedTab = searchParams.get('tab') === 'versions' ? linkedFocus : undefined;
  // A state, not a ref: an action already listed when the link arrives opens at once.
  const [toOpen, setToOpen] = useState<string | null>(null);

  const openLinked = (actionId: string) => {
    setToOpen(actionId);
    fetchThreatArsenalAction(actionId).then(({ data }: { data: ThreatArsenalActionFullOutput }) => searchByName(tPick(data.action_labels)));
  };

  useEffect(() => {
    if (linkedActionId) openLinked(linkedActionId);
  }, [linkedActionId]);

  useEffect(() => {
    if (linkedPayloadId) {
      fetchThreatArsenalActionOfPayload(linkedPayloadId).then(({ data }: { data: ThreatArsenalActionIdOutput }) => openLinked(data.action_id));
    }
  }, [linkedPayloadId]);

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
  const showVersions = (action: ThreatArsenalAction, tab: OpenedTab = 'versions') => openAction(action, tab);
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
