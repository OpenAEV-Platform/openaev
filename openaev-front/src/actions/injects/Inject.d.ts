import { type Inject, type InjectorContract, type InjectOutput } from '../../utils/api-types';
import { type InjectorContractConverted } from '../../utils/api-types-custom';

export type InjectStore = Omit<Inject, 'inject_content' | 'inject_injector_contract'> & {
  inject_content?: {
    expectationScore: number;
    challenges: string[] | undefined;
  };
  inject_injector_contract: Omit<InjectorContract, 'convertedContent'> & { convertedContent: InjectorContractConverted['convertedContent'] };
};

export type InjectOutputType = InjectOutput & { inject_injector_contract: { convertedContent: InjectorContractConverted['convertedContent'] } & Inject['inject_injector_contract'] };

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
  inject?: InjectOutputType;
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
