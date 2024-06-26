/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import org.agrona.DirectBuffer;

public final class BrokerResolveIncidentRequest extends BrokerExecuteCommand<IncidentRecord> {

  private final IncidentRecord requestDto = new IncidentRecord();

  public BrokerResolveIncidentRequest(final long incidentKey) {
    super(ValueType.INCIDENT, IncidentIntent.RESOLVE);
    request.setKey(incidentKey);
  }

  @Override
  public IncidentRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected IncidentRecord toResponseDto(final DirectBuffer buffer) {
    final IncidentRecord responseDto = new IncidentRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
