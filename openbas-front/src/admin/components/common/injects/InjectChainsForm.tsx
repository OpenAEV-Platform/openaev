import { Add, DeleteOutlined, ExpandMore } from '@mui/icons-material';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  type SelectChangeEvent,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FormApi } from 'final-form';
import { type FunctionComponent, type ReactElement, type ReactNode, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type ConditionElement, type ConditionType, type Content, type ConvertedContentType, type Dependency, type InjectOutputType } from '../../../../actions/injects/Inject';
import ClickableChip, { type Element } from '../../../../components/common/chips/ClickableChip';
import ClickableModeChip from '../../../../components/common/chips/ClickableModeChip';
import { useFormatter } from '../../../../components/i18n';
import { type Inject, type InjectDependency, type InjectDependencyCondition, type InjectOutput } from '../../../../utils/api-types';
import { capitalize } from '../../../../utils/String';

const useStyles = makeStyles()(() => ({
  container: {
    display: 'inline-flex',
    alignItems: 'center',
  },
  importerStyle: {
    display: 'flex',
    alignItems: 'center',
  },
  labelExecutionCondition: { color: '#7c8088' },
}));

interface Props {
  values: Inject & { inject_depends_to: InjectDependency[] };
  form: FormApi<Inject & { inject_depends_to: InjectDependency[] }, Partial<Inject & { inject_depends_to: InjectDependency[] }>>;
  injects?: InjectOutputType[];
  isDisabled: boolean;
}

const InjectForm: FunctionComponent<Props> = ({ values, form, injects, isDisabled }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  // List of parents
  const [parents, setParents] = useState<Dependency[]>(
    () => {
      if (values.inject_depends_on) {
        return values.inject_depends_on?.filter(searchInject => searchInject.dependency_relationship?.inject_children_id === values.inject_id)
          .map((inject, index) => {
            return {
              inject: injects?.find(currentInject => currentInject.inject_id === inject.dependency_relationship?.inject_parent_id),
              index,
            };
          });
      }
      return [];
    },

  );

  // List of childrens
  const [childrens, setChildrens] = useState<Dependency[]>(
    () => {
      if (injects !== undefined) {
        return injects?.filter(
          searchInject => searchInject.inject_depends_on?.find(
            dependsOnSearch => dependsOnSearch.dependency_relationship?.inject_parent_id === values.inject_id,
          ) !== undefined,
        )
          .map((inject, index) => {
            return {
              inject,
              index,
            };
          });
      }
      return [];
    },
  );

  // Property to deactivate the add children button if there are no children available anymore
  const [addChildrenButtonDisabled, setAddChildrenButtonDisabled] = useState(false);
  useEffect(() => {
    const availableChildrensNumber = injects ? injects.filter(currentInject => currentInject.inject_depends_duration > values.inject_depends_duration).length : 0;
    setAddChildrenButtonDisabled(childrens ? childrens.length >= availableChildrensNumber : true);
  }, [childrens]);

  /**
   * Transform an inject dependency into ConditionElement
   * @param injectDependsOn an array of injectDependency
   */
  const getConditionContentParent = (injectDependsOn: (InjectDependency | undefined)[]) => {
    const conditions: ConditionType[] = [];
    if (injectDependsOn) {
      injectDependsOn.forEach((parent) => {
        if (parent !== undefined) {
          conditions.push({
            parentId: parent.dependency_relationship?.inject_parent_id,
            childrenId: parent.dependency_relationship?.inject_children_id,
            mode: parent.dependency_condition?.mode,
            conditionElement: parent.dependency_condition?.conditions?.map((dependencyCondition, indexCondition) => {
              return {
                name: dependencyCondition.key,
                value: dependencyCondition.value!,
                key: dependencyCondition.key,
                index: indexCondition,
              };
            }),
          });
        }
      });
    }
    return conditions;
  };

  /**
   * Transform an inject dependency into ConditionElement
   * @param injectDependsTo an array of injectDependency
   */
  const getConditionContentChildren = (injectDependsTo: (InjectDependency | undefined)[]) => {
    const conditions: ConditionType[] = [];
    injectDependsTo.forEach((children) => {
      if (children !== undefined) {
        conditions.push({
          parentId: values.inject_id,
          childrenId: children.dependency_relationship?.inject_children_id,
          mode: children.dependency_condition?.mode,
          conditionElement: children.dependency_condition?.conditions?.map((dependencyCondition, indexCondition) => {
            return {
              name: dependencyCondition.key,
              value: dependencyCondition.value!,
              key: dependencyCondition.key,
              index: indexCondition,
            };
          }),
        });
      }
    });
    return conditions;
  };

  const [parentConditions, setParentConditions] = useState(getConditionContentParent(values.inject_depends_on ? values.inject_depends_on : []));
  const [childrenConditions, setChildrenConditions] = useState(getConditionContentChildren(values.inject_depends_to));

  /**
   * Get the inject dependency object from dependency ones
   * @param deps the inject depencies
   */
  const injectDependencyFromDependency = (deps: Dependency[]) => {
    return deps.flatMap(dependency => (dependency.inject?.inject_depends_on !== null ? dependency.inject?.inject_depends_on : []));
  };

  /**
   * Handle the change of the parent
   * @param _event the event
   * @param parent the parent key
   */
  const handleChangeParent = (_event: SelectChangeEvent<string>, parent: ReactNode) => {
    const rx = /\.\$select-parent-(.*)-inject-(.*)/g;
    if (!parent) return;
    let key = '';
    const parentElement = parent as ReactElement;
    if ('key' in parentElement && parentElement.key !== null) {
      key = parentElement.key;
    }
    if (key === null) {
      return;
    }
    const arr = rx.exec(key);

    if (parents === undefined || arr === null || injects === undefined) return;
    const newInject = injects.find(currentInject => currentInject.inject_id === arr[2]);
    const newParents = parents
      .map((element) => {
        if (element.index === parseInt(arr[1], 10)) {
          const previousInject = injects.find(value => value.inject_id === element.inject?.inject_id);
          if (previousInject?.inject_depends_on !== undefined) {
            previousInject!.inject_depends_on = previousInject!.inject_depends_on?.filter(
              dependsOn => dependsOn.dependency_relationship?.inject_children_id !== values.inject_id,
            );
          }
          return {
            inject: newInject!,
            index: element.index,
          };
        }
        return element;
      });
    setParents(newParents);

    const baseInjectDependency: InjectDependency = {
      dependency_relationship: {
        inject_parent_id: newInject?.inject_id,
        inject_children_id: values.inject_id,
      },
      dependency_condition: {
        conditions: [
          {
            key: 'Execution',
            operator: 'eq',
            value: true,
          },
        ],
        mode: 'and',
      },
    };
    setParentConditions(getConditionContentParent([baseInjectDependency]));

    form.mutators.setValue(
      'inject_depends_on',
      [baseInjectDependency],
    );
  };

  /**
   * Add a new parent inject
   */
  const addParent = () => {
    setParents([...parents, {
      inject: undefined,
      index: parents.length,
    }]);
  };

  /**
   * Handle the change of a children
   * @param _event
   * @param child
   */
  const handleChangeChildren = (_event: SelectChangeEvent<string>, child: ReactNode) => {
    const rx = /\.\$select-children-(.*)-inject-(.*)/g;
    if (!child) return;
    let key = '';
    const childElement = child as ReactElement;
    if ('key' in (childElement as ReactElement) && childElement.key !== null) {
      key = childElement.key;
    }
    if (key === null) {
      return;
    }
    const arr = rx.exec(key);

    if (childrens === undefined || arr === null || injects === undefined) return;
    const newInject = injects.find(currentInject => currentInject.inject_id === arr[2]);
    const newChildrens = childrens
      .map((element) => {
        if (element.index === parseInt(arr[1], 10)) {
          const baseInjectDependency: InjectDependency = {
            dependency_relationship: {
              inject_parent_id: values.inject_id,
              inject_children_id: newInject?.inject_id,
            },
            dependency_condition: {
              conditions: [
                {
                  key: 'Execution',
                  operator: 'eq',
                  value: true,
                },
              ],
              mode: 'and',
            },
          };
          newInject!.inject_depends_on = [baseInjectDependency];
          return {
            inject: newInject!,
            index: element.index,
          };
        }
        return element;
      });

    setChildrens(newChildrens);

    const dependsTo = injectDependencyFromDependency(newChildrens);
    form.mutators.setValue('inject_depends_to', dependsTo);

    if (newInject!.inject_depends_on !== null) {
      setChildrenConditions(getConditionContentChildren(dependsTo.filter(dep => dep !== undefined)));
    }
  };

  /**
   * Add a new children inject
   */
  const addChildren = () => {
    setChildrens([...childrens, {
      inject: undefined,
      index: childrens.length,
    }]);
  };

  /**
   * Delete a parent inject
   * @param parent
   */
  const deleteParent = (parent: Dependency) => {
    const parentIndexInArray = parents.findIndex(currentParent => currentParent.index === parent.index);

    if (parentIndexInArray > -1) {
      const newParents = [
        ...parents.slice(0, parentIndexInArray),
        ...parents.slice(parentIndexInArray + 1),
      ];
      setParents(newParents);

      form.mutators.setValue(
        'inject_depends_on',
        injectDependencyFromDependency(newParents),
      );
    }
  };

  /**
   * Delete a children inject
   * @param children
   */
  const deleteChildren = (children: Dependency) => {
    const childrenIndexInArray = childrens.findIndex(currentChildren => currentChildren.inject?.inject_id === children.inject?.inject_id);

    if (childrenIndexInArray > -1) {
      const newChildrens = [
        ...childrens.slice(0, childrenIndexInArray),
        ...childrens.slice(childrenIndexInArray + 1),
      ];
      setChildrens(newChildrens);

      form.mutators.setValue('inject_depends_to', injectDependencyFromDependency(newChildrens));
    }
  };

  /**
   * Returns an updated depends on from a ConditionType
   * @param conditions
   * @param switchIds
   */
  const updateDependsCondition = (conditions: ConditionType) => {
    const result: InjectDependencyCondition = {
      mode: conditions.mode === 'and' ? 'and' : 'or',
      conditions: conditions.conditionElement?.map((value) => {
        return {
          value: value.value,
          key: value.key,
          operator: 'eq',
        };
      }),
    };
    return result;
  };

  /**
   * Returns an updated depends on from a ConditionType
   * @param conditions
   * @param switchIds
   */
  const updateDependsOn = (conditions: ConditionType) => {
    const result: InjectDependency = {
      dependency_relationship: {
        inject_parent_id: conditions.parentId,
        inject_children_id: conditions.childrenId,
      },
      dependency_condition: updateDependsCondition(conditions),
    };
    return result;
  };

  /**
   * Get the list of available expectations
   * @param inject
   */
  const getAvailableExpectations = (inject: InjectOutputType | undefined) => {
    if (inject?.inject_content !== null && inject?.inject_content !== undefined && (inject.inject_content as Content).expectations !== undefined) {
      const expectations = (inject.inject_content as Content).expectations.map(expectation => (expectation.expectation_type === 'MANUAL' ? expectation.expectation_name : capitalize(expectation.expectation_type)));
      return ['Execution', ...expectations];
    }
    if (inject?.inject_injector_contract !== undefined
      && (inject?.inject_injector_contract.convertedContent as unknown as ConvertedContentType).fields.find(field => field.key === 'expectations')) {
      const predefinedExpectations = (inject.inject_injector_contract.convertedContent as unknown as ConvertedContentType).fields?.find(field => field.key === 'expectations')
        ?.predefinedExpectations.map(expectation => (expectation.expectation_type === 'MANUAL' ? expectation.expectation_name : capitalize(expectation.expectation_type)));
      if (predefinedExpectations !== undefined) {
        return ['Execution', ...predefinedExpectations];
      }
    }
    return ['Execution'];
  };

  /**
   * Add a new condition to a parent inject
   * @param parent
   */
  const addConditionParent = (parent: Dependency) => {
    const currentConditions = parentConditions.find(currentCondition => parent.inject!.inject_id === currentCondition.parentId);

    if (parent.inject !== undefined && currentConditions !== undefined) {
      let expectationString = 'Execution';
      if (currentConditions?.conditionElement !== undefined) {
        expectationString = getAvailableExpectations(parent.inject)
          .find(expectation => !currentConditions?.conditionElement?.find(conditionElement => conditionElement.key === expectation));
      }
      currentConditions.conditionElement?.push({
        key: expectationString,
        name: expectationString,
        value: true,
        index: currentConditions.conditionElement?.length,
      });

      setParentConditions(parentConditions);

      const element = parentConditions.find(conditionElement => conditionElement.childrenId === values.inject_id);

      const dep: InjectDependency = {
        dependency_relationship: {
          inject_parent_id: element?.parentId,
          inject_children_id: element?.childrenId,
        },
        dependency_condition: {
          mode: element?.mode === '&&' ? 'and' : 'or',
          conditions: element?.conditionElement
            ? element?.conditionElement.map((value) => {
                return {
                  key: value.key,
                  value: value.value,
                  operator: 'eq',
                };
              })
            : [],
        },
      };

      form.mutators.setValue(
        'inject_depends_on',
        [dep],
      );
    }
  };

  /**
   * Add a new condition to a children inject
   * @param children
   */
  const addConditionChildren = (children: Dependency) => {
    const currentConditions = childrenConditions.find(currentCondition => children.inject!.inject_id === currentCondition.childrenId);

    if (children.inject !== undefined && currentConditions !== undefined) {
      const updatedChildren = childrens.find(currentChildren => currentChildren.inject?.inject_id === children.inject?.inject_id);
      let expectationString = 'Execution';
      if (currentConditions?.conditionElement !== undefined) {
        expectationString = getAvailableExpectations(values as InjectOutput as InjectOutputType)
          .find(expectation => !currentConditions?.conditionElement?.find(conditionElement => conditionElement.key === expectation));
      }
      currentConditions.conditionElement?.push({
        key: expectationString,
        name: expectationString,
        value: true,
        index: currentConditions.conditionElement?.length,
      });

      if (updatedChildren?.inject?.inject_depends_on !== undefined) {
        updatedChildren.inject.inject_depends_on = [updateDependsOn(currentConditions)];
      }

      setChildrenConditions(childrenConditions);
      form.mutators.setValue(
        'inject_depends_to',
        injectDependencyFromDependency(childrens),
      );
    }
  };

  /**
   * Handle a change in a condition of a parent element
   * @param newElement
   * @param conditions
   * @param condition
   * @param parent
   */
  const changeParentElement = (newElement: Element, conditions: ConditionType, condition: ConditionElement, parent: Dependency) => {
    const newConditionElements = conditions.conditionElement?.map((newConditionElement) => {
      if (newConditionElement.index === condition.index) {
        return {
          index: condition.index,
          key: newElement.key,
          name: `${conditions.parentId}-${newElement.key}-Success`,
          value: newElement.value === 'Success',
        };
      }
      return newConditionElement;
    });
    const newParentConditions = parentConditions.map((parentCondition) => {
      if (parentCondition.parentId === parent.inject?.inject_id) {
        return {
          ...parentCondition,
          conditionElement: newConditionElements,
        };
      }
      return parentCondition;
    });
    setParentConditions(newParentConditions);

    const element = newParentConditions?.find(conditionElement => conditionElement.parentId === conditions.parentId);
    const dep: InjectDependency = {
      dependency_relationship: {
        inject_parent_id: element?.parentId,
        inject_children_id: element?.childrenId,
      },
      dependency_condition: {
        mode: element?.mode === '&&' ? 'and' : 'or',
        conditions: element?.conditionElement
          ? element?.conditionElement.map((value) => {
              return {
                key: value.key,
                value: value.value,
                operator: 'eq',
              };
            })
          : [],
      },
    };

    form.mutators.setValue(
      'inject_depends_on',
      [dep],
    );
  };

  /**
   * Handle a change in a condition of a children element
   * @param newElement
   * @param conditions
   * @param condition
   * @param children
   */
  const changeChildrenElement = (newElement: Element, conditions: ConditionType, condition: ConditionElement, children: Dependency) => {
    const newConditionElements = conditions.conditionElement?.map((newConditionElement) => {
      if (newConditionElement.index === condition.index) {
        return {
          index: condition.index,
          key: newElement.key,
          name: `${conditions.childrenId}-${newElement.key}-Success`,
          value: newElement.value === 'Success',
        };
      }
      return newConditionElement;
    });
    const newChildrenConditions = childrenConditions.map((childrenCondition) => {
      if (childrenCondition.childrenId === children.inject?.inject_id) {
        return {
          ...childrenCondition,
          conditionElement: newConditionElements,
        };
      }
      return childrenCondition;
    });
    setChildrenConditions(newChildrenConditions);

    const updatedChildren = childrens.find(currentChildren => currentChildren.inject?.inject_id === children.inject?.inject_id);
    const newCondition = newChildrenConditions.find(childrenCondition => childrenCondition.childrenId === children.inject?.inject_id);
    if (updatedChildren?.inject?.inject_depends_on !== undefined && newCondition !== undefined) {
      updatedChildren.inject.inject_depends_on = [updateDependsOn(newCondition)];
    }
    form.mutators.setValue(
      'inject_depends_to',
      injectDependencyFromDependency(childrens),
    );
  };

  /**
   * Changes the mode (AND/OR) in a parent inject
   * @param conditions
   * @param condition
   */
  const changeModeParent = (conditions: ConditionType[] | undefined, condition: ConditionType) => {
    const newConditionElements = conditions?.map((currentCondition) => {
      if (currentCondition.parentId === condition.parentId) {
        return {
          ...currentCondition,
          mode: currentCondition.mode === 'and' ? 'or' : 'and',
        };
      }
      return currentCondition;
    });
    if (newConditionElements !== undefined) {
      setParentConditions(newConditionElements);
    }

    const element = newConditionElements?.find(conditionElement => conditionElement.parentId === condition.parentId);
    const dep: InjectDependency = {
      dependency_relationship: {
        inject_parent_id: element?.parentId,
        inject_children_id: element?.childrenId,
      },
      dependency_condition: {
        mode: element?.mode === '&&' ? 'and' : 'or',
        conditions: element?.conditionElement
          ? element?.conditionElement.map((value) => {
              return {
                key: value.key,
                value: value.value,
                operator: 'eq',
              };
            })
          : [],
      },
    };

    form.mutators.setValue(
      'inject_depends_on',
      [dep],
    );
  };

  /**
   * Changes the mode (AND/OR) in a children inject
   * @param conditions
   * @param condition
   */
  const changeModeChildren = (conditions: ConditionType[] | undefined, condition: ConditionType) => {
    const newConditionElements = conditions?.map((currentCondition) => {
      if (currentCondition.childrenId === condition.childrenId) {
        return {
          ...currentCondition,
          mode: currentCondition.mode === 'and' ? 'or' : 'and',
        };
      }
      return currentCondition;
    });
    if (newConditionElements !== undefined) {
      setChildrenConditions(newConditionElements);
    }

    const newCurrentCondition = newConditionElements?.find(currentCondition => currentCondition.childrenId === condition.childrenId);
    const updatedChildren = childrens.find(currentChildren => currentChildren.inject?.inject_id === newCurrentCondition?.childrenId);
    if (updatedChildren?.inject?.inject_depends_on !== undefined && newCurrentCondition !== undefined) {
      updatedChildren.inject.inject_depends_on = [updateDependsOn(newCurrentCondition)];
    }
    form.mutators.setValue(
      'inject_depends_to',
      injectDependencyFromDependency(childrens),
    );
  };

  /**
   * Delete a condition from a parent inject
   * @param conditions
   * @param condition
   */
  const deleteConditionParent = (conditions: ConditionType, condition: ConditionElement) => {
    const newConditionElements = parentConditions.map((currentCondition) => {
      if (currentCondition.parentId === conditions.parentId) {
        return {
          ...currentCondition,
          conditionElement: currentCondition.conditionElement?.filter(element => element.index !== condition.index),
        };
      }
      return currentCondition;
    });
    setParentConditions(newConditionElements);

    const element = newConditionElements.find(conditionElement => conditionElement.parentId === conditions.parentId);
    const dep: InjectDependency = {
      dependency_relationship: {
        inject_parent_id: element?.parentId,
        inject_children_id: element?.childrenId,
      },
      dependency_condition: {
        mode: element?.mode === '&&' ? 'and' : 'or',
        conditions: element?.conditionElement
          ? element?.conditionElement.map((value) => {
              return {
                key: value.key,
                value: value.value,
                operator: 'eq',
              };
            })
          : [],
      },
    };

    form.mutators.setValue(
      'inject_depends_on',
      [dep],
    );
  };

  /**
   * Delete a condition from a children inject
   * @param conditions
   * @param condition
   */
  const deleteConditionChildren = (conditions: ConditionType, condition: ConditionElement) => {
    const newConditionElements = childrenConditions.map((currentCondition) => {
      if (currentCondition.childrenId === conditions.childrenId) {
        return {
          ...currentCondition,
          conditionElement: currentCondition.conditionElement?.filter(element => element.index !== condition.index),
        };
      }
      return currentCondition;
    });
    setChildrenConditions(newConditionElements);

    const updatedChildren = childrens.find(currentChildren => currentChildren.inject?.inject_id === conditions.childrenId);
    if (updatedChildren?.inject?.inject_depends_on !== undefined && conditions !== undefined) {
      const newCondition = newConditionElements.find(currentCondition => currentCondition.childrenId === conditions.childrenId);
      if (newCondition !== undefined) updatedChildren.inject.inject_depends_on = [updateDependsOn(newCondition)];
    }
    form.mutators.setValue(
      'inject_depends_to',
      injectDependencyFromDependency(childrens),
    );
  };

  /**
   * Whether or not we can add a new condition
   * @param inject
   * @param conditions
   */
  const canAddConditions = (inject: InjectOutputType, conditions?: ConditionType) => {
    const expectationsNumber = getAvailableExpectations(inject).length;
    if (conditions === undefined || conditions.conditionElement === undefined) return true;

    return conditions?.conditionElement.length < expectationsNumber;
  };

  /**
   * Return a clickable parent chip
   * @param parent
   */
  const getClickableParentChip = (parent: Dependency) => {
    const parentChip = parentConditions.find(parentCondition => parent.inject !== undefined && parentCondition.parentId === parent.inject.inject_id);
    if (parentChip === undefined || parentChip.conditionElement === undefined) return (<></>);
    return parentChip.conditionElement.map((condition, conditionIndex) => {
      const conditions = parentConditions
        .find(parentCondition => parent.inject !== undefined && parentCondition.parentId === parent.inject.inject_id);
      if (conditions?.conditionElement !== undefined) {
        return (
          <div key={`${condition.name}-${condition.index}`} style={{ display: 'contents' }}>
            <ClickableChip
              selectedElement={{
                key: condition.key,
                operator: 'is',
                value: condition.value ? 'Success' : 'Fail',
              }}
              pristine={true}
              availableKeys={getAvailableExpectations(parent.inject)}
              availableOperators={['is']}
              availableValues={['Success', 'Fail']}
              onDelete={
                conditions.conditionElement.length > 1 ? () => {
                  deleteConditionParent(conditions, condition);
                } : undefined
              }
              onChange={(newElement) => {
                changeParentElement(newElement, conditions, condition, parent);
              }}
            />
            {conditionIndex < conditions.conditionElement.length - 1
              && (
                <ClickableModeChip
                  mode={conditions.mode}
                  onClick={() => {
                    changeModeParent(parentConditions, conditions);
                  }}
                />
              )}
          </div>
        );
      }
      return (<></>);
    });
  };

  /**
   * Return a clickable children chip
   * @param parent
   */
  const getClickableChildrenChip = (children: Dependency) => {
    const childrenChip = childrenConditions.find(childrenCondition => children.inject !== undefined && childrenCondition.childrenId === children.inject.inject_id);
    if (childrenChip?.conditionElement === undefined) return (<></>);
    return childrenChip
      .conditionElement.map((condition, conditionIndex) => {
        const conditions = childrenConditions
          .find(childrenCondition => childrenCondition.childrenId === children.inject?.inject_id);
        if (conditions?.conditionElement !== undefined) {
          return (
            <div key={`${condition.name}-${condition.index}`} style={{ display: 'contents' }}>
              <ClickableChip
                selectedElement={{
                  key: condition.key,
                  operator: 'is',
                  value: condition.value ? 'Success' : 'Fail',
                }}
                pristine={true}
                availableKeys={getAvailableExpectations(injects?.find(currentInject => currentInject.inject_id === values.inject_id))}
                availableOperators={['is']}
                availableValues={['Success', 'Fail']}
                onDelete={
                  conditions.conditionElement.length > 1 ? () => {
                    deleteConditionChildren(conditions, condition);
                  } : undefined
                }
                onChange={(newElement) => {
                  changeChildrenElement(newElement, conditions, condition, children);
                }}
              />
              {conditionIndex < conditions.conditionElement.length - 1
                && (
                  <ClickableModeChip
                    mode={conditions?.mode}
                    onClick={() => {
                      changeModeChildren(childrenConditions, conditions);
                    }}
                  />
                )}
            </div>
          );
        }
        return (<></>);
      });
  };

  return (
    <>
      <div className={classes.importerStyle}>
        <Typography variant="h2" sx={{ m: 0 }}>
          {t('Parent')}
        </Typography>
        <IconButton
          color="secondary"
          aria-label="Add"
          size="large"
          disabled={parents.length > 0
            || injects?.filter(currentInject => currentInject.inject_depends_duration < values.inject_depends_duration).length === 0 || isDisabled}
          onClick={addParent}
        >
          <Add fontSize="small" />
        </IconButton>
      </div>

      {parents.map((parent, index) => {
        return (
          <Accordion
            key={`accordion-parent-${parent.index}`}
            variant="outlined"
            style={{
              width: '100%',
              marginBottom: '10px',
            }}
            disabled={isDisabled}
          >
            <AccordionSummary
              expandIcon={<ExpandMore />}
            >
              <div className={classes.container}>
                <Typography>
                  #
                  {index + 1}
                  {' '}
                  {parent.inject?.inject_title}
                </Typography>
                <Tooltip title={t('Delete')}>
                  <IconButton
                    color="error"
                    onClick={() => {
                      deleteParent(parent);
                    }}
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </div>
            </AccordionSummary>
            <AccordionDetails>
              <FormControl style={{ width: '100%' }}>
                <InputLabel id="inject_id">{t('Inject')}</InputLabel>
                <Select
                  labelId="condition"
                  fullWidth={true}
                  value={parents[parent.index].inject ? parents[parent.index].inject?.inject_id : ''}
                  onChange={handleChangeParent}
                >
                  {injects?.filter(currentInject => currentInject.inject_depends_duration < values.inject_depends_duration
                    && (parents.find(parentSearch => currentInject.inject_id === parentSearch.inject?.inject_id) === undefined
                      || parents[parent.index].inject?.inject_id === currentInject.inject_id))
                    .map((currentInject) => {
                      return (
                        <MenuItem
                          key={`select-parent-${index}-inject-${currentInject.inject_id}`}
                          value={currentInject.inject_id}
                        >
                          {currentInject.inject_title}
                        </MenuItem>
                      );
                    })}
                </Select>
              </FormControl>
              <FormControl style={{
                width: '100%',
                marginTop: '15px',
              }}
              >
                <label className={classes.labelExecutionCondition}>{t('Execution condition:')}</label>
                <Box
                  sx={{
                    padding: '12px 4px',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 1,
                  }}
                >
                  {getClickableParentChip(parent)}
                </Box>
                <div style={{ justifyContent: 'left' }}>
                  <Button
                    color="secondary"
                    aria-label="Add"
                    size="large"
                    onClick={() => {
                      addConditionParent(parent);
                    }}
                    style={{ justifyContent: 'start' }}
                    disabled={!canAddConditions(parent.inject!, parentConditions.find(parentCondition => parentCondition.parentId === parent.inject?.inject_id))}
                  >
                    <Add fontSize="small" />
                    <Typography>
                      {t('Add condition')}
                    </Typography>
                  </Button>
                </div>
              </FormControl>
            </AccordionDetails>
          </Accordion>
        );
      })}

      <div className={classes.importerStyle}>
        <Typography variant="h2" sx={{ m: 0 }}>
          {t('Children')}
        </Typography>
        <IconButton
          color="secondary"
          aria-label="Add"
          size="large"
          disabled={addChildrenButtonDisabled || isDisabled}
          onClick={addChildren}
        >
          <Add fontSize="small" />
        </IconButton>
      </div>
      {childrens.map((children, index) => {
        return (
          <Accordion
            key={`accordion-children-${children.index}`}
            variant="outlined"
            style={{
              width: '100%',
              marginBottom: '10px',
            }}
            disabled={isDisabled}
          >
            <AccordionSummary
              expandIcon={<ExpandMore />}
            >
              <div className={classes.container}>
                <Typography>
                  #
                  {index + 1}
                  {children.inject?.inject_title}
                </Typography>
                <Tooltip title={t('Delete')}>
                  <IconButton
                    color="error"
                    onClick={() => {
                      deleteChildren(children);
                    }}
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </div>
            </AccordionSummary>
            <AccordionDetails>
              <FormControl style={{ width: '100%' }}>
                <InputLabel id="inject_id">{t('Inject')}</InputLabel>
                <Select
                  labelId="condition"
                  fullWidth={true}
                  value={childrens.find(childrenSearch => children.index === childrenSearch.index)?.inject
                    ? childrens.find(childrenSearch => children.index === childrenSearch.index)?.inject?.inject_id : ''}
                  onChange={handleChangeChildren}
                >
                  {injects?.filter(currentInject => currentInject.inject_depends_duration > values.inject_depends_duration
                    && (childrens.find(childrenSearch => currentInject.inject_id === childrenSearch.inject?.inject_id) === undefined
                      || childrens.find(childrenSearch => children.index === childrenSearch.index)?.inject?.inject_id === currentInject.inject_id))
                    .map((currentInject) => {
                      return (
                        <MenuItem
                          key={`select-children-${children.index}-inject-${currentInject.inject_id}`}
                          value={currentInject.inject_id}
                        >
                          {currentInject.inject_title}
                        </MenuItem>
                      );
                    })}
                </Select>
              </FormControl>
              <FormControl style={{
                width: '100%',
                marginTop: '15px',
              }}
              >
                <label className={classes.labelExecutionCondition}>{t('Execution condition:')}</label>

                <Box
                  sx={{
                    padding: '12px 4px',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 1,
                  }}
                >
                  {getClickableChildrenChip(children)}
                </Box>
                <div style={{ justifyContent: 'left' }}>
                  <Button
                    color="secondary"
                    aria-label="Add"
                    size="large"
                    onClick={() => {
                      addConditionChildren(children);
                    }}
                    disabled={!canAddConditions(
                      values as InjectOutput as InjectOutputType,
                      childrenConditions.find(childrenCondition => childrenCondition.childrenId === children.inject?.inject_id),
                    )}
                    style={{ justifyContent: 'start' }}
                  >
                    <Add fontSize="small" />
                    <Typography>
                      {t('Add condition')}
                    </Typography>
                  </Button>
                </div>
              </FormControl>
            </AccordionDetails>
          </Accordion>
        );
      })}
    </>
  );
};

export default InjectForm;
