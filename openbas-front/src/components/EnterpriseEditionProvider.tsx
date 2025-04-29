/*
Copyright (c) 2021-2024 Filigran SAS
This file is part of the OpenBAS Enterprise Edition ("EE") and is
licensed under the OpenBAS Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
https://github.com/OpenBAS-Platform/openbas/blob/master/LICENSE
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

import { type ReactNode, useCallback, useState } from 'react';

import EnterpriseEditionContext from './EnterpriseEditionContext';

const EnterpriseEditionProvider = ({ children }: { children: ReactNode }) => {
  const [open, setOpen] = useState(false);
  const [featureDetectedInfo, setFeatureDetectedInfo] = useState('');

  const openDialog = useCallback(() => setOpen(true), []);
  const closeDialog = useCallback(() => setOpen(false), []);

  return (
    <EnterpriseEditionContext.Provider
      value={{
        open,
        openDialog,
        closeDialog,
        featureDetectedInfo,
        setFeatureDetectedInfo,
      }}
    >
      {children}
    </EnterpriseEditionContext.Provider>
  );
};

export default EnterpriseEditionProvider;
