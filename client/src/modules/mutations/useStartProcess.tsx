/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMutation} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {Process, ProcessInstance} from 'modules/types';

function useStartProcess() {
  return useMutation<
    ProcessInstance,
    RequestError | Error,
    Pick<Process, 'bpmnProcessId'>
  >({
    mutationFn: async ({bpmnProcessId}) => {
      const {response, error} = await request(api.startProcess(bpmnProcessId));

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not start process');
    },
  });
}

export {useStartProcess};
