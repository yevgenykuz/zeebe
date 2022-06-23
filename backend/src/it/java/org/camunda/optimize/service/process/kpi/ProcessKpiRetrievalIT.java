/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.kpi;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiType;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessKpiRetrievalIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefKey";

  @Test
  public void getKpisForDefinition() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(true, "2", true, PROCESS_DEFINITION_KEY);
    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto1.setUnit("");

    KpiResponseDto kpiResponseDto2 = new KpiResponseDto();
    kpiResponseDto2.setReportId(reportId2);
    kpiResponseDto2.setReportName("My test report");
    kpiResponseDto2.setTarget("2");
    kpiResponseDto2.setValue("1.0");
    kpiResponseDto2.setIsBelow(true);
    kpiResponseDto2.setType(KpiType.QUALITY);
    kpiResponseDto2.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto2.setUnit("");

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          List.of(kpiResponseDto1, kpiResponseDto2)
        )
      );
  }

  @Test
  public void getKpiWithDateFilterForDefinition() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget("1");
    final RollingDateFilterDataDto dateFilterDataDto = new RollingDateFilterDataDto(
      new RollingDateFilterStartDto(4L, DateUnit.DAYS)
    );
    final InstanceStartDateFilterDto startDateFilterDto = new InstanceStartDateFilterDto();
    startDateFilterDto.setData(dateFilterDataDto);
    startDateFilterDto.setFilterLevel(FilterApplicationLevel.INSTANCE);
    reportDataDto.setFilter(Collections.singletonList(startDateFilterDto));
    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto1.setUnit("");

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          List.of(kpiResponseDto1)
        )
      );
  }

  @Test
  public void reportIsNotReturnedIfNotKpi() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(true, "2", false, PROCESS_DEFINITION_KEY);
    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto1.setUnit("");

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          List.of(kpiResponseDto1)
        )
      );
  }

  @Test
  public void otherProcessDefinitionKpiReportIsNotReturned() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram("somedefinition"));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(true, "2", true, PROCESS_DEFINITION_KEY);
    String reportId3 = createKpiReport(true, "1", true, "somedefinition");
    String reportId4 = createKpiReport(true, "2", true, "somedefinition");

    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto1.setUnit("");

    KpiResponseDto kpiResponseDto2 = new KpiResponseDto();
    kpiResponseDto2.setReportId(reportId2);
    kpiResponseDto2.setReportName("My test report");
    kpiResponseDto2.setTarget("2");
    kpiResponseDto2.setValue("1.0");
    kpiResponseDto2.setIsBelow(true);
    kpiResponseDto2.setType(KpiType.QUALITY);
    kpiResponseDto2.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto2.setUnit("");

    KpiResponseDto kpiResponseDto3 = new KpiResponseDto();
    kpiResponseDto3.setReportId(reportId3);
    kpiResponseDto3.setReportName("My test report");
    kpiResponseDto3.setValue("1.0");
    kpiResponseDto3.setTarget("1");
    kpiResponseDto3.setIsBelow(true);
    kpiResponseDto3.setType(KpiType.QUALITY);
    kpiResponseDto3.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto3.setUnit("");

    KpiResponseDto kpiResponseDto4 = new KpiResponseDto();
    kpiResponseDto4.setReportId(reportId4);
    kpiResponseDto4.setReportName("My test report");
    kpiResponseDto4.setTarget("2");
    kpiResponseDto4.setValue("1.0");
    kpiResponseDto4.setIsBelow(true);
    kpiResponseDto4.setType(KpiType.QUALITY);
    kpiResponseDto4.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto4.setUnit("");

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(2)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .map(tuple2 -> Tuple.tuple(tuple2.toList().get(0), Set.copyOf((List)tuple2.toList().get(1))))
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          Set.of(kpiResponseDto1, kpiResponseDto2)
        ),
        Tuple.tuple(
          "somedefinition",
          Set.of(kpiResponseDto3, kpiResponseDto4)
        )
      );
  }

  @Test
  public void kpiTypeGetsAssignedCorrectly() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setFilterLevel(FilterApplicationLevel.INSTANCE);
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReportWithMeasures(true, "2", true, PROCESS_DEFINITION_KEY, ViewProperty.DURATION);
    String reportId3 = createKpiReportWithMeasures(true, "3", true, PROCESS_DEFINITION_KEY, ViewProperty.FREQUENCY);
    String reportId4 = createKpiReportWithMeasures(true, "4", true, PROCESS_DEFINITION_KEY, ViewProperty.PERCENTAGE);

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1);
    assertThat(processes.get(0).getKpis()).extracting(KpiResponseDto::getReportId, KpiResponseDto::getType)
      .containsExactlyInAnyOrder(
        Tuple.tuple(reportId1, KpiType.QUALITY),
        Tuple.tuple(reportId2, KpiType.TIME),
        Tuple.tuple(reportId3, KpiType.QUALITY),
        Tuple.tuple(reportId4, KpiType.QUALITY)
      );
  }

  @Test
  public void kpiUnitGetsReturned() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setFilterLevel(FilterApplicationLevel.INSTANCE);
    String reportId1 = createKpiReportWithDurationProgress(true, "1", true, PROCESS_DEFINITION_KEY);

    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("0.0");
    kpiResponseDto1.setTarget("1.0");
    kpiResponseDto1.setIsBelow(false);
    kpiResponseDto1.setType(KpiType.TIME);
    kpiResponseDto1.setMeasure(ViewProperty.DURATION);
    kpiResponseDto1.setUnit("DAYS");

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          List.of(kpiResponseDto1)
        )
      );
  }

  @Test
  public void kpiReportsGetRetrievedWithGroupBy() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);

    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget("2");

    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);
    kpiResponseDto1.setUnit("");

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          List.of(kpiResponseDto1)
        )
      );
  }

  private String createKpiReport(final Boolean isBelow, final String target, final Boolean isKpi,
                                 final String definitionKey) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(definitionKey)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(isKpi);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(isBelow);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget(target);
    return reportClient.createSingleProcessReport(reportDataDto);
  }

  private String createKpiReportWithDurationProgress(final Boolean isBelow, final String target, final Boolean isKpi, final String definitionKey) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(definitionKey)))
      .build();
    TargetDto targetDto = new TargetDto();
    targetDto.setValue("1.0");
    targetDto.setUnit(TargetValueUnit.DAYS);
    reportDataDto.getView().setProperties(ViewProperty.DURATION);
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(isKpi);
    reportDataDto.getConfiguration().getTargetValue().getDurationProgress().setTarget(targetDto);
    return reportClient.createSingleProcessReport(reportDataDto);
  }

  private String createKpiReportWithMeasures(final Boolean isBelow, final String target, final Boolean isKpi,
                                             final String definitionKey, final ViewProperty viewProperty) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(definitionKey)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(isKpi);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(isBelow);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget(target);
    reportDataDto.getView().setProperties(List.of(viewProperty));
    return reportClient.createSingleProcessReport(reportDataDto);
  }

}
