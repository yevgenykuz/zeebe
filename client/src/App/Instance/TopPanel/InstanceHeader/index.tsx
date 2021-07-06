/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {formatDate} from 'modules/utils/date';
import {getProcessName} from 'modules/utils/instance';
import {Operations} from 'modules/components/Operations';
import Skeleton from './Skeleton';
import {observer} from 'mobx-react';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';

import * as Styled from './styled';
import {variablesStore} from 'modules/stores/variables';
import {useNotifications} from 'modules/notifications';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';

const InstanceHeader = observer(() => {
  const {instance} = currentInstanceStore.state;
  const notifications = useNotifications();

  if (
    instance === null ||
    !singleInstanceDiagramStore.areDiagramDefinitionsAvailable
  ) {
    return <Skeleton />;
  }

  const {id, processVersion, startDate, endDate, parentInstanceId, state} =
    instance;

  return (
    <>
      <Styled.StateIconWrapper>
        <Styled.StateIcon state={state} />
      </Styled.StateIconWrapper>

      <Styled.Table>
        <thead>
          <tr>
            <Styled.Th>Process</Styled.Th>
            <Styled.Th>Instance Id</Styled.Th>
            <Styled.Th>Version</Styled.Th>
            <Styled.Th>Start Date</Styled.Th>
            <Styled.Th>End Date</Styled.Th>
            <Styled.Th>Parent Instance Id</Styled.Th>
            <Styled.Th>Called Instances</Styled.Th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <Styled.Td>{getProcessName(instance)}</Styled.Td>
            <Styled.Td>{id}</Styled.Td>
            <Styled.Td>{processVersion}</Styled.Td>
            <Styled.Td data-testid="start-date">
              {formatDate(startDate)}
            </Styled.Td>
            <Styled.Td data-testid="end-date">{formatDate(endDate)}</Styled.Td>
            <Styled.Td>
              {parentInstanceId !== null ? (
                <Link
                  to={(location) =>
                    Locations.instance(parentInstanceId, location)
                  }
                  title={`View parent instance ${parentInstanceId}`}
                >
                  {parentInstanceId}
                </Link>
              ) : (
                'None'
              )}
            </Styled.Td>
            <Styled.Td>
              {singleInstanceDiagramStore.hasCalledInstances ? (
                <Link
                  to={(location) =>
                    Locations.filters(location, {
                      parentInstanceId: id,
                      active: true,
                      incidents: true,
                      canceled: true,
                      completed: true,
                    })
                  }
                  title={`View all called instances`}
                >
                  View All
                </Link>
              ) : (
                'None'
              )}
            </Styled.Td>
          </tr>
        </tbody>
      </Styled.Table>
      <Operations
        instance={instance}
        onOperation={() => currentInstanceStore.activateOperation()}
        onFailure={() => {
          currentInstanceStore.deactivateOperation();
          notifications.displayNotification('error', {
            headline: 'Operation could not be created',
          });
        }}
        forceSpinner={
          variablesStore.hasActiveOperation || instance?.hasActiveOperation
        }
      />
    </>
  );
});

export {InstanceHeader};
