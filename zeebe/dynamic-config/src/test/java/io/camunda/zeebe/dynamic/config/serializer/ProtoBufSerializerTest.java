/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class ProtoBufSerializerTest {

  final ProtoBufSerializer protoBufSerializer = new ProtoBufSerializer();

  @ParameterizedTest
  @MethodSource("provideClusterTopologies")
  void shouldEncodeAndDecode(final ClusterConfiguration initialClusterConfiguration) {
    // given
    final ClusterConfigurationGossipState gossipState = new ClusterConfigurationGossipState();
    gossipState.setClusterConfiguration(initialClusterConfiguration);

    // when
    final var decodedState = protoBufSerializer.decode(protoBufSerializer.encode(gossipState));

    // then
    assertThat(decodedState.getClusterConfiguration())
        .describedAs("Decoded clusterTopology must be equal to initial one")
        .isEqualTo(initialClusterConfiguration);
  }

  @Test
  void shouldEncodeAndDecodeClusterTopology() {
    // given
    final var initialClusterTopology = topologyWithTwoMembers();

    // when
    final var encoded = protoBufSerializer.encode(initialClusterTopology);
    final var decodedClusterTopology =
        protoBufSerializer.decodeClusterTopology(encoded, 0, encoded.length);

    // then
    assertThat(decodedClusterTopology)
        .describedAs("Decoded clusterTopology must be equal to initial one")
        .isEqualTo(initialClusterTopology);
  }

  @Test
  void shouldEncodeAndDecodeAddMembersRequest() {
    // given
    final var addMembersRequest =
        new AddMembersRequest(Set.of(MemberId.from("1"), MemberId.from("2")), false);

    // when
    final var encodedRequest = protoBufSerializer.encodeAddMembersRequest(addMembersRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeAddMembersRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(addMembersRequest);
  }

  @Test
  void shouldEncodeAndDecodeRemoveMembersRequest() {
    // given
    final var removeMembersRequest =
        new RemoveMembersRequest(Set.of(MemberId.from("1"), MemberId.from("2")), false);

    // when
    final var encodedRequest = protoBufSerializer.encodeRemoveMembersRequest(removeMembersRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeRemoveMembersRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(removeMembersRequest);
  }

  @Test
  void shouldEncodeAndDecodeReassignAllPartitionsRequest() {
    // given
    final var reassignPartitionsRequest =
        new ReassignPartitionsRequest(Set.of(MemberId.from("1"), MemberId.from("2")), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeReassignPartitionsRequest(reassignPartitionsRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeReassignPartitionsRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(reassignPartitionsRequest);
  }

  @Test
  void shouldEncodeAndDecodeJoinPartitionRequest() {
    // given
    final var joinPartitionRequest = new JoinPartitionRequest(MemberId.from("2"), 3, 5, false);

    // when
    final var encodedRequest = protoBufSerializer.encodeJoinPartitionRequest(joinPartitionRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeJoinPartitionRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(joinPartitionRequest);
  }

  @Test
  void shouldEncodeAndDecodeLeavePartitionRequest() {
    // given
    final var leavePartitionRequest = new LeavePartitionRequest(MemberId.from("6"), 2, false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeLeavePartitionRequest(leavePartitionRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeLeavePartitionRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(leavePartitionRequest);
  }

  @Test
  void shouldEncodeAndDecodeTopologyChangeResponse() {
    // given
    final var topologyChangeResponse =
        new ClusterConfigurationChangeResponse(
            2,
            Map.of(
                MemberId.from("1"),
                MemberState.initializeAsActive(Map.of()),
                MemberId.from("2"),
                MemberState.initializeAsActive(Map.of())),
            Map.of(MemberId.from("2"), MemberState.initializeAsActive(Map.of())),
            List.of(
                new MemberLeaveOperation(MemberId.from("1")),
                new PartitionJoinOperation(MemberId.from("2"), 1, 2)));

    // when
    final var encodedResponse = protoBufSerializer.encodeResponse(topologyChangeResponse);

    // then
    final var decodedResponse =
        protoBufSerializer.decodeTopologyChangeResponse(encodedResponse).get();
    assertThat(decodedResponse).isEqualTo(topologyChangeResponse);
  }

  private static Stream<ClusterConfiguration> provideClusterTopologies() {
    return Stream.of(
        topologyWithOneMemberNoPartitions(),
        topologyWithOneJoiningMember(),
        topologyWithOneLeavingMember(),
        topologyWithOneLeftMember(),
        topologyWithOneMemberOneActivePartition(),
        topologyWithOneMemberOneLeavingPartition(),
        topologyWithOneMemberOneJoiningPartition(),
        topologyWithOneMemberTwoPartitions(),
        topologyWithTwoMembers(),
        topologyWithClusterChangePlan(),
        topologyWithCompletedClusterChangePlan(),
        topologyWithClusterChangePlanWithMemberOperations(),
        topologyWithExporterState());
  }

  private static ClusterConfiguration topologyWithOneMemberNoPartitions() {
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));
  }

  private static ClusterConfiguration topologyWithOneJoiningMember() {
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.uninitialized().toJoining());
  }

  private static ClusterConfiguration topologyWithOneLeavingMember() {
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()).toLeaving());
  }

  private static ClusterConfiguration topologyWithOneLeftMember() {
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()).toLeft());
  }

  private static ClusterConfiguration topologyWithOneMemberOneActivePartition() {
    return ClusterConfiguration.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));
  }

  private static ClusterConfiguration topologyWithOneMemberOneLeavingPartition() {
    return ClusterConfiguration.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(Map.of(1, PartitionState.active(1).toLeaving())));
  }

  private static ClusterConfiguration topologyWithOneMemberOneJoiningPartition() {
    return ClusterConfiguration.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(Map.of(1, PartitionState.joining(1))));
  }

  private static ClusterConfiguration topologyWithOneMemberTwoPartitions() {
    return ClusterConfiguration.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.active(1), 2, PartitionState.active(2).toLeaving())));
  }

  private static ClusterConfiguration topologyWithTwoMembers() {
    return ClusterConfiguration.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.joining(1), 2, PartitionState.active(2))))
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()).toLeaving());
  }

  private static ClusterConfiguration topologyWithClusterChangePlan() {
    final List<ClusterConfigurationChangeOperation> changes =
        List.of(
            new PartitionLeaveOperation(MemberId.from("1"), 1),
            new PartitionJoinOperation(MemberId.from("2"), 2, 5),
            new PartitionReconfigurePriorityOperation(MemberId.from("3"), 4, 3),
            new PartitionForceReconfigureOperation(
                MemberId.from("4"), 5, List.of(MemberId.from("1"), MemberId.from("3"))),
            new MemberRemoveOperation(MemberId.from("5"), MemberId.from("6")));
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
        .startConfigurationChange(changes);
  }

  private static ClusterConfiguration topologyWithCompletedClusterChangePlan() {
    final List<ClusterConfigurationChangeOperation> changes =
        List.of(new PartitionLeaveOperation(MemberId.from("1"), 1));
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
        .startConfigurationChange(changes)
        .advanceConfigurationChange(topology -> topology);
  }

  private static ClusterConfiguration topologyWithClusterChangePlanWithMemberOperations() {
    final List<ClusterConfigurationChangeOperation> changes =
        List.of(
            new MemberJoinOperation(MemberId.from("2")),
            new MemberLeaveOperation(MemberId.from("1")));
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
        .startConfigurationChange(changes);
  }

  private static ClusterConfiguration topologyWithExporterState() {
    final var dynamicConfig =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(
                    "expA",
                    new ExporterState(State.ENABLED),
                    "expB",
                    new ExporterState(State.DISABLED))));
    return ClusterConfiguration.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
        .updateMember(
            MemberId.from("1"),
            m ->
                m.addPartition(
                    1, new PartitionState(PartitionState.State.ACTIVE, 1, dynamicConfig)));
  }
}
