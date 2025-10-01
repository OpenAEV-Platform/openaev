/*
Copyright (c) 2021-2024 Filigran SAS

This file is part of the OpenAEV Enterprise Edition ("EE") and is
licensed under the OpenAEV Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://github.com/OpenAEV-Platform/openaev/blob/master/LICENSE

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

import { useContext } from 'react';

import EnterpriseEditionContext from '../../components/EnterpriseEditionContext';
import useAuth from './useAuth';

const useEnterpriseEdition = () => {
  const { settings } = useAuth();
  const context = useContext(EnterpriseEditionContext);

  return {
    ...context,
    isValidated: settings.platform_license?.license_is_validated === true,
  };
};

export default useEnterpriseEdition;
