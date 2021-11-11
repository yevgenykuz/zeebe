/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mergePathname} from './mergePathname';

let responseInterceptor: null | ((response: Response) => Promise<void>) = null;

async function request({url, method, body, headers, signal}: any) {
  const response = await fetch(
    mergePathname(window.clientConfig?.contextPath ?? '/', url),
    {
      method,
      credentials: 'include',
      body: typeof body === 'string' ? body : JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      mode: 'cors',
      signal,
    }
  );

  if (typeof responseInterceptor === 'function') {
    await responseInterceptor(response);
  }

  return response;
}

function setResponseInterceptor(fct: typeof responseInterceptor) {
  responseInterceptor = fct;
}

export {request, setResponseInterceptor};
