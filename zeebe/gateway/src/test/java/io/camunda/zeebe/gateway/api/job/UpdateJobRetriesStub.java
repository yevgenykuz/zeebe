/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.job;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobRetriesRequest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;

public final class UpdateJobRetriesStub extends JobRequestStub
    implements RequestStub<BrokerUpdateJobRetriesRequest, BrokerResponse<JobRecord>> {

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerUpdateJobRetriesRequest.class, this);
  }

  @Override
  public BrokerResponse<JobRecord> handle(final BrokerUpdateJobRetriesRequest request)
      throws Exception {
    final JobRecord value = buildDefaultValue();
    value.setRetries(request.getRequestWriter().getRetries());

    return new BrokerResponse<>(value, 0, request.getKey());
  }
}
