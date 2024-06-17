/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.process;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class SuspendedInstancesOnlyFilterIT extends AbstractFilterIT {

  @Test
  public void suspendedInstancesOnlyFilter() {
    // given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst =
        engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst =
        engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());

    engineDatabaseExtension.changeProcessInstanceState(firstProcInst.getId(), SUSPENDED_STATE);
    engineDatabaseExtension.changeProcessInstanceState(secondProcInst.getId(), SUSPENDED_STATE);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().suspendedInstancesOnly().add().buildList());
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
        reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(2);
    List<String> resultProcDefIds =
        result.getData().stream()
            .map(RawDataProcessInstanceDto::getProcessInstanceId)
            .collect(Collectors.toList());

    assertThat(resultProcDefIds).contains(firstProcInst.getId());
    assertThat(resultProcDefIds).contains(secondProcInst.getId());
  }
}
