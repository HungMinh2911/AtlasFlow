package com.atlasflow.worker.application;

import com.atlasflow.job.domain.Job;
import com.atlasflow.workflow.step.domain.WorkflowStep;

public interface StepExecutor {

    String taskType();

    boolean supports(String stepType);

    boolean execute(WorkflowStep step, Job job);
}
