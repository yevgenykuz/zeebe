/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.candidategroup.distributedby.usertask;

import static io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_CANDIDATE_BY_USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import io.camunda.optimize.service.db.es.report.util.MapResultUtil;
import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import java.util.List;

public class UserTaskTotalDurationByCandidateGroupByUserTaskReportEvaluationIT
    extends AbstractUserTaskDurationByCandidateGroupByUserTaskReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.TOTAL;
  }

  @Override
  protected void changeDuration(
      final ProcessInstanceEngineDto processInstanceDto,
      final String userTaskKey,
      final Double durationInMs) {
    changeUserTaskTotalDuration(processInstanceDto, userTaskKey, durationInMs);
  }

  @Override
  protected void changeDuration(
      final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
    changeUserTaskTotalDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected ProcessReportDataDto createReport(
      final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder.createReportData()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersions(versions)
        .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
        .setReportDataType(USER_TASK_DUR_GROUP_BY_CANDIDATE_BY_USER_TASK)
        .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilters(
      final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
      final FlowNodeStatusTestValues expectedValues) {
    assertThat(
            MapResultUtil.getDataEntryForKey(
                result.getFirstMeasureData(), FIRST_CANDIDATE_GROUP_ID))
        .isPresent()
        .get()
        .isEqualTo(expectedValues.getExpectedTotalDurationValues());
  }
}
