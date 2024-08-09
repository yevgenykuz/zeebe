/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Data;

@Data
public class DeploymentEngineDto implements Serializable, TenantSpecificEngineDto {

  private String id;
  private String name;
  private String source;
  private OffsetDateTime deploymentTime;
  private String tenantId;

  @Override
  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}