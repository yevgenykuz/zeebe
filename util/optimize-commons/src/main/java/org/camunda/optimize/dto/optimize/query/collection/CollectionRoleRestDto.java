/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;

import java.util.Optional;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
public class CollectionRoleRestDto implements Comparable<CollectionRoleRestDto> {
  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
  private String id;
  private IdentityDto identity;
  private RoleType role;
  private Boolean hasFullScopeAuthorizations;

  public CollectionRoleRestDto(CollectionRoleDto oldRole) {
    if (oldRole.getIdentity().getType().equals(IdentityType.USER)) {
      UserDto oldUserDto = (UserDto) oldRole.getIdentity();
      this.identity = new UserDto(
        oldUserDto.getId(),
        oldUserDto.getFirstName(),
        oldUserDto.getLastName(),
        oldUserDto.getEmail()
      );
    } else {
      GroupDto oldGroupDto = (GroupDto) oldRole.getIdentity();
      this.identity = new GroupDto(oldGroupDto.getId(), oldGroupDto.getName(), oldGroupDto.getMemberCount());
    }

    this.role = oldRole.getRole();
    this.id = oldRole.getId();
  }

  @Override
  public int compareTo(final CollectionRoleRestDto other) {
    if (this.identity instanceof UserDto && other.getIdentity() instanceof GroupDto) {
      return 1;
    } else if (this.identity instanceof GroupDto && other.getIdentity() instanceof UserDto) {
      return -1;
    } else {
      return StringUtils.compareIgnoreCase(this.identity.getName(), other.getIdentity().getName());
    }
  }
}
