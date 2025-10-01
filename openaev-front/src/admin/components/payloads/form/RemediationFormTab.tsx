import type { ClassicEditor } from 'ckeditor5';
import { useRef } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { postDetectionRemediationAIRulesByPayload } from '../../../../actions/detection-remediation/detectionremediation-action';
import CKEditor from '../../../../components/CKEditor';
import { type Collector, type PayloadInput } from '../../../../utils/api-types';
import { isNotEmptyField } from '../../../../utils/utils';
import {
  type DetectionRemediationForm,
  hasSpecificDirtyFieldAI,
  payloadFormToPayloadInputForAI,
  trackedFields,
} from '../utils/payloadFormToPayloadInput';
import { type SnapshotEditionRemediationType } from '../utils/SnapshotRemediationContext';
import typeChar from '../utils/typeChar';
import { useSnapshotRemediation } from '../utils/useSnapshotRemediation';
import DetectionRemediationInfo from './DetectionRemediationInfo';
import DetectionRemediationUseAriane from './DetectionRemediationUseAriane';

interface RemediationFormTabProps { activeTab: Collector }

const RemediationFormTab = ({ activeTab }: RemediationFormTabProps) => {
  const { control, watch, setValue, getValues, formState: { isValid, defaultValues } } = useFormContext();

  const { snapshot, setSnapshot } = useSnapshotRemediation();
  const editorRef = useRef<ClassicEditor | null>(null);
  const fieldName = 'remediations.' + activeTab.collector_type;

  const onClickUseAriane = async () => {
    const payloadInput: Partial<PayloadInput> = payloadFormToPayloadInputForAI(getValues());

    setSnapshot((prev) => {
      let prevEdited = prev;
      if (!prevEdited) prevEdited = new Map<string, SnapshotEditionRemediationType>();

      const currentValue = structuredClone(getValues(trackedFields));
      const snapshot: SnapshotEditionRemediationType = {
        ...prevEdited.get(activeTab.collector_type) ?? {},
        trackedFields: currentValue,
        isLoading: true,
      };
      prevEdited.set(activeTab.collector_type, snapshot as SnapshotEditionRemediationType);
      return prevEdited;
    });

    return postDetectionRemediationAIRulesByPayload(activeTab.collector_type, payloadInput).then((value) => {
      const editor = editorRef.current;
      const current = getValues(fieldName);
      const updated = {
        ...current,
        author_rule: 'AI',
      };
      setValue(fieldName, updated);

      if (editor) {
        typeChar(
          editor,
          value.data.rules,
          (value: string) => {
            const current = getValues(fieldName);
            const updated = {
              ...current,
              content: value,
              author_rule: 'AI',
            };
            setValue(fieldName, updated);
          },
        ).then(() => {
          setTimeout(() => {
            setSnapshot((prev) => {
              const map = new Map(prev || []);

              map.set(activeTab.collector_type, {
                ...map.get(activeTab.collector_type) || {},
                isLoading: false,
                AIRules: getValues(fieldName).content,
              } as SnapshotEditionRemediationType);

              return map;
            });
          }, 10);
        });
      }
    }).finally(() => {
      setSnapshot((prev) => {
        const map = new Map(prev || []);

        map.set(activeTab.collector_type, {
          ...map.get(activeTab.collector_type) || {},
          isLoading: false,
          AIRules: getValues(fieldName).content,
        } as SnapshotEditionRemediationType);
        return map;
      });
    });
  };

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
