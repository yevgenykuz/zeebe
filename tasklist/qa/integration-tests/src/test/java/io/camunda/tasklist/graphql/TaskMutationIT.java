/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.graphql;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TaskMutationIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String TASK_RESULT_PATTERN = "{id name assignee taskState completionTime}";
  public static final String COMPLETE_TASK_MUTATION_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: [%s])" + TASK_RESULT_PATTERN + "}";
  public static final String CLAIM_TASK_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";
  public static final String CLAIM_TASK_WITH_PARAM_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\", assignee: \"%s\")" + TASK_RESULT_PATTERN + "}";
  public static final String UNCLAIM_TASK_MUTATION_PATTERN =
      "mutation {unclaimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";
  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @BeforeEach
  public void before() {
    super.before();
  }

  @Test
  public void shouldFailCompleteNotAssigned() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response = tester.getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final String completeTaskRequest =
        String.format(COMPLETE_TASK_MUTATION_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");

    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertEquals("Task is not assigned", response.get("$.errors[0].message"));

    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

    // when
    setCurrentUser(new UserDTO().setUserId("joe").setPermissions(List.of(Permission.WRITE)));
    final String completeTaskRequestAssignToMe =
        String.format(COMPLETE_TASK_MUTATION_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");

    response = graphQLTestTemplate.postMultipart(completeTaskRequestAssignToMe, "{}");

    // then
    assertEquals("Task is not assigned to joe", response.get("$.errors[0].message"));
  }

  @Test
  public void shouldCompleteTask() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response = tester.getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

    final String completeTaskRequest =
        String.format(COMPLETE_TASK_MUTATION_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertTaskIsCompleted(response.get("$.data.completeTask", TaskDTO.class), taskId);

    // query "Get tasks" immediately
    response = tester.getAllTasks();
    assertTaskIsCompleted(response.get("$.data.tasks[0]", TaskDTO.class), taskId);
  }

  @Test
  public void shouldStoreSnapshotVariablesOnTaskCompletion() throws IOException {
    // having
    final String flowNodeBpmnIdA = "taskA";
    final String flowNodeBpmnIdB = "taskB";
    createTwoTasksInstance(flowNodeBpmnIdA, flowNodeBpmnIdB);

    GraphQLResponse response = tester.getAllTasks();
    final String taskAId = response.get("$.data.tasks[0].id");

    // complete task A
    tester.claimHumanTask(flowNodeBpmnIdA);
    String completeTaskRequest =
        String.format(
            COMPLETE_TASK_MUTATION_PATTERN,
            taskAId,
            "{name: \"var\", value: \"\\\"taskAValue\\\"\"}, {name: \"varTaskA\", value: \"123\"}");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");
    assertThat(response.get("$.data.completeTask.id")).isNotNull();
    tester.waitUntil().taskIsCompleted(flowNodeBpmnIdA).waitUntil().taskIsCreated(flowNodeBpmnIdB);

    // complete task B
    tester.claimHumanTask(flowNodeBpmnIdB);
    response = tester.getAllTasks();
    final String taskBId = response.get("$.data.tasks[0].id");
    completeTaskRequest =
        String.format(
            COMPLETE_TASK_MUTATION_PATTERN,
            taskBId,
            "{name: \"var\", value: \"\\\"taskBValue\\\"\"}, {name: \"varTaskB\", value: \"true\"}");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");
    assertThat(response.get("$.data.completeTask.id")).isNotNull();
    tester.waitUntil().taskIsCompleted(flowNodeBpmnIdB);

    // when
    response = tester.getAllTasks();
    assertTrue(response.isOk());
    assertEquals("2", response.get("$.data.tasks.length()"));

    // then
    // task B
    assertEquals(taskBId, response.get("$.data.tasks[0].id"));
    assertEquals("var", response.get("$.data.tasks[0].variables[0].name"));
    assertEquals("\"taskBValue\"", response.get("$.data.tasks[0].variables[0].value"));

    // task A
    assertEquals(taskAId, response.get("$.data.tasks[1].id"));
    assertEquals("var", response.get("$.data.tasks[1].variables[0].name"));
    assertEquals("\"taskAValue\"", response.get("$.data.tasks[1].variables[0].value"));
  }

  private void createTwoTasksInstance(String flowNodeBpmnIdA, String flowNodeBpmnIdB) {
    final String payload = "{\"var\": \"value\"}";
    final String bpmnProcessId = "testProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .userTask(flowNodeBpmnIdA)
            .userTask(flowNodeBpmnIdB)
            .endEvent()
            .done();
    tester
        .deployProcess(process, bpmnProcessId + ".bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstance(BPMN_PROCESS_ID, payload)
        .waitUntil()
        .taskIsCreated(flowNodeBpmnIdA);
  }

  @Test
  public void shouldCompleteWithoutVariablesTask() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response = tester.getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

    final String completeTaskRequest = String.format(COMPLETE_TASK_MUTATION_PATTERN, taskId, "");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertTaskIsCompleted(response.get("$.data.completeTask", TaskDTO.class), taskId);

    // query "Get tasks" immediately
    response = tester.getAllTasks();
    assertTaskIsCompleted(response.get("$.data.tasks[0]", TaskDTO.class), taskId);
  }

  private void assertTaskIsCompleted(TaskDTO taskDTO, String taskId) {
    assertThat(taskDTO.getId()).isEqualTo(taskId);
    assertThat(taskDTO.getTaskState()).isEqualTo(TaskState.COMPLETED);
    assertThat(taskDTO.getCompletionTime()).isNotNull();
  }

  @Test
  public void shouldFailNonApiUserClaimWithAssigneeParam() throws IOException {
    tester
        .having()
        .and()
        .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 0, 1)
        .when()
        .getAllTasks();

    final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);

    setCurrentUser(
        new UserDTO().setUserId("joe").setApiUser(false).setPermissions(List.of(Permission.WRITE)));
    final Map<String, Object> errors =
        tester
            .when()
            .claimTask(
                String.format(
                    CLAIM_TASK_WITH_PARAM_MUTATION_PATTERN,
                    unclaimedTask.getId(),
                    "anotherAssignee"))
            .then()
            .getByPath("$.errors[0]");
    assertEquals(
        "User doesn't have the permission to assign another user to this task",
        errors.get("message"));
  }

  @Test
  public void shouldFailClaimAlreadyAssigned() throws IOException {
    try {
      tester
          .having()
          .and()
          .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
          .when()
          .getAllTasks();

      final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);
      tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, unclaimedTask.getId()));

      // when
      setCurrentUser(new UserDTO().setUserId("joe").setPermissions(List.of(Permission.WRITE)));
      final Map<String, Object> errors =
          tester
              .when()
              .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, unclaimedTask.getId()))
              .then()
              .getByPath("$.errors[0]");
      assertEquals("Task is already assigned", errors.get("message"));
    } finally {
      setDefaultCurrentUser();
    }
  }

  @Test
  public void shouldClaimTask() throws IOException {
    tester
        .having()
        .and()
        .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
        .when()
        .getAllTasks();

    final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);

    final Map<String, Object> claimedTask =
        tester
            .when()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, unclaimedTask.getId()))
            .then()
            .getByPath("$.data.claimTask");

    assertEquals(claimedTask.get("id"), unclaimedTask.getId());
    final String assignee = (String) claimedTask.get("assignee");
    assertEquals(getDefaultCurrentUser().getUserId(), assignee);

    // query "Get tasks" immediately
    tester.getAllTasks();
    final TaskDTO claimedTaskObject = tester.getTasksByPath("$.data.tasks").get(0);
    assertEquals(getDefaultCurrentUser().getUserId(), claimedTaskObject.getAssignee());
  }

  @Test
  public void shouldClaimTaskToAssigneeForAPIUser() throws IOException {
    tester
        .having()
        .and()
        .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
        .when()
        .getAllTasks();

    final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);
    setCurrentUser(
        new UserDTO().setUserId("joe").setApiUser(true).setPermissions(List.of(Permission.WRITE)));
    final String assigneeID = "otherAssigneeID";

    final Map<String, Object> claimedTask =
        tester
            .when()
            .claimTask(
                String.format(
                    CLAIM_TASK_WITH_PARAM_MUTATION_PATTERN, unclaimedTask.getId(), assigneeID))
            .then()
            .getByPath("$.data.claimTask");

    assertEquals(claimedTask.get("id"), unclaimedTask.getId());
    final String assignee = (String) claimedTask.get("assignee");
    assertEquals(assigneeID, assignee);

    // query "Get tasks" immediately
    tester.getAllTasks();
    final TaskDTO claimedTaskObject = tester.getTasksByPath("$.data.tasks").get(0);
    assertEquals(assigneeID, claimedTaskObject.getAssignee());
  }

  @Test
  public void shouldClaimTaskNonApiUserWithSameUsername() throws IOException {
    tester
        .having()
        .and()
        .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
        .when()
        .getAllTasks();

    final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);
    setCurrentUser(
        new UserDTO().setUserId("joe").setApiUser(false).setPermissions(List.of(Permission.WRITE)));
    final String assigneeID = "joe"; // verify whether same assignee as logged user works

    final Map<String, Object> claimedTask =
        tester
            .when()
            .claimTask(
                String.format(
                    CLAIM_TASK_WITH_PARAM_MUTATION_PATTERN, unclaimedTask.getId(), assigneeID))
            .then()
            .getByPath("$.data.claimTask");

    assertEquals(claimedTask.get("id"), unclaimedTask.getId());
    final String assignee = (String) claimedTask.get("assignee");
    assertEquals(assigneeID, assignee);

    // query "Get tasks" immediately
    tester.getAllTasks();
    final TaskDTO claimedTaskObject = tester.getTasksByPath("$.data.tasks").get(0);
    assertEquals(assigneeID, claimedTaskObject.getAssignee());
  }

  @Test
  public void shouldFailForNotActiveTasksOperations() throws IOException {
    // having
    tester.having().createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 0, 1).getAllTasks();

    final String taskId = tester.get("$.data.tasks[0].id");

    // when try to unclaim a task
    final Map<String, Object> errorsUnclaimed =
        tester
            .when()
            .unclaimTask(String.format(UNCLAIM_TASK_MUTATION_PATTERN, taskId))
            .then()
            .getByPath("$.errors[0]");

    // then
    assertEquals("Task is not active", errorsUnclaimed.get("message"));

    // when try to claim a task
    final Map<String, Object> errorsClaim =
        tester
            .when()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId))
            .then()
            .getByPath("$.errors[0]");

    // then
    assertEquals("Task is not active", errorsClaim.get("message"));

    // when try to complete a task
    final String completeTaskRequest =
        String.format(COMPLETE_TASK_MUTATION_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");
    final var errorsComplete = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertEquals("Task is not active", errorsComplete.get("$.errors[0].message"));
  }

  @Test
  public void shouldFailUnclaimNotAssigned() throws IOException {
    // having
    tester.having().createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0).getAllTasks();

    final String taskId = tester.get("$.data.tasks[0].id");

    // when
    final Map<String, Object> errors =
        tester
            .when()
            .unclaimTask(String.format(UNCLAIM_TASK_MUTATION_PATTERN, taskId))
            .then()
            .getByPath("$.errors[0]");

    // then
    assertEquals("Task is not assigned", errors.get("message"));
  }

  @Test
  public void shouldUnclaimTask() throws IOException {
    // having
    tester.having().createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0).getAllTasks();

    final String taskId = tester.get("$.data.tasks[0].id");
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

    // when
    final Map<String, Object> task =
        tester
            .when()
            .unclaimTask(String.format(UNCLAIM_TASK_MUTATION_PATTERN, taskId))
            .then()
            .getByPath("$.data.unclaimTask");

    // then
    assertEquals(taskId, task.get("id"));
    assertNull(task.get("assignee"));

    // query "Get tasks" immediately
    final GraphQLResponse allTasks = tester.getAllTasks();
    assertThat(allTasks.get("$.data.tasks[0].assignee")).isNull();
  }

  private void createCreatedAndCompletedTasks(int created, int completed) {
    final String payload = "{\"var\": \"value\"}";
    tester
        .createAndDeploySimpleProcess(BPMN_PROCESS_ID, ELEMENT_ID)
        .waitUntil()
        .processIsDeployed()
        .and();
    sleepFor(5000);
    // complete tasks
    for (int i = 0; i < completed; i++) {
      tester
          .startProcessInstance(BPMN_PROCESS_ID, payload)
          .waitUntil()
          .taskIsCreated(ELEMENT_ID)
          .and()
          .claimAndCompleteHumanTask(ELEMENT_ID);
    }
    // start more process instances
    for (int i = 0; i < created; i++) {
      tester.startProcessInstance(BPMN_PROCESS_ID, payload).waitUntil().taskIsCreated(ELEMENT_ID);
    }
  }
}
