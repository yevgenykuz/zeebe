/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.result;

import io.camunda.service.search.result.ProcessInstanceQueryResultConfig.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class QueryResultConfigBuilders {

  private QueryResultConfigBuilders() {}

  public static ProcessInstanceQueryResultConfig.Builder processInstance() {
    return new ProcessInstanceQueryResultConfig.Builder();
  }

  public static ProcessInstanceQueryResultConfig processInstance(
      final Function<Builder, ObjectBuilder<ProcessInstanceQueryResultConfig>> fn) {
    return fn.apply(processInstance()).build();
  }
}