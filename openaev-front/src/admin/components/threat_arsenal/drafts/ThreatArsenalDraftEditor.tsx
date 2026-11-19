import { Alert, Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue, Spinner, Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useRef, useState } from 'react';

import { fetchThreatArsenalAction, updateThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import {
  createThreatArsenalDraft,
  discardThreatArsenalDraft,
  fetchThreatArsenalDrafts,
  publishThreatArsenalDraft,
  saveThreatArsenalDraft,
} from '../../../../actions/threat_arsenals/threatArsenal-draft-actions';
import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadDraftOutput, type PayloadVersionOutput, type ThreatArsenalAction, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../../utils/api-types-custom';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildInitialValues, type ThreatArsenalActionFormValues, toActionUpdateInput } from '../actionFormValues';
import ThreatArsenalActionForm from '../ThreatArsenalActionForm';
import SnapshotRemediationProvider from '../utils/SnapshotRemediationProvider';
import { defaultSelection, editOptions, parseSelection } from './draftSelection';

interface Props {
  /** The action on screen: its version is offered first when nothing else is being edited. */
  actionId: string;
  /** What to open on: a draft ("draft:<id>") or a new draft from a version ("new:<actionId>"). */
  initialSelection?: string;
  onClose: () => void;
  /** A version was published or sent for approval. */
  onPublished: (saved: ThreatArsenalAction | undefined) => void;
}

/**
 * Payload drafts, inside the edit drawer: the selector at the top chooses what is worked on (a
 * draft, a rejected version to fix, or a new draft from a published version), the form below is
 * the usual one, and its buttons save the draft or publish it ("Send for approval" without
 * "Approve content"). A published version is never edited: a new draft starts from it. Switching
 * with unsaved changes asks first.
 */
const ThreatArsenalDraftEditor: FunctionComponent<Props> = ({ actionId, initialSelection, onClose, onPublished }) => {
  const { t, tPick, nsdt } = useFormatter();
  const canApprove = useAbility().can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [drafts, setDrafts] = useState<PayloadDraftOutput[]>([]);
  const [selection, setSelection] = useState<string>('');
  const [values, setValues] = useState<ThreatArsenalActionFormValues | null>(null);
  // Mounts the form again on another selection, from its values.
  const [formKey, setFormKey] = useState(0);
  const [dirty, setDirty] = useState(false);
  const [switchTo, setSwitchTo] = useState<string | null>(null);
  const [discarding, setDiscarding] = useState(false);
  const [failed, setFailed] = useState(false);
  // The form's current values, for "Save draft" in the switch dialog.
  const formValues = useRef<(() => ThreatArsenalActionCreateCustomInput) | null>(null);

  useEffect(() => {
    Promise.all([fetchThreatArsenalVersions(actionId), fetchThreatArsenalDrafts(actionId)])
      .then(([versionsResponse, draftsResponse]: [{ data: PayloadVersionOutput[] }, { data: PayloadDraftOutput[] }]) => {
        setVersions(versionsResponse.data);
        setDrafts(draftsResponse.data);
        setSelection(initialSelection ?? defaultSelection(actionId, versionsResponse.data, draftsResponse.data));
      })
      .catch(() => setFailed(true));
  }, [actionId]);

  const current = parseSelection(selection);
  const currentDraft = current?.kind === 'draft' ? drafts.find(draft => draft.draft_id === current.id) : undefined;

  // The form's values for the selection: a draft's saved content, or the version it starts from.
  useEffect(() => {
    if (!current) return;
    setValues(null);
    setDirty(false);
    if (current.kind === 'draft') {
      if (currentDraft) {
        setValues(currentDraft.draft_content as ThreatArsenalActionFormValues);
        setFormKey(key => key + 1);
      }
      return;
    }
    fetchThreatArsenalAction(current.id).then((response: { data: ThreatArsenalActionFullOutput }) => {
      setValues({
        ...buildInitialValues(response.data, tPick(response.data.action_labels)),
        payload_id: response.data.action_payload_id,
      });
      setFormKey(key => key + 1);
    });
  }, [selection]);

  const options = editOptions(versions ?? [], drafts);
  const draftLabel = (draft: PayloadDraftOutput) => (draft.draft_rework
    ? t('v{version} · Rejected, being fixed', { version: draft.draft_source_version ?? '' })
    : t('Draft from v{version}', { version: draft.draft_source_version ?? '' }));

  const select = (next: string) => {
    if (next === selection) return;
    if (dirty) {
      setSwitchTo(next);
    } else {
      setSelection(next);
    }
  };

  const content = (formValues: ThreatArsenalActionCreateCustomInput) => ({ draft_content: formValues as unknown as Record<string, unknown> });

  // Saves the form in the selected draft, or starts a draft from the selected version.
  const saveDraft = async (values: ThreatArsenalActionCreateCustomInput): Promise<PayloadDraftOutput | undefined> => {
    const formValues = values;
    if (!current) return undefined;
    if (current.kind === 'draft') {
      const response = await saveThreatArsenalDraft(actionId, current.id, content(formValues));
      const saved = response.data as PayloadDraftOutput;
      setDrafts(list => [saved, ...list.filter(draft => draft.draft_id !== saved.draft_id)]);
      MESSAGING$.notifySuccess(t('Draft saved'));
      return saved;
    }
    const response = await createThreatArsenalDraft(current.id, content(formValues));
    const created = response.data as PayloadDraftOutput;
    setDrafts(list => [created, ...list]);
    setSelection(`draft:${created.draft_id}`);
    MESSAGING$.notifySuccess(t('Draft saved'));
    return created;
  };

  const publish = async (formValues: ThreatArsenalActionCreateCustomInput) => {
    if (!current) return;
    const input = toActionUpdateInput(formValues);
    let response;
    if (current.kind === 'new') {
      // Straight from a published version: a new version, as any update.
      response = await updateThreatArsenalAction(current.id, input);
    } else {
      const draftId = current.kind === 'draft'
        ? current.id
        : ((await createThreatArsenalDraft(current.id, content(formValues))).data as PayloadDraftOutput).draft_id;
      response = await publishThreatArsenalDraft(actionId, draftId, input);
    }
    onPublished(response.data as ThreatArsenalAction | undefined);
  };

  const discard = async () => {
    if (!currentDraft) return;
    await discardThreatArsenalDraft(actionId, currentDraft.draft_id);
    const left = drafts.filter(draft => draft.draft_id !== currentDraft.draft_id);
    setDrafts(left);
    setDiscarding(false);
    setSelection(defaultSelection(actionId, versions ?? [], left));
    MESSAGING$.notifySuccess(t('Draft discarded'));
  };

  if (failed) {
    return <Alert severity="error" title={t('The versions of this action could not be loaded.')} />;
  }
  if (!versions || !current) {
    return <Spinner size="md" />;
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
      }}
      >
        <Text variant="content-compact" className="text-default-secondary">{t('Editing')}</Text>
        <Select value={selection} onValueChange={select} disabled={!values}>
          <SelectTrigger aria-label={t('Editing')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {options.drafts.length > 0 && (
              <SelectGroup>
                <SelectLabel>{t('Drafts')}</SelectLabel>
                {options.drafts.map(draft => (
                  <SelectItem key={draft.draft_id} value={`draft:${draft.draft_id}`}>{draftLabel(draft)}</SelectItem>
                ))}
              </SelectGroup>
            )}
            {options.rejected.length > 0 && (
              <SelectGroup>
                <SelectLabel>{t('Rejected versions')}</SelectLabel>
                {options.rejected.map(version => (
                  <SelectItem key={version.payload_action_id} value={`rejected:${version.payload_action_id}`}>
                    {t('v{version} · Rejected', { version: version.payload_version ?? '' })}
                  </SelectItem>
                ))}
              </SelectGroup>
            )}
            <SelectGroup>
              <SelectLabel>{t('New draft from a version')}</SelectLabel>
              {options.published.map(version => (
                <SelectItem key={version.payload_action_id} value={`new:${version.payload_action_id}`}>
                  {t('New draft from v{version}', { version: version.payload_version ?? '' })}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
        {currentDraft && (
          <Text variant="content-compact" className="text-default-secondary">
            {currentDraft.draft_updated_by
              ? t('Saved by {name}, {date}', {
                  name: currentDraft.draft_updated_by,
                  date: nsdt(currentDraft.draft_updated_at),
                })
              : t('Saved {date}', { date: nsdt(currentDraft.draft_updated_at) })}
          </Text>
        )}
      </div>
      {values
        ? (
            <SnapshotRemediationProvider key={formKey}>
              <ThreatArsenalActionForm
                onSubmit={publish}
                handleClose={onClose}
                editing
                initialValues={values}
                draft={{
                  publishLabel: canApprove ? t('Publish version') : t('Send for approval'),
                  saved: current.kind === 'draft',
                  onSaveDraft: async (formValues) => {
                    await saveDraft(formValues);
                  },
                  onDiscard: currentDraft ? () => setDiscarding(true) : undefined,
                  onDirtyChange: setDirty,
                  bindValues: (getValues) => {
                    formValues.current = getValues;
                  },
                }}
              />
            </SnapshotRemediationProvider>
          )
        : <Spinner size="md" />}

      <Dialog open={switchTo !== null} onOpenChange={open => !open && setSwitchTo(null)}>
        <DialogContent size="sm">
          <DialogTitle>{t('Save your changes as a draft?')}</DialogTitle>
          <DialogBody>
            <Text variant="content-base">{t('You changed what you are editing. Save it as a draft before switching, or your changes are lost.')}</Text>
          </DialogBody>
          <DialogFooter>
            <Button
              priority="tertiary"
              onClick={() => {
                setSelection(switchTo ?? selection);
                setSwitchTo(null);
              }}
            >
              {t('Discard changes')}
            </Button>
            <Button priority="secondary" onClick={() => setSwitchTo(null)}>{t('Cancel')}</Button>
            <Button
              onClick={async () => {
                const next = switchTo;
                setSwitchTo(null);
                if (formValues.current) await saveDraft(formValues.current());
                if (next) setSelection(next);
              }}
            >
              {t('Save draft')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={discarding} onOpenChange={open => !open && setDiscarding(false)}>
        <DialogContent size="sm">
          <DialogTitle>{t('Discard draft')}</DialogTitle>
          <DialogBody>
            <Text variant="content-base">{t('Discard this draft? No version changes.')}</Text>
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={() => setDiscarding(false)}>{t('Cancel')}</Button>
            <Button variant="destructive" onClick={discard}>{t('Discard')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default ThreatArsenalDraftEditor;
