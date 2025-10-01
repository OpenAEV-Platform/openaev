import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import arrayMutators from 'final-form-arrays';
import { type FunctionComponent, useContext } from 'react';
import { Form } from 'react-final-form';

import { type InjectOutputType, type InjectStore } from '../../../../actions/injects/Inject';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Inject, type InjectDependency } from '../../../../utils/api-types';
import { PermissionsContext } from '../Context';
import InjectChainsForm from './InjectChainsForm';

interface Props {
  inject: InjectStore;
  handleClose: () => void;
  onUpdateInject?: (data: Inject[]) => Promise<void>;
  injects?: InjectOutputType[];
  isDisabled: boolean;
}

const UpdateInjectLogicalChains: FunctionComponent<Props> = ({ inject, handleClose, onUpdateInject, injects, isDisabled }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  const { injectsMap } = useHelper((helper: InjectHelper) => ({ injectsMap: helper.getInjectsMap() }));

  const initialValues = {
    ...inject,
    inject_depends_to: injects !== undefined
      ? injects
          .filter(currentInject => currentInject.inject_depends_on !== undefined
            && currentInject.inject_depends_on !== null
            && currentInject.inject_depends_on
              .find(searchInject => searchInject.dependency_relationship?.inject_parent_id === inject.inject_id)
              !== undefined)
          .flatMap((currentInject) => {
            return currentInject.inject_depends_on;
          })
      : undefined,
    inject_depends_on: inject.inject_depends_on,
  };

  const onSubmit = async (data: Inject & { inject_depends_to: InjectDependency[] }) => {
    const injectUpdate = {
      ...data,
      inject_id: data.inject_id,
      inject_injector_contract: data.inject_injector_contract?.injector_contract_id,
      inject_depends_on: data.inject_depends_on,
    };

    const injectsToUpdate: Inject[] = [];

    const childrenIds = data.inject_depends_to.map((childrenInject: InjectDependency) => childrenInject.dependency_relationship?.inject_children_id);

    const injectsWithoutDependencies = injects
      ? injects
          .filter(currentInject => currentInject.inject_depends_on !== null
            && currentInject.inject_depends_on?.find(searchInject => searchInject.dependency_relationship?.inject_parent_id === data.inject_id) !== undefined
            && !childrenIds.includes(currentInject.inject_id))
          .map((currentInject) => {
            return {
              ...injectsMap[currentInject.inject_id],
              inject_id: currentInject.inject_id,
              inject_injector_contract: currentInject.inject_injector_contract?.injector_contract_id,
              inject_depends_on: undefined,
            } as unknown as Inject;
          })
      : [];

    injectsToUpdate.push(...injectsWithoutDependencies);

    childrenIds.forEach((childrenId) => {
      if (injects === undefined || childrenId === undefined) return;
      const children = injects.find(currentInject => currentInject.inject_id === childrenId);
      if (children !== undefined) {
        const injectDependsOnUpdate = data.inject_depends_to
          .find(dependsTo => dependsTo.dependency_relationship?.inject_children_id === childrenId);

        const injectChildrenUpdate: Inject = {
          ...injectsMap[children.inject_id],
          inject_id: children.inject_id,
          inject_injector_contract: children.inject_injector_contract?.injector_contract_id,
          inject_depends_on: injectDependsOnUpdate ? [injectDependsOnUpdate] : [],
        };
        injectsToUpdate.push(injectChildrenUpdate);
      }
    });
    if (onUpdateInject) {
      await onUpdateInject([injectUpdate as Inject, ...injectsToUpdate]);
    }

    handleClose();
  };

  return (
    <Form
      keepDirtyOnReinitialize={true}
      initialValues={initialValues}
      onSubmit={onSubmit}
      mutators={{
        ...arrayMutators,
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ form, handleSubmit, values, errors }) => {
        return (
          <form id="injectContentForm" onSubmit={handleSubmit}>
            <InjectChainsForm
              form={form}
              values={values}
              injects={injects}
              isDisabled={isDisabled}
            />
            <div style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: theme.spacing(1),
            }}
            >
              <Button
                variant="contained"
                onClick={handleClose}
              >
                {t('Cancel')}
              </Button>
              <Button
                variant="contained"
                color="secondary"
                type="submit"
                disabled={(errors !== undefined && Object.keys(errors).length > 0) || permissions.readOnly}
              >
                {t('Update')}
              </Button>
            </div>
          </form>
        );
      }}
    </Form>
  );
};

export default UpdateInjectLogicalChains;
