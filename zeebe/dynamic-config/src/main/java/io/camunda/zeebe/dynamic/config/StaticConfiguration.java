/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.TopologyUtil;
import java.util.List;
import java.util.Set;

public record StaticConfiguration(
    PartitionDistributor partitionDistributor,
    Set<MemberId> clusterMembers,
    MemberId localMemberId,
    List<PartitionId> partitionIds,
    int replicationFactor) {

  public ClusterConfiguration generateTopology() {
    final Set<PartitionMetadata> partitionDistribution = generatePartitionDistribution();
    return TopologyUtil.getClusterTopologyFrom(partitionDistribution);
  }

  public Set<PartitionMetadata> generatePartitionDistribution() {
    final var sortedPartitionIds = partitionIds.stream().sorted().toList();
    return partitionDistributor.distributePartitions(
        clusterMembers, sortedPartitionIds, replicationFactor);
  }
}
