import { type InjectOutput } from '../../utils/api-types';

export interface ConditionElement {
  name: string;
  value: boolean;
  key: string;
  index: number;
}

export interface ConditionType {
  parentId?: string;
  childrenId?: string;
  mode?: string;
  conditionElement?: ConditionElement[];
}

export interface Dependency {
  inject?: InjectOutput;
  index: number;
}

export interface Content {
  expectations: {
    expectation_type: string;
    expectation_name: string;
  }[];
}

export interface ConvertedContentType {
  fields: {
    key: string;
    value: string;
    predefinedExpectations: {
      expectation_type: string;
      expectation_name: string;
    }[];
  }[];
}
