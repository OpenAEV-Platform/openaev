import { RichTextEditor, type RichTextEditorAdapter } from '@filigran/rich-text-editor';
import { useRef } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { postDetectionRemediationAIRulesByPayload } from '../../../../actions/detection-remediation/detectionremediation-action';
import { type PayloadInput, type SecurityPlatform } from '../../../../utils/api-types';
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

interface RemediationFormTabProps { activeTab: SecurityPlatform }

const RemediationFormTab = ({ activeTab }: RemediationFormTabProps) => {
  const { control, watch, setValue, getValues, formState: { isValid, defaultValues } } = useFormContext();
  const { snapshot, setSnapshot } = useSnapshotRemediation();
  const editorRef = useRef<RichTextEditorAdapter | null>(null);
  const platformId = activeTab.asset_id;
  const fieldName = 'remediations.' + platformId;

  const setLoadingSnapshot = (securityPlatformId: string, isLoading: boolean) => {
    setSnapshot((prev) => {
      const map = new Map(prev || []);
      map.set(securityPlatformId, {
        ...map.get(securityPlatformId) || {},
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
      setLoadingSnapshot(platformId, false);
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
        setTimeout(() => setLoadingSnapshot(platformId, false), 10);
      });
  };

  const onClickUseAriane = async (agentSlug?: string) => {
    const payloadInput: Partial<PayloadInput> = payloadFormToPayloadInputForAI(getValues());

    setSnapshot((prev) => {
      const next = new Map(prev ?? []);
      const snapshot: SnapshotEditionRemediationType = {
        ...next.get(platformId) ?? {},
        trackedFields: structuredClone(getValues(trackedFields)),
        isLoading: true,
      };
      next.set(platformId, snapshot as SnapshotEditionRemediationType);
      return next;
    });

    return postDetectionRemediationAIRulesByPayload(platformId, payloadInput, agentSlug).then((value) => {
      applyRulesToEditor(value.data.rules);
    }).finally(() => {
      setLoadingSnapshot(platformId, false);
    });
  };

  function initSnap() {
    const formValues: DetectionRemediationForm = getValues(fieldName);
    const isAIRule = ['AI', 'AI_OUTDATED'].includes(formValues.author_rule);
    if (!isAIRule) return;

    setSnapshot((prev) => {
      const updatedSnapshot = new Map(prev || []);
      const currentSnapshot = updatedSnapshot.get(platformId) || {} as SnapshotEditionRemediationType;

      updatedSnapshot.set(platformId, {
        ...currentSnapshot,
        AIRules: formValues.content.trim(),
      });

      return updatedSnapshot;
    });
  }

  // Author rule update on user keypress (mirrors the CKEditor keyup listener)
  const handleKeyUp = () => {
    const latest = getValues(fieldName);
    if (snapshot?.get(platformId)?.AIRules === latest.content) {
      const isAiOutdated = hasSpecificDirtyFieldAI(
        defaultValues,
        snapshot?.get(platformId)?.trackedFields,
        getValues(trackedFields),
      );
      const defaultAuthor = snapshot?.get(platformId)?.trackedFields == undefined
        ? defaultValues?.['remediations']?.[platformId]?.author_rule
        : 'AI';
      setValue(fieldName, {
        ...latest,
        author_rule: isAiOutdated ? 'AI_OUTDATED' : defaultAuthor,
      });
    } else {
      setValue(fieldName, {
        ...latest,
        author_rule: 'HUMAN',
      });
    }
  };

  return (
    <>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
        minHeight: 40,
      }}
      >
        <div>
          {isNotEmptyField(watch(fieldName)?.content)
            && <DetectionRemediationInfo author_rule={watch(fieldName).author_rule} />}
        </div>
        <DetectionRemediationUseAriane
          payloadType={watch('payload_type')}
          securityPlatformId={platformId}
          securityPlatformName={activeTab.asset_name}
          detectionRemediationContent={watch(fieldName)?.content}
          onSubmit={onClickUseAriane}
          isValidForm={isValid}
        />
      </div>
      <div
        key={platformId}
        style={{
          height: '250px',
          position: 'relative',
        }}
        onKeyUp={handleKeyUp}
      >
        <Controller
          name={fieldName}
          control={control}
          defaultValue={{ content: '' }}
          render={({ field: { onChange, value } }) => (
            <RichTextEditor
              variant="outlined"
              onReady={(editor) => {
                editorRef.current = editor;
                initSnap();
              }}
              id={'payload-remediation-editor' + platformId}
              data={value?.content ?? ''}
              onChange={(_, editor) => {
                const latest = getValues(fieldName);
                onChange({
                  ...latest,
                  content: editor.getData(),
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
