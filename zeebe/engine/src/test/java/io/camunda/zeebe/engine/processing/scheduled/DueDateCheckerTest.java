/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DueDateCheckerTest {

  @Test
  public void shouldNotScheduleTwoTasks() {
    // given
    final var timerResolution = 100;
    final var dueDateChecker = new DueDateChecker(timerResolution, false, (builder) -> 0L);
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runDelayed(eq(Duration.ofMillis(timerResolution)), any(Task.class));
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    final var currentTimeMillis = System.currentTimeMillis();
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second

    // then
    verify(mockScheduleService).runDelayed(any(), any(Task.class));
  }

  @Test
  public void shouldScheduleForAnEarlierTasks() {
    // given
    final var timerResolution = 100;
    final var dueDateChecker = new DueDateChecker(timerResolution, false, (builder) -> 0L);
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runDelayed(any(Duration.class), any(Task.class)))
        .thenReturn(mockScheduledTask);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runDelayed(eq(Duration.ofMillis(timerResolution)), any(Task.class));
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    final var currentTimeMillis = System.currentTimeMillis();
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second
    dueDateChecker.schedule(currentTimeMillis + 100); // in 100 millis

    // then
    verify(mockScheduleService, times(2)).runDelayed(any(), any(Task.class));
    verify(mockScheduledTask).cancel();
  }

  @Test
  public void shouldRescheduleAutomatically() {
    // given
    final Function<TaskResultBuilder, Long> visitor =
        (builder) -> ActorClock.currentTimeMillis() + 1000L;

    final var timerResolution = 100;
    final var dueDateChecker = new DueDateChecker(timerResolution, false, visitor);
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runDelayed(any(Duration.class), any(Task.class)))
        .thenReturn(mockScheduledTask);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runDelayed(eq(Duration.ofMillis(timerResolution)), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    dueDateChecker.execute(mock(TaskResultBuilder.class));

    // then
    verify(mockScheduleService).runDelayed(any(), any(Task.class));
  }

  @Test
  public void shouldScheduleEarlierIfRescheduledAutomatically() {
    // given
    final Function<TaskResultBuilder, Long> visitor =
        (builder) -> ActorClock.currentTimeMillis() + 1000L;

    final var timerResolution = 100;
    final var dueDateChecker = new DueDateChecker(timerResolution, false, visitor);
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runDelayed(any(Duration.class), any(Task.class)))
        .thenReturn(mockScheduledTask);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runDelayed(eq(Duration.ofMillis(timerResolution)), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    dueDateChecker.execute(mock(TaskResultBuilder.class));
    // expect that there is a next execution scheduled after execution
    verify(mockScheduleService).runDelayed(any(), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    dueDateChecker.schedule(ActorClock.currentTimeMillis() + 100); // in one second

    // then
    verify(mockScheduleService).runDelayed(any(), any(Task.class));
    verify(mockScheduledTask).cancel();
  }
}
