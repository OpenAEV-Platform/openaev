import type { ClassicEditor } from 'ckeditor5';
import { useRef } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { postDetectionRemediationAIRulesByPayload } from '../../../../actions/detection-remediation/detectionremediation-action';
import CKEditor from '../../../../components/CKEditor';
import { callDetectionRemediationAgent } from '../../../../utils/ai/agentApi';
import { type Collector, type PayloadInput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import useAI from '../../../../utils/hooks/useAI';
import { isNotEmptyField } from '../../../../utils/utils';
import {
  type DetectionRemediationForm,
  hasSpecificDirtyFieldAI,
  payloadFormToPayloadInputForAI,
  trackedFields,
} from '../../payloads/utils/payloadFormToPayloadInput';
import typeChar from '../../payloads/utils/typeChar';
import { type SnapshotEditionRemediationType } from '../utils/SnapshotRemediationContext';
import { useSnapshotRemediation } from '../utils/useSnapshotRemediation';
import DetectionRemediationInfo from './DetectionRemediationInfo';
import DetectionRemediationUseAriane from './DetectionRemediationUseAriane';

interface RemediationFormTabProps { activeTab: Collector }

const RemediationFormTab = ({ activeTab }: RemediationFormTabProps) => {
  const { control, watch, setValue, getValues, formState: { isValid, defaultValues } } = useFormContext();
  const { xtmOneConfigured } = useAI();

  const { snapshot, setSnapshot } = useSnapshotRemediation();
  const editorRef = useRef<ClassicEditor | null>(null);
  const fieldName = 'remediations.' + activeTab.collector_type;

  const setLoadingSnapshot = (collectorType: string, isLoading: boolean) => {
    setSnapshot((prev) => {
      const map = new Map(prev || []);
      map.set(collectorType, {
        ...map.get(collectorType) || {},
        isLoading,
        AIRules: getValues(fieldName).content,
      } as SnapshotEditionRemediationType);
      return map;
    });
  };

  const applyRulesToEditor = (rules: string) => {
    const editor = editorRef.current;
    const current = getValues(fieldName);
    const updated = {
      ...current,
      author_rule: 'AI',
    };
    setValue(fieldName, updated);

    if (!editor) {
      // Editor is not mounted yet (e.g. user triggered AI before CKEditor finished initialising):
      // clear the loading flag so the UI doesn't stay stuck in `isLoading: true`.
      setLoadingSnapshot(activeTab.collector_type, false);
      return;
    }

    typeChar(
      editor,
      rules,
      (value: string) => {
        const current = getValues(fieldName);
        const updated = {
          ...current,
          content: value,
          author_rule: 'AI',
        };
        setValue(fieldName, updated);
      },
    )
      .catch(() => undefined)
      .finally(() => {
        setTimeout(() => setLoadingSnapshot(activeTab.collector_type, false), 10);
      });
  };

  const onClickUseArianeViaXtmOne = async (agentSlug?: string) => {
    const payloadInput: Partial<PayloadInput> = payloadFormToPayloadInputForAI(getValues());

    setSnapshot((prev) => {
      const next = new Map(prev ?? []);
      const snapshot: SnapshotEditionRemediationType = {
        ...next.get(activeTab.collector_type) ?? {},
        trackedFields: structuredClone(getValues(trackedFields)),
        isLoading: true,
      };
      next.set(activeTab.collector_type, snapshot as SnapshotEditionRemediationType);
      return next;
    });

    if (!agentSlug) {
      MESSAGING$.notifyError('AI service is unavailable. No agent selected for detection generation.');
      setLoadingSnapshot(activeTab.collector_type, false);
      return;
    }

    try {
      // Build prompt with payload context for the remediation agent chain
      const prompt = `Generate ${activeTab.collector_type} detection rules for the following payload:\n\n`
        + `${JSON.stringify(payloadInput, null, 2)}`;

      const result = await callDetectionRemediationAgent(
        agentSlug,
        prompt,
        activeTab.collector_type,
      );
      if (result.status === 'success' && result.content) {
        applyRulesToEditor(result.content);
      } else {
        setLoadingSnapshot(activeTab.collector_type, false);
      }
    } catch {
      MESSAGING$.notifyError('AI service is unavailable.');
      setLoadingSnapshot(activeTab.collector_type, false);
    }
  };

  const onClickUseArianeLegacy = async () => {
    const payloadInput: Partial<PayloadInput> = payloadFormToPayloadInputForAI(getValues());

    setSnapshot((prev) => {
      const next = new Map(prev ?? []);
      const currentValue = structuredClone(getValues(trackedFields));
      const snapshot: SnapshotEditionRemediationType = {
        ...next.get(activeTab.collector_type) ?? {},
        trackedFields: currentValue,
        isLoading: true,
      };
      next.set(activeTab.collector_type, snapshot as SnapshotEditionRemediationType);
      return next;
    });

    return postDetectionRemediationAIRulesByPayload(activeTab.collector_type, payloadInput).then((value) => {
      applyRulesToEditor(value.data.rules);
    }).finally(() => {
      setLoadingSnapshot(activeTab.collector_type, false);
    });
  };

  const onClickUseAriane = xtmOneConfigured
    ? (agentSlug?: string) => onClickUseArianeViaXtmOne(agentSlug)
    : () => onClickUseArianeLegacy();

  function initSnap() {
    const formValues: DetectionRemediationForm = getValues(fieldName);
    const isAIRule = ['AI', 'AI_OUTDATED'].includes(formValues.author_rule);
    if (!isAIRule) return;

    setSnapshot((prev) => {
      const updatedSnapshot = new Map(prev || []);
      const currentSnapshot = updatedSnapshot.get(activeTab.collector_type) || {} as SnapshotEditionRemediationType;

      updatedSnapshot.set(activeTab.collector_type, {
        ...currentSnapshot,
        AIRules: formValues.content.trim(),
      });

      return updatedSnapshot;
    });
  }

  return (
    <>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
      }}
      >
        <div>
          {isNotEmptyField(watch(fieldName)?.content)
            && <DetectionRemediationInfo author_rule={watch(fieldName).author_rule} />}
        </div>
        <DetectionRemediationUseAriane
          payloadType={watch('payload_type')}
          collectorType={activeTab.collector_type}
          detectionRemediationContent={watch(fieldName)?.content}
          onSubmit={onClickUseAriane}
          isValidForm={isValid}
        />
      </div>
      <div
        key={activeTab.collector_type}
        style={{
          height: '250px',
          position: 'relative',
          display: activeTab.collector_type === activeTab.collector_type ? 'block' : 'none',
        }}
      >
        <Controller
          name={fieldName}
          control={control}
          defaultValue={{ content: '' }}
          render={({ field: { onChange, value } }) => (
            <CKEditor
              onReady={(editor) => {
                editorRef.current = editor;
                initSnap();
              }}
              id={'payload-remediation-editor' + activeTab.collector_type}
              data={value?.content}
              onChange={(_, editor) => {
                const latest = getValues(fieldName);

                onChange({
                  ...latest,
                  content: editor.getData(),
                });

                editor.editing.view.document.on('keyup', () => {
                  const latest = getValues(fieldName);
                  if (snapshot?.get(activeTab.collector_type)?.AIRules === latest.content) {
                    const isAiOutdated = hasSpecificDirtyFieldAI(defaultValues, snapshot?.get(activeTab.collector_type)?.trackedFields, getValues(trackedFields));
                    const defaultAuthor = snapshot?.get(activeTab.collector_type)?.trackedFields == undefined
                      ? defaultValues?.['remediations'][activeTab.collector_type].author_rule
                      : 'AI';
                    onChange({
                      ...latest,
                      content: editor.getData(),
                      author_rule: isAiOutdated ? 'AI_OUTDATED' : defaultAuthor,
                    });
                  } else {
                    onChange({
                      ...latest,
                      content: editor.getData(),
                      author_rule: 'HUMAN',
                    });
                  }
                });
              }}
            />
          )}
        />
      </div>
    </>
  );
};

export default RemediationFormTab;
