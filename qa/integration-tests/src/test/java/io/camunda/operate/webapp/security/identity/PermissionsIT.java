/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.webapp.security.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        PermissionsService.class
    },
    properties = {
        OperateProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url"
    }
)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class PermissionsIT {

  @Autowired
  private PermissionsService permissionsService;

  @Test
  public void testProcessesGrouped() {

    // given
    final String demoProcessId = "demoProcess";
    final String orderProcessId = "orderProcess";
    final String loanProcessId = "loanProcess";

    final Map<String, List<ProcessEntity>> processesGrouped = new LinkedHashMap<>();
    processesGrouped.put(demoProcessId, Collections.singletonList(new ProcessEntity().setBpmnProcessId(demoProcessId)));
    processesGrouped.put(orderProcessId, Collections.singletonList(new ProcessEntity().setBpmnProcessId(orderProcessId)));
    processesGrouped.put(loanProcessId, Collections.singletonList(new ProcessEntity().setBpmnProcessId(loanProcessId)));

    // when
    IdentityAuthentication authentication = Mockito.mock(IdentityAuthentication.class);
    Mockito.when(authentication.getAuthorizations()).thenReturn(Arrays.asList(
        new IdentityAuthorization().setResourceKey(demoProcessId).setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
            .setPermissions(new HashSet<>(Arrays.asList("READ", "DELETE", "UPDATE"))),
        new IdentityAuthorization().setResourceKey(orderProcessId).setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
            .setPermissions(new HashSet<>(Arrays.asList("READ", "DELETE")))));
    SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    List<ProcessGroupDto> processGroupDtos = ProcessGroupDto.createFrom(processesGrouped, permissionsService);

    // then
    assertThat(processGroupDtos).hasSize(3);

    final ProcessGroupDto demoProcessProcessGroup = processGroupDtos.stream().filter(x -> x.getBpmnProcessId().equals(demoProcessId)).findFirst().get();
    assertThat(demoProcessProcessGroup.getPermissions()).hasSize(3);
    assertThat(demoProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder("READ", "DELETE", "UPDATE");

    final ProcessGroupDto orderProcessProcessGroup = processGroupDtos.stream().filter(x -> x.getBpmnProcessId().equals(orderProcessId)).findFirst().get();
    assertThat(orderProcessProcessGroup.getPermissions()).hasSize(2);
    assertThat(orderProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder("READ", "DELETE");

    final ProcessGroupDto loanProcessProcessGroup = processGroupDtos.stream().filter(x -> x.getBpmnProcessId().equals(loanProcessId)).findFirst().get();
    assertThat(loanProcessProcessGroup.getPermissions()).isEmpty();
  }

  @Test
  public void testProcessesGroupedWithWildcardPermission() {

    // given
    final String demoProcessId = "demoProcess";
    final String orderProcessId = "orderProcess";
    final String loanProcessId = "loanProcess";

    final Map<String, List<ProcessEntity>> processesGrouped = new LinkedHashMap<>();
    processesGrouped.put(demoProcessId, List.of(new ProcessEntity().setBpmnProcessId(demoProcessId)));
    processesGrouped.put(orderProcessId, List.of(new ProcessEntity().setBpmnProcessId(orderProcessId)));
    processesGrouped.put(loanProcessId, List.of(new ProcessEntity().setBpmnProcessId(loanProcessId)));

    // when
    IdentityAuthentication authentication = Mockito.mock(IdentityAuthentication.class);
    Mockito.when(authentication.getAuthorizations()).thenReturn(Arrays.asList(
        new IdentityAuthorization().setResourceKey(demoProcessId).setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
            .setPermissions(new HashSet<>(List.of("DELETE"))),
        new IdentityAuthorization().setResourceKey(orderProcessId).setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
            .setPermissions(new HashSet<>(List.of("UPDATE"))),
        new IdentityAuthorization().setResourceKey(PermissionsService.RESOURCE_KEY_ALL).setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
            .setPermissions(new HashSet<>(List.of("READ")))));
    SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    List<ProcessGroupDto> processGroupDtos = ProcessGroupDto.createFrom(processesGrouped, permissionsService);

    // then
    assertThat(processGroupDtos).hasSize(3);

    final ProcessGroupDto demoProcessProcessGroup = processGroupDtos.stream().filter(x -> x.getBpmnProcessId().equals(demoProcessId)).findFirst().get();
    assertThat(demoProcessProcessGroup.getPermissions()).hasSize(2);
    assertThat(demoProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder("READ", "DELETE");

    final ProcessGroupDto orderProcessProcessGroup = processGroupDtos.stream().filter(x -> x.getBpmnProcessId().equals(orderProcessId)).findFirst().get();
    assertThat(orderProcessProcessGroup.getPermissions()).hasSize(2);
    assertThat(orderProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder("READ", "UPDATE");

    final ProcessGroupDto loanProcessProcessGroup = processGroupDtos.stream().filter(x -> x.getBpmnProcessId().equals(loanProcessId)).findFirst().get();
    assertThat(loanProcessProcessGroup.getPermissions()).hasSize(1);
    assertThat(loanProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder("READ");
  }
}
