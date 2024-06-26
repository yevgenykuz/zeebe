/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {Modal as BaseModal} from '@carbon/react';
import {CarbonTheme} from 'modules/theme/CarbonTheme';

type Props = React.ComponentProps<typeof BaseModal>;

const Modal: React.FC<Props> = ({children, ...props}) => {
  return createPortal(
    <CarbonTheme>
      <BaseModal {...props}>{children}</BaseModal>
    </CarbonTheme>,
    document.body,
  );
};

export {Modal};
