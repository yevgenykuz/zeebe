/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.qa.util.RestAPITestUtil.*;
import static io.camunda.operate.util.TestUtil.createFlowNodeInstance;
import static io.camunda.operate.util.TestUtil.createFlowNodeInstanceWithIncident;
import static io.camunda.operate.util.TestUtil.createIncident;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.operate.util.TestUtil.createVariableForListView;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.SortingDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.listview.VariablesQueryDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Tests Elasticsearch queries for process instances. */
public class ListViewQueryIT extends OperateAbstractIT {

  private static final String QUERY_INSTANCES_URL = PROCESS_INSTANCE_URL;
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @MockBean private PermissionsService permissionsService;
  private ProcessInstanceForListViewEntity instanceWithoutIncident;
  private ProcessInstanceForListViewEntity runningInstance;
  private ProcessInstanceForListViewEntity completedInstance;
  private ProcessInstanceForListViewEntity canceledInstance;

  private String batchOperationId = "batchOperationId";
  private Long parentInstanceKey1 = 111L;
  private Long parentInstanceKey2 = 222L;
  private String rootInstanceId = "333";

  private String tenant1 = "tenant1";
  private String tenant2 = "tenant2";

  @Test
  public void testVariousQueries() throws Exception {
    createData();

    testQueryAllRunning();
    testQueryByVariableValue();
    testQueryByVariableInValues();
    testQueryByVariableValueNotExists();
    testQueryByVariableInValuesNotExists();
    testQueryByBatchOperationId();
    testQueryByParentProcessId();

    testQueryAllFinished();
    testQueryFinishedAndRunning();
    testQueryFinishedCompleted();
    testQueryFinishedCanceled();
    testQueryRetriesLeft();
    testQueryRunningWithIncidents();
    testQueryRunningWithoutIncidents();
    testParamsAreEmptyStringsInsteadOfNull();
    testPagination();
    testQueryByNonExistingParentProcessId();

    testQueryByTenantId();
  }

  private void testQueryByTenantId() throws Exception {
    ListViewRequestDto query = createGetAllProcessInstancesRequest(q -> q.setTenantId(tenant1));

    // when
    MvcResult mvcResult = postRequest(query(), query);

    ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(3);
    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.TENANT_ID)
        .containsOnly(tenant1);

    query = createGetAllProcessInstancesRequest(q -> q.setTenantId(tenant2));

    // when
    mvcResult = postRequest(query(), query);

    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(2);
    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.TENANT_ID)
        .containsOnly(tenant2);
  }

  private void testQueryAllRunning() throws Exception {
    // query running instances
    final ListViewRequestDto processInstanceQueryDto = createGetAllRunningRequest();

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getProcessInstances().size()).isEqualTo(6);
    assertThat(response.getTotalCount()).isEqualTo(6);
    for (final ListViewProcessInstanceDto processInstanceDto : response.getProcessInstances()) {
      assertThat(processInstanceDto.getEndDate()).isNull();
      assertThat(processInstanceDto.getState())
          .isIn(ProcessInstanceStateDto.ACTIVE, ProcessInstanceStateDto.INCIDENT);
    }
  }

  @Test
  public void testQueryByStartAndEndDate() throws Exception {
    // given
    final OffsetDateTime date1 =
        OffsetDateTime.of(
            2018, 1, 1, 15, 30, 30, 156, OffsetDateTime.now().getOffset()); // January 1, 2018
    final OffsetDateTime date2 =
        OffsetDateTime.of(
            2018, 2, 1, 12, 0, 30, 457, OffsetDateTime.now().getOffset()); // February 1, 2018
    final OffsetDateTime date3 =
        OffsetDateTime.of(
            2018, 3, 1, 17, 15, 14, 235, OffsetDateTime.now().getOffset()); // March 1, 2018
    final OffsetDateTime date4 =
        OffsetDateTime.of(
            2018, 4, 1, 2, 12, 0, 0, OffsetDateTime.now().getOffset()); // April 1, 2018
    final OffsetDateTime date5 =
        OffsetDateTime.of(
            2018, 5, 1, 23, 30, 15, 666, OffsetDateTime.now().getOffset()); // May 1, 2018
    final ProcessInstanceForListViewEntity processInstance1 = createProcessInstance(date1, date5);
    final ProcessInstanceForListViewEntity processInstance2 = createProcessInstance(date2, date4);
    final ProcessInstanceForListViewEntity processInstance3 = createProcessInstance(date3, null);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    // when
    ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setStartDateAfter(date1.minus(1, ChronoUnit.DAYS));
              q.setStartDateBefore(date3);
            });
    // then
    requestAndAssertIds(query, "TEST CASE #1", processInstance1.getId(), processInstance2.getId());

    // test inclusion for startDateAfter and exclusion for startDateBefore
    // when
    query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setStartDateAfter(date1);
              q.setStartDateBefore(date3);
            });
    // then
    requestAndAssertIds(query, "TEST CASE #2", processInstance1.getId(), processInstance2.getId());

    // when
    query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setStartDateAfter(date1.plus(1, ChronoUnit.MILLIS));
              q.setStartDateBefore(date3.plus(1, ChronoUnit.MILLIS));
            });
    // then
    requestAndAssertIds(query, "TEST CASE #3", processInstance2.getId(), processInstance3.getId());

    // test combination of start date and end date
    // when
    query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setStartDateAfter(date2.minus(1, ChronoUnit.DAYS));
              q.setStartDateBefore(date3.plus(1, ChronoUnit.DAYS));
              q.setEndDateAfter(date4.minus(1, ChronoUnit.DAYS));
              q.setEndDateBefore(date4.plus(1, ChronoUnit.DAYS));
            });
    // then
    requestAndAssertIds(query, "TEST CASE #4", processInstance2.getId());

    // test inclusion for endDateAfter and exclusion for endDateBefore
    // when
    query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setEndDateAfter(date4);
              q.setEndDateBefore(date5);
            });
    // then
    requestAndAssertIds(query, "TEST CASE #5", processInstance2.getId());

    // when
    query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setEndDateAfter(date4);
              q.setEndDateBefore(date5.plus(1, ChronoUnit.MILLIS));
            });
    // then
    requestAndAssertIds(query, "TEST CASE #6", processInstance1.getId(), processInstance2.getId());
  }

  private void requestAndAssertIds(ListViewRequestDto query, String testCaseName, String... ids)
      throws Exception {
    // then
    final MvcResult mvcResult = postRequest(query(), query);
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getProcessInstances())
        .as(testCaseName)
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(ids);
  }

  @Test
  public void testQueryByErrorMessage() throws Exception {
    final String errorMessage = "No more retries left.";

    // given we have 2 process instances: one with active activity with given error msg, another
    // with active activity with another error message
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE, true, null);
    final FlowNodeInstanceForListViewEntity activityInstance1 =
        createFlowNodeInstanceWithIncident(
            processInstance1.getProcessInstanceKey(), FlowNodeState.ACTIVE, errorMessage);

    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE, true, null);
    final FlowNodeInstanceForListViewEntity activityInstance2 =
        createFlowNodeInstanceWithIncident(
            processInstance2.getProcessInstanceKey(), FlowNodeState.ACTIVE, "other error message");

    searchTestRule.persistNew(
        processInstance1, activityInstance1, processInstance2, activityInstance2);

    // given
    final ListViewRequestDto query =
        new ListViewRequestDto(
            createGetAllProcessInstancesQuery(q -> q.setErrorMessage(errorMessage)));
    // when
    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(1);

    final ListViewProcessInstanceDto processInstance = response.getProcessInstances().get(0);
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceStateDto.INCIDENT);
    assertThat(processInstance.getId()).isEqualTo(processInstance1.getId());
  }

  private void testQueryByVariableValue() throws Exception {
    // given
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(q -> q.setVariable(new VariablesQueryDto("var1", "X")));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(3);
    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(
            runningInstance.getId(), completedInstance.getId(), canceledInstance.getId());
  }

  private void testQueryByVariableInValues() throws Exception {
    // given
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q -> q.setVariable(new VariablesQueryDto("var2", new String[] {"Y", "Z", "A"})));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(2);
    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(runningInstance.getId(), completedInstance.getId());
  }

  private void testQueryByVariableValueNotExists() throws Exception {
    // given
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(q -> q.setVariable(new VariablesQueryDto("var1", "A")));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(0);
  }

  private void testQueryByVariableInValuesNotExists() throws Exception {
    // given
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q -> q.setVariable(new VariablesQueryDto("var1", new String[] {"A", "B"})));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(0);
  }

  @Test
  public void testQueryByActiveActivityId() throws Exception {

    final String activityId = "taskA";

    final OperateEntity[] data = createDataForActiveActivityIdQuery(activityId);
    searchTestRule.persistNew(data);

    // when
    final ListViewRequestDto query =
        createProcessInstanceRequest(
            q -> {
              q.setRunning(true).setActive(true).setActivityId(activityId);
            });

    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(1);

    assertThat(response.getProcessInstances().get(0).getId()).isEqualTo(data[0].getId());
  }

  /** 1st entity must be selected */
  private OperateEntity[] createDataForActiveActivityIdQuery(String activityId) {
    final List<OperateEntity> entities = new ArrayList<>();
    final List<OperateEntity> activityInstances = new ArrayList<>();

    // pi 1: active with active activity with given id
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE);

    final FlowNodeInstanceForListViewEntity activeWithIdActivityInstance =
        createFlowNodeInstance(
            processInstance1.getProcessInstanceKey(), FlowNodeState.ACTIVE, activityId);

    final FlowNodeInstanceForListViewEntity completedWithoutIdActivityInstance =
        createFlowNodeInstance(
            processInstance1.getProcessInstanceKey(), FlowNodeState.COMPLETED, "otherActivityId");

    entities.add(processInstance1);
    activityInstances.addAll(
        Arrays.asList(activeWithIdActivityInstance, completedWithoutIdActivityInstance));

    // pi 2: active with active activity with another id and incident activity with given id
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE, true, null);

    final FlowNodeInstanceForListViewEntity activeWithoutIdActivityInstance =
        createFlowNodeInstance(
            processInstance2.getProcessInstanceKey(), FlowNodeState.ACTIVE, "otherActivityId");

    final FlowNodeInstanceForListViewEntity incidentWithIdActivityInstance =
        createFlowNodeInstanceWithIncident(
            processInstance2.getProcessInstanceKey(), FlowNodeState.ACTIVE, "error");
    incidentWithIdActivityInstance.setActivityId(activityId);

    entities.add(processInstance2);
    activityInstances.addAll(
        Arrays.asList(activeWithoutIdActivityInstance, incidentWithIdActivityInstance));

    entities.addAll(activityInstances);

    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByIncidentActivityId() throws Exception {
    final String activityId = "taskA";

    final OperateEntity[] data = createDataForIncidentActivityIdQuery(activityId);
    searchTestRule.persistNew(data);

    // when
    final ListViewRequestDto query =
        createProcessInstanceRequest(
            q -> {
              q.setRunning(true).setIncidents(true).setActivityId(activityId);
            });

    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(1);

    assertThat(response.getProcessInstances().get(0).getId()).isEqualTo(data[0].getId());
  }

  /** 1st entity must be selected */
  private OperateEntity[] createDataForIncidentActivityIdQuery(String activityId) {
    final List<OperateEntity> entities = new ArrayList<>();
    final List<OperateEntity> activityInstances = new ArrayList<>();

    // wi1: active with activity in INCIDENT state with given id
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE, true, null);

    final FlowNodeInstanceForListViewEntity incidentWithIdActivityInstance =
        createFlowNodeInstanceWithIncident(
            processInstance1.getProcessInstanceKey(), FlowNodeState.ACTIVE, "error");
    incidentWithIdActivityInstance.setActivityId(activityId);

    final FlowNodeInstanceForListViewEntity completedWithoutIdActivityInstance =
        createFlowNodeInstance(
            processInstance1.getProcessInstanceKey(), FlowNodeState.COMPLETED, "otherActivityId");

    entities.add(processInstance1);
    activityInstances.addAll(
        Arrays.asList(incidentWithIdActivityInstance, completedWithoutIdActivityInstance));

    // wi2: active with activity in INCIDENT state with another id
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE, true, null);

    final FlowNodeInstanceForListViewEntity incidentWithoutIdActivityInstance =
        createFlowNodeInstanceWithIncident(
            processInstance2.getProcessInstanceKey(), FlowNodeState.ACTIVE, "error");
    incidentWithoutIdActivityInstance.setActivityId("otherActivityId");

    final FlowNodeInstanceForListViewEntity completedWithIdActivityInstance =
        createFlowNodeInstance(
            processInstance2.getProcessInstanceKey(), FlowNodeState.COMPLETED, activityId);

    entities.add(processInstance2);
    activityInstances.addAll(
        Arrays.asList(incidentWithoutIdActivityInstance, completedWithIdActivityInstance));

    // wi3: active with activity in ACTIVE state with given id
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.ACTIVE);

    final FlowNodeInstanceForListViewEntity activeWithIdActivityInstance =
        createFlowNodeInstance(
            processInstance3.getProcessInstanceKey(), FlowNodeState.ACTIVE, activityId);

    final FlowNodeInstanceForListViewEntity completedWithoutIdActivityInstance2 =
        createFlowNodeInstance(
            processInstance3.getProcessInstanceKey(), FlowNodeState.COMPLETED, "otherActivityId");

    entities.add(processInstance3);
    activityInstances.addAll(
        Arrays.asList(activeWithIdActivityInstance, completedWithoutIdActivityInstance2));

    entities.addAll(activityInstances);

    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByTerminatedActivityId() throws Exception {
    final String activityId = "taskA";

    final OperateEntity[] data = createDataForTerminatedActivityIdQuery(activityId);
    searchTestRule.persistNew(data);

    // when
    final ListViewRequestDto query =
        createProcessInstanceRequest(
            q -> {
              q.setFinished(true).setCanceled(true).setActivityId(activityId);
            });

    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(1);

    assertThat(response.getProcessInstances().get(0).getId()).isEqualTo(data[0].getId());
  }

  /** 1st entity must be selected */
  private OperateEntity[] createDataForTerminatedActivityIdQuery(String activityId) {
    final List<OperateEntity> entities = new ArrayList<>();

    // wi1: canceled with TERMINATED activity with given id
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.CANCELED);

    final FlowNodeInstanceForListViewEntity terminatedWithIdActivityInstance =
        createFlowNodeInstance(
            processInstance1.getProcessInstanceKey(), FlowNodeState.TERMINATED, activityId);

    final FlowNodeInstanceForListViewEntity completedWithoutIdActivityInstance =
        createFlowNodeInstance(
            processInstance1.getProcessInstanceKey(), FlowNodeState.COMPLETED, "otherActivityId");

    entities.addAll(
        Arrays.asList(
            processInstance1,
            terminatedWithIdActivityInstance,
            completedWithoutIdActivityInstance));

    // wi2: canceled with TERMINATED activity with another id
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED);

    final FlowNodeInstanceForListViewEntity terminatedWithoutIdActivityInstance =
        createFlowNodeInstance(
            processInstance2.getProcessInstanceKey(), FlowNodeState.TERMINATED, "otherActivityId");

    final FlowNodeInstanceForListViewEntity completedWithIdActivityInstance =
        createFlowNodeInstance(
            processInstance2.getProcessInstanceKey(), FlowNodeState.COMPLETED, activityId);

    entities.addAll(
        Arrays.asList(
            processInstance2,
            terminatedWithoutIdActivityInstance,
            completedWithIdActivityInstance));

    // wi3: active with TERMINATED activity with given id
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.ACTIVE);

    final FlowNodeInstanceForListViewEntity activeWithIdActivityInstance =
        createFlowNodeInstance(
            processInstance3.getProcessInstanceKey(), FlowNodeState.TERMINATED, activityId);

    final FlowNodeInstanceForListViewEntity completedWithoutIdActivityInstance2 =
        createFlowNodeInstance(
            processInstance3.getProcessInstanceKey(), FlowNodeState.COMPLETED, "otherActivityId");

    entities.addAll(
        Arrays.asList(
            processInstance3, activeWithIdActivityInstance, completedWithoutIdActivityInstance2));

    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByCombinedStateActivityId() throws Exception {
    final String activityId = "taskA";

    final List<String> selectedIds = new ArrayList<>();

    OperateEntity[] data = createDataForActiveActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[1].getId());
    searchTestRule.persistNew(data);

    data = createDataForIncidentActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[2].getId());
    searchTestRule.persistNew(data);

    data = createDataForTerminatedActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[6].getId());
    searchTestRule.persistNew(data);

    // when
    final ListViewRequestDto query =
        createProcessInstanceRequest(
            q -> {
              q.setRunning(true)
                  .setIncidents(true)
                  .setActive(true)
                  .setFinished(true)
                  .setCanceled(true)
                  .setActivityId(activityId);
            });

    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(selectedIds.size());

    assertThat(response.getProcessInstances())
        .extracting("id")
        .containsExactlyInAnyOrder(selectedIds.toArray());
  }

  @Test
  public void testQueryByCompletedActivityId() throws Exception {
    final String activityId = "endEvent";

    // pi 1: completed with completed end event
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.COMPLETED);

    final FlowNodeInstanceForListViewEntity completedEndEventWithIdActivityInstance =
        TestUtil.createFlowNodeInstance(
            processInstance1.getProcessInstanceKey(),
            FlowNodeState.COMPLETED,
            activityId,
            FlowNodeType.END_EVENT);

    final FlowNodeInstanceForListViewEntity completedWithoutIdActivityInstance =
        createFlowNodeInstance(
            processInstance1.getProcessInstanceKey(), FlowNodeState.COMPLETED, "otherActivityId");

    // pi 2: completed without completed end event
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.COMPLETED);

    final FlowNodeInstanceForListViewEntity activeEndEventWithIdActivityInstance =
        TestUtil.createFlowNodeInstance(
            processInstance2.getProcessInstanceKey(),
            FlowNodeState.ACTIVE,
            activityId,
            FlowNodeType.END_EVENT);

    // pi 3: completed with completed end event (but not of type END_EVENT)
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED);

    final FlowNodeInstanceForListViewEntity completedWithIdActivityInstance =
        createFlowNodeInstance(
            processInstance3.getProcessInstanceKey(), FlowNodeState.COMPLETED, activityId);

    // pi 4: active with completed end event
    final ProcessInstanceForListViewEntity processInstance4 =
        createProcessInstance(ProcessInstanceState.ACTIVE);

    final FlowNodeInstanceForListViewEntity completedEndEventWithIdActivityInstance2 =
        TestUtil.createFlowNodeInstance(
            processInstance4.getProcessInstanceKey(),
            FlowNodeState.COMPLETED,
            activityId,
            FlowNodeType.END_EVENT);

    searchTestRule.persistNew(
        processInstance1,
        completedEndEventWithIdActivityInstance,
        completedWithoutIdActivityInstance,
        processInstance2,
        activeEndEventWithIdActivityInstance,
        processInstance3,
        completedWithIdActivityInstance,
        processInstance4,
        completedEndEventWithIdActivityInstance2);

    // when
    final ListViewRequestDto query =
        createProcessInstanceRequest(
            q -> {
              q.setFinished(true).setCompleted(true).setActivityId(activityId);
            });

    final MvcResult mvcResult = postRequest(query(), query);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(1);

    assertThat(response.getProcessInstances().get(0).getId()).isEqualTo(processInstance1.getId());
  }

  @Test
  public void testQueryByProcessInstanceIds() throws Exception {
    // given
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q -> q.setIds(Arrays.asList(processInstance1.getId(), processInstance2.getId())));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getProcessInstances()).hasSize(2);

    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
  }

  public void testQueryByBatchOperationId() throws Exception {
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(q -> q.setBatchOperationId(batchOperationId));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getProcessInstances()).hasSize(2);

    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(runningInstance.getId(), completedInstance.getId());
  }

  @Test
  public void testQueryByExcludeIds() throws Exception {
    // given
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED);
    final ProcessInstanceForListViewEntity processInstance4 =
        createProcessInstance(ProcessInstanceState.COMPLETED);
    searchTestRule.persistNew(
        processInstance1, processInstance2, processInstance3, processInstance4);

    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q ->
                q.setExcludeIds(Arrays.asList(processInstance1.getId(), processInstance3.getId())));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getProcessInstances()).hasSize(2);

    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(processInstance2.getId(), processInstance4.getId());
  }

  @Test
  public void testQueryByProcessDefinitionKeys() throws Exception {
    // given
    final Long wfKey1 = 1L, wfKey2 = 2L, wfKey3 = 3L;
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE);
    processInstance1.setProcessDefinitionKey(wfKey1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED);
    processInstance2.setProcessDefinitionKey(wfKey2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED);
    final ProcessInstanceForListViewEntity processInstance4 =
        createProcessInstance(ProcessInstanceState.COMPLETED);
    processInstance3.setProcessDefinitionKey(wfKey3);
    processInstance4.setProcessDefinitionKey(wfKey3);

    searchTestRule.persistNew(
        processInstance1, processInstance2, processInstance3, processInstance4);

    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q -> q.setProcessIds(Arrays.asList(wfKey1.toString(), wfKey3.toString())));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getProcessInstances()).hasSize(3);

    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(
            processInstance1.getId(), processInstance3.getId(), processInstance4.getId());
  }

  @Test
  public void testQueryByBpmnProcessIdAndVersion() throws Exception {
    // given
    final String bpmnProcessId1 = "pr1";
    final int version1 = 1;
    final String bpmnProcessId2 = "pr2";
    final int version2 = 2;
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE);
    processInstance1.setBpmnProcessId(bpmnProcessId1);
    processInstance1.setProcessVersion(version1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED);
    processInstance2.setBpmnProcessId(bpmnProcessId1);
    processInstance2.setProcessVersion(version2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED);
    processInstance3.setBpmnProcessId(bpmnProcessId2);
    processInstance3.setProcessVersion(version1);

    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setBpmnProcessId(bpmnProcessId1);
              q.setProcessVersion(version1);
            });

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getProcessInstances()).hasSize(1);

    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactly(processInstance1.getId());
  }

  private void testQueryByParentProcessId() throws Exception {
    // given
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(q -> q.setParentInstanceId(parentInstanceKey1));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getProcessInstances()).hasSize(2);

    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(runningInstance.getId(), completedInstance.getId());
    assertThat(response.getProcessInstances())
        .extracting("rootInstanceId")
        .containsExactly(rootInstanceId, rootInstanceId);
  }

  private void testQueryByNonExistingParentProcessId() throws Exception {
    // given
    final long nonExistingParentId = 333L;
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(q -> q.setParentInstanceId(nonExistingParentId));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response.getProcessInstances()).hasSize(0);
  }

  @Test
  public void testQueryByProcessVersionFail() throws Exception {
    // when
    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setProcessVersion(1);
            });
    // then
    final MvcResult mvcResult = postRequestThatShouldFail(query(), query);

    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("BpmnProcessId must be provided in request, when process version is not null");
  }

  @Test
  public void testQueryByBpmnProcessIdAllVersions() throws Exception {
    // given
    final String bpmnProcessId1 = "pr1";
    final int version1 = 1;
    final String bpmnProcessId2 = "pr2";
    final int version2 = 2;
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE);
    processInstance1.setBpmnProcessId(bpmnProcessId1);
    processInstance1.setProcessVersion(version1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED);
    processInstance2.setBpmnProcessId(bpmnProcessId1);
    processInstance2.setProcessVersion(version2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED);
    processInstance3.setBpmnProcessId(bpmnProcessId2);
    processInstance3.setProcessVersion(version1);

    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto query =
        createGetAllProcessInstancesRequest(q -> q.setBpmnProcessId(bpmnProcessId1));

    // when
    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getProcessInstances()).hasSize(2);

    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
  }

  private void testPagination() throws Exception {
    // query running instances
    final ListViewRequestDto processInstanceRequest = createGetAllProcessInstancesRequest();
    processInstanceRequest.setSorting(
        new SortingDto()
            .setSortBy(ListViewTemplate.END_DATE)
            .setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE));
    processInstanceRequest.setPageSize(5);

    // page 1
    MvcResult mvcResult = postRequest(query(), processInstanceRequest);
    final ListViewResponseDto page1Response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(page1Response.getProcessInstances().size()).isEqualTo(5);
    assertThat(page1Response.getTotalCount()).isEqualTo(8);

    // page 2
    processInstanceRequest.setSearchAfter(
        page1Response
            .getProcessInstances()
            .get(page1Response.getProcessInstances().size() - 1)
            .getSortValues());
    processInstanceRequest.setPageSize(3);
    mvcResult = postRequest(query(), processInstanceRequest);
    final ListViewResponseDto page2Response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(page2Response.getProcessInstances().size()).isEqualTo(3);
    assertThat(page2Response.getTotalCount()).isEqualTo(8);
    assertThat(page2Response.getProcessInstances())
        .doesNotContainAnyElementsOf(page1Response.getProcessInstances());

    // page 1 via searchBefore
    processInstanceRequest.setSearchAfter(null);
    processInstanceRequest.setSearchBefore(
        page2Response.getProcessInstances().get(0).getSortValues());
    processInstanceRequest.setPageSize(5);
    mvcResult = postRequest(query(), processInstanceRequest);
    final ListViewResponseDto page1Response2 =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});
    assertThat(page1Response2.getProcessInstances().size()).isEqualTo(5);
    assertThat(page1Response2.getTotalCount()).isEqualTo(8);
    assertThat(page1Response.getProcessInstances())
        .containsExactlyInAnyOrderElementsOf(page1Response2.getProcessInstances());
  }

  private void testSorting(
      SortingDto sorting,
      Comparator<ListViewProcessInstanceDto> comparator,
      String sortingDescription)
      throws Exception {

    // query running instances
    final ListViewRequestDto processInstanceQueryDto = createGetAllProcessInstancesRequest();
    if (sorting != null) {
      processInstanceQueryDto.setSorting(sorting);
    }

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getProcessInstances().size())
        .as("List size sorted by %s.", sortingDescription)
        .isEqualTo(8);

    assertThat(response.getProcessInstances())
        .as("Sorted by %s.", sortingDescription)
        .isSortedAccordingTo(comparator);
  }

  @Test
  public void testVariousSorting() throws Exception {

    createData();

    testSortingByStartDateAsc();
    testSortingByStartDateDesc();
    testDefaultSorting();
    testSortingByIdAsc();
    testSortingByIdDesc();
    testSortingByTenantIdAsc();
    testSortingByTenantIdDesc();
    testSortingByProcessNameAsc();
    testSortingByProcessNameDesc();
    testSortingByProcessVersionAsc();
    testSortingByProcessVersionDesc();
    testSortingByEndDateAsc();
    testSortingByEndDateDesc();
    testSortingByParentInstanceIdDesc();
    testSortingByParentInstanceIdAsc();
  }

  private void testSortingByStartDateAsc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        Comparator.comparing(ListViewProcessInstanceDto::getStartDate);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_START_DATE);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "startDate asc");
  }

  private void testSortingByStartDateDesc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_START_DATE);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "startDate desc");
  }

  private void testDefaultSorting() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        Comparator.comparing(o -> Long.valueOf(o.getId()));
    testSorting(null, comparator, "default");
  }

  private void testSortingByIdAsc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        Comparator.comparing(o -> Long.valueOf(o.getId()));
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "id asc");
  }

  private void testSortingByIdDesc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> Long.valueOf(o2.getId()).compareTo(Long.valueOf(o1.getId()));
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "id desc");
  }

  private void testSortingByTenantIdAsc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        Comparator.comparing((ListViewProcessInstanceDto o) -> o.getTenantId().toLowerCase())
            .thenComparingLong(o -> Long.valueOf(o.getId()));
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_TENANT_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "tenant asc");
  }

  private void testSortingByTenantIdDesc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> {
          int x = o2.getTenantId().toLowerCase().compareTo(o1.getTenantId().toLowerCase());
          if (x == 0) {
            x = Long.valueOf(o1.getId()).compareTo(Long.valueOf(o2.getId()));
          }
          return x;
        };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_TENANT_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "tenant desc");
  }

  private void testSortingByProcessNameAsc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        Comparator.comparing((ListViewProcessInstanceDto o) -> o.getProcessName().toLowerCase())
            .thenComparingLong(o -> Long.valueOf(o.getId()));
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_PROCESS_NAME);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "processName acs");
  }

  private void testSortingByProcessNameDesc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> {
          int x = o2.getProcessName().toLowerCase().compareTo(o1.getProcessName().toLowerCase());
          if (x == 0) {
            x = Long.valueOf(o1.getId()).compareTo(Long.valueOf(o2.getId()));
          }
          return x;
        };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_PROCESS_NAME);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "processName desc");
  }

  private void testSortingByProcessVersionAsc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        Comparator.comparing(ListViewProcessInstanceDto::getProcessVersion);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_WORFLOW_VERSION);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "processVersion asc");
  }

  private void testSortingByProcessVersionDesc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> o2.getProcessVersion().compareTo(o1.getProcessVersion());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_WORFLOW_VERSION);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "processVersion desc");
  }

  private void testSortingByEndDateAsc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> {
          // nulls are always at the end
          if (o1.getEndDate() == null && o2.getEndDate() == null) {
            return 0;
          } else if (o1.getEndDate() == null) {
            return 1;
          } else if (o2.getEndDate() == null) {
            return -1;
          } else {
            return o1.getEndDate().compareTo(o2.getEndDate());
          }
        };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_END_DATE);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "endDate asc");
  }

  private void testSortingByEndDateDesc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> {
          // nulls are always at the end
          if (o1.getEndDate() == null && o2.getEndDate() == null) {
            return 0;
          } else if (o1.getEndDate() == null) {
            return 1;
          } else if (o2.getEndDate() == null) {
            return -1;
          } else {
            return o2.getEndDate().compareTo(o1.getEndDate());
          }
        };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_END_DATE);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "endDate desc");
  }

  private void testSortingByParentInstanceIdDesc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> {
          int x;
          if (o1.getParentInstanceId() == null && o2.getParentInstanceId() == null) {
            x = 0;
          } else if (o1.getParentInstanceId() == null) {
            x = 1;
          } else if (o2.getParentInstanceId() == null) {
            x = -1;
          } else {
            x = o2.getParentInstanceId().compareTo(o1.getParentInstanceId());
          }
          if (x == 0) {
            x = Long.valueOf(o1.getId()).compareTo(Long.valueOf(o2.getId()));
          }
          return x;
        };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_PARENT_INSTANCE_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "parentProcessInstanceId desc");
  }

  private void testSortingByParentInstanceIdAsc() throws Exception {
    final Comparator<ListViewProcessInstanceDto> comparator =
        (o1, o2) -> {
          int x;
          if (o1.getParentInstanceId() == null && o2.getParentInstanceId() == null) {
            x = 0;
          } else if (o1.getParentInstanceId() == null) {
            x = 1;
          } else if (o2.getParentInstanceId() == null) {
            x = -1;
          } else {
            x = o1.getParentInstanceId().compareTo(o2.getParentInstanceId());
          }
          if (x == 0) {
            x = Long.valueOf(o1.getId()).compareTo(Long.valueOf(o2.getId()));
          }
          return x;
        };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(ListViewRequestDto.SORT_BY_PARENT_INSTANCE_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "parentProcessInstanceId asc");
  }

  @Test
  public void testSortingByWrongValue() throws Exception {
    // when
    final String wrongSortParameter = "bpmnProcessId";
    final String query =
        "{\"query\": {\"running\": true},"
            + "\"sorting\": { \"sortBy\": \""
            + wrongSortParameter
            + "\"}}}";
    final MockHttpServletRequestBuilder request =
        post(query()).content(query.getBytes()).contentType(mockMvcTestRule.getContentType());
    final MvcResult mvcResult =
        mockMvc.perform(request).andExpect(status().isBadRequest()).andReturn();

    // then
    assertErrorMessageContains(
        mvcResult, "SortBy parameter has invalid value: " + wrongSortParameter);
  }

  private void testQueryAllFinished() throws Exception {
    final ListViewRequestDto processInstanceQueryDto = createGetAllFinishedRequest();

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getProcessInstances().size()).isEqualTo(2);
    for (final ListViewProcessInstanceDto processInstanceDto : response.getProcessInstances()) {
      assertThat(processInstanceDto.getEndDate()).isNotNull();
      assertThat(processInstanceDto.getState())
          .isIn(ProcessInstanceStateDto.COMPLETED, ProcessInstanceStateDto.CANCELED);
    }
  }

  private void testQueryFinishedAndRunning() throws Exception {
    final ListViewRequestDto processInstanceQueryDto = createGetAllProcessInstancesRequest();

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getTotalCount()).isEqualTo(8);
    assertThat(response.getProcessInstances().size()).isEqualTo(8);
  }

  private void testQueryFinishedCompleted() throws Exception {
    final ListViewRequestDto processInstanceQueryDto =
        createProcessInstanceRequest(
            q -> {
              q.setFinished(true).setCompleted(true);
            });

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances().size()).isEqualTo(1);
    assertThat(response.getProcessInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getProcessInstances().get(0).getState())
        .isEqualTo(ProcessInstanceStateDto.COMPLETED);
  }

  private void testQueryRetriesLeft() throws Exception {
    final ListViewRequestDto processInstanceQueryDto =
        createGetAllProcessInstancesRequest(
            q -> {
              q.setRetriesLeft(true);
            });

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getProcessInstances().size()).isEqualTo(2);
    assertThat(response.getProcessInstances())
        .extracting(ListViewProcessInstanceDto::getId)
        .containsExactlyInAnyOrder(runningInstance.getId(), instanceWithoutIncident.getId());
  }

  private void testQueryFinishedCanceled() throws Exception {
    final ListViewRequestDto processInstanceQueryDto =
        createProcessInstanceRequest(
            q -> {
              q.setFinished(true).setCanceled(true);
            });

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances().size()).isEqualTo(1);
    assertThat(response.getProcessInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getProcessInstances().get(0).getState())
        .isEqualTo(ProcessInstanceStateDto.CANCELED);
    assertThat(response.getProcessInstances()).extracting("rootInstanceId").containsOnlyNulls();
  }

  private void testQueryRunningWithIncidents() throws Exception {
    final ListViewRequestDto processInstanceQueryDto =
        createProcessInstanceRequest(
            q -> {
              q.setRunning(true).setIncidents(true);
            });

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances().size()).isEqualTo(1);
    assertThat(response.getProcessInstances().get(0).getState())
        .isEqualTo(ProcessInstanceStateDto.INCIDENT);
  }

  private void testQueryRunningWithoutIncidents() throws Exception {
    final ListViewRequestDto processInstanceQueryDto =
        createProcessInstanceRequest(
            q -> {
              q.setRunning(true).setActive(true);
            });

    final MvcResult mvcResult = postRequest(query(), processInstanceQueryDto);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getProcessInstances().size()).isEqualTo(5);
    assertThat(response.getProcessInstances())
        .allMatch((pi) -> !pi.getState().equals(ProcessInstanceStateDto.INCIDENT));
  }

  @Test
  public void testQueryWithPermisssionForAllProcesses() throws Exception {
    // given
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto query = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.all());

    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getProcessInstances()).hasSize(3);
    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(
            processInstance1.getId(), processInstance2.getId(), processInstance3.getId());
  }

  @Test
  public void testQueryWithPermisssionForNoProcesses() throws Exception {
    // given
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto query = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));

    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getProcessInstances()).isEmpty();
  }

  @Test
  public void testQueryWithPermisssionForSpecificProcesses() throws Exception {
    // given
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.CANCELED).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.COMPLETED).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto query = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(
            PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId1, bpmnProcessId2)));

    final MvcResult mvcResult = postRequest(query(), query);

    // then
    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getProcessInstances()).hasSize(2);
    assertThat(response.getProcessInstances())
        .extracting(ListViewTemplate.ID)
        .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
  }

  //
  //  @Test
  //  public void testGetProcessInstanceById() throws Exception {
  //    createData();
  //
  //    MockHttpServletRequestBuilder request = get(String.format(GET_INSTANCE_URL,
  // instanceWithoutIncident.getId()));
  //
  //    final MvcResult mvcResult = mockMvc
  //      .perform(request)
  //      .andExpect(status().isOk())
  //      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
  //      .andReturn();
  //
  //    final ProcessInstanceDto processInstanceDto = mockMvcTestRule.fromResponse(mvcResult, new
  // TypeReference<ProcessInstanceDto>() {});
  //
  //    assertThat(processInstanceDto.getId()).isEqualTo(instanceWithoutIncident.getId());
  //
  // assertThat(processInstanceDto.getProcessId()).isEqualTo(instanceWithoutIncident.getProcessId());
  //    assertThat(processInstanceDto.getState()).isEqualTo(instanceWithoutIncident.getState());
  //
  // assertThat(processInstanceDto.getStartDate()).isEqualTo(instanceWithoutIncident.getStartDate());
  //    assertThat(processInstanceDto.getEndDate()).isEqualTo(instanceWithoutIncident.getEndDate());
  //
  // assertThat(processInstanceDto.getBpmnProcessId()).isEqualTo(instanceWithoutIncident.getBpmnProcessId());
  //
  //    assertThat(processInstanceDto.getActivities().size()).isGreaterThan(0);
  //
  // assertThat(processInstanceDto.getActivities().size()).isEqualTo(instanceWithoutIncident.getActivities().size());
  //
  //    assertThat(processInstanceDto.getIncidents().size()).isGreaterThan(0);
  //
  // assertThat(processInstanceDto.getIncidents().size()).isEqualTo(instanceWithoutIncident.getIncidents().size());
  //
  //  }

  // ---------------------------
  // OPE-669
  // ---- Observed queries: ----
  // Before:
  // {"queries":[{"running":true,"incidents":true,"active":true,"ids":["2251799813686074","2251799813686197"]}]}
  // After:
  // {"queries":[{"running":true,"completed":false,"canceled":false,"ids":["2251799813685731","2251799813685734"],"errorMessage":"","startDateAfter":null,"startDateBefore":null,"endDateAfter":null,"endDateBefore":null,"activityId":"","variable":{"name":"","value":""},"active":true,"incidents":true}]}
  private void testParamsAreEmptyStringsInsteadOfNull() throws Exception {

    final List<String> processInstanceIds =
        CollectionUtil.toSafeListOfStrings(
            runningInstance.getProcessInstanceKey().toString(),
            instanceWithoutIncident.getProcessInstanceKey().toString());
    final ListViewRequestDto queryRequest = createGetAllRunningRequest();
    queryRequest
        .getQuery()
        .setCompleted(false)
        .setCanceled(false)
        // part with empty strings instead of NULL
        .setErrorMessage("")
        .setActivityId("")
        .setVariable(new VariablesQueryDto("", ""))
        .setIds(processInstanceIds);

    final MvcResult mvcResult = postRequest(query(), queryRequest);

    final ListViewResponseDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getProcessInstances().size()).isEqualTo(2);
  }

  private void createProcessInstanceWithUpperLowerCaseProcessname() {
    final ProcessInstanceForListViewEntity upperProcess =
        createProcessInstance(ProcessInstanceState.ACTIVE, 42L, null);
    upperProcess.setProcessName("UPPER_LOWER_PROCESS_NAME");

    final ProcessInstanceForListViewEntity lowerProcess =
        createProcessInstance(ProcessInstanceState.ACTIVE, 23L, null);
    lowerProcess.setProcessName("upper_lower_process_name");

    searchTestRule.persistNew(upperProcess, lowerProcess);
  }

  private void createProcessInstanceWithoutProcessname() {
    final ProcessInstanceForListViewEntity processWithoutName =
        createProcessInstance(ProcessInstanceState.ACTIVE, 27L, null);
    processWithoutName.setBpmnProcessId("lower_process_id");
    processWithoutName.setProcessName(processWithoutName.getBpmnProcessId());

    searchTestRule.persistNew(processWithoutName);
  }

  protected void createData() {
    final List<VariableForListViewEntity> vars = new ArrayList<>();

    createProcessInstanceWithUpperLowerCaseProcessname();
    createProcessInstanceWithoutProcessname();
    // running instance with one activity and without incidents
    final Long processDefinitionKey = 27L;
    runningInstance =
        createProcessInstance(
            ProcessInstanceState.ACTIVE,
            processDefinitionKey,
            parentInstanceKey1,
            "PI_"
                + rootInstanceId
                + "/FI_someFlowNode/FNI_958398/PI_"
                + parentInstanceKey1
                + "/FI_anotherFlowNode/FNI_45345/PI_9898",
            tenant1);
    runningInstance.setBatchOperationIds(Arrays.asList("a", batchOperationId));
    final FlowNodeInstanceForListViewEntity activityInstance1 =
        TestUtil.createFlowNodeInstance(
            runningInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "start", null, true);
    vars.add(
        createVariableForListView(
            runningInstance.getProcessInstanceKey(),
            runningInstance.getProcessInstanceKey(),
            "var1",
            "X"));
    vars.add(
        createVariableForListView(
            runningInstance.getProcessInstanceKey(),
            runningInstance.getProcessInstanceKey(),
            "var2",
            "Y"));

    // completed instance with one activity and without incidents
    completedInstance =
        createProcessInstance(
            ProcessInstanceState.COMPLETED,
            processDefinitionKey,
            parentInstanceKey1,
            "PI_"
                + rootInstanceId
                + "/FI_someFlowNode/FNI_958398/PI_"
                + parentInstanceKey1
                + "/FI_anotherFlowNode/FNI_45345/PI_9898",
            tenant2);
    completedInstance.setBatchOperationIds(Arrays.asList("b", batchOperationId));
    final FlowNodeInstanceForListViewEntity activityInstance2 =
        TestUtil.createFlowNodeInstance(
            completedInstance.getProcessInstanceKey(), FlowNodeState.COMPLETED);
    vars.add(
        createVariableForListView(
            completedInstance.getProcessInstanceKey(),
            completedInstance.getProcessInstanceKey(),
            "var1",
            "X"));
    vars.add(
        createVariableForListView(
            completedInstance.getProcessInstanceKey(),
            completedInstance.getProcessInstanceKey(),
            "var2",
            "Z"));

    // canceled instance with two activities and without incidents
    canceledInstance = createProcessInstance(ProcessInstanceState.CANCELED);
    canceledInstance.setTenantId(tenant1);
    canceledInstance.setBatchOperationIds(Arrays.asList("c", "d"));
    final FlowNodeInstanceForListViewEntity activityInstance3 =
        TestUtil.createFlowNodeInstance(
            canceledInstance.getProcessInstanceKey(),
            FlowNodeState.COMPLETED,
            "start",
            null,
            false);
    final FlowNodeInstanceForListViewEntity activityInstance4 =
        TestUtil.createFlowNodeInstance(
            canceledInstance.getProcessInstanceKey(), FlowNodeState.TERMINATED);
    vars.add(
        createVariableForListView(
            canceledInstance.getProcessInstanceKey(),
            Long.valueOf(activityInstance3.getId()),
            "var1",
            "X"));
    vars.add(
        createVariableForListView(
            canceledInstance.getProcessInstanceKey(),
            canceledInstance.getProcessInstanceKey(),
            "var2",
            "W"));

    // instance with incidents (one resolved and one active) and one active activity
    final ProcessInstanceForListViewEntity instanceWithIncident =
        createProcessInstance(ProcessInstanceState.ACTIVE, true, tenant2);
    final FlowNodeInstanceForListViewEntity activityInstance5 =
        TestUtil.createFlowNodeInstance(
            instanceWithIncident.getProcessInstanceKey(), FlowNodeState.ACTIVE);
    vars.add(
        createVariableForListView(
            instanceWithIncident.getProcessInstanceKey(),
            instanceWithIncident.getProcessInstanceKey(),
            "var1",
            "Y"));
    createIncident(activityInstance5, null);

    // instance with one resolved incident and one completed activity
    instanceWithoutIncident = createProcessInstance(ProcessInstanceState.ACTIVE);
    instanceWithoutIncident.setTenantId(tenant1);
    instanceWithoutIncident.setParentProcessInstanceKey(parentInstanceKey2);
    final FlowNodeInstanceForListViewEntity activityInstance6 =
        TestUtil.createFlowNodeInstance(
            instanceWithoutIncident.getProcessInstanceKey(),
            FlowNodeState.COMPLETED,
            "start",
            null,
            true);

    // persist instances
    searchTestRule.persistNew(
        runningInstance,
        completedInstance,
        instanceWithIncident,
        instanceWithoutIncident,
        canceledInstance,
        activityInstance1,
        activityInstance2,
        activityInstance3,
        activityInstance4,
        activityInstance5,
        activityInstance6);

    searchTestRule.persistNew(vars.toArray(new OperateEntity[vars.size()]));
  }

  private String query() {
    return QUERY_INSTANCES_URL;
  }
}
