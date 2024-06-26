/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.prometheus.client.Histogram;
import java.util.List;

/**
 * Represents an in-flight entry and its lifecycle from being written, committed, processed and
 * finally exported. Updates metrics and backpressure limits after being {@link
 * InFlightEntry#onAppend(long) started} and handles callbacks from the log storage and other
 * components involved.
 */
public final class InFlightEntry {

  private final List<LogAppendEntryMetadata> entryMetadata;
  private final Limiter.Listener limiter;
  private final LogStreamMetrics metrics;
  private Histogram.Timer writeTimer;
  private Histogram.Timer commitTimer;
  private long position;

  public InFlightEntry(
      final List<LogAppendEntryMetadata> entryMetadata,
      final Limiter.Listener limiter,
      final LogStreamMetrics metrics) {
    this.entryMetadata = entryMetadata;
    this.limiter = limiter;
    this.metrics = metrics;
  }

  public InFlightEntry onAppend(final long position) {
    this.position = position;
    writeTimer = metrics.startWriteTimer();
    commitTimer = metrics.startCommitTimer();
    metrics.increaseInflight();
    metrics.increaseTriedAppends();
    return this;
  }

  public void onWrite() {
    writeTimer.close();
    entryMetadata.forEach(
        metadata ->
            metrics.recordAppendedEntry(
                1, metadata.recordType(), metadata.valueType(), metadata.intent()));
    metrics.setLastWrittenPosition(position);
  }

  public void onCommit() {
    metrics.decreaseInflight();
    metrics.setLastCommittedPosition(position);
    if (commitTimer != null) {
      commitTimer.close();
    }
    limiter.onSuccess();
  }
}
