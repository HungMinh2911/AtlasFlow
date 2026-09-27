package com.atlasflow.worker.application;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.execution.api.JobExecutionResponse;
import com.atlasflow.job.execution.application.JobExecutionApplicationService;
import com.atlasflow.job.execution.domain.JobExecutionStatus;
import com.atlasflow.message.domain.NonRetryableJobException;
import com.atlasflow.message.domain.RetryableJobException;
import com.atlasflow.workflow.step.application.port.WorkflowStepRepository;
import com.atlasflow.workflow.step.domain.WorkflowStep;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowWorker {

    private  final StepExecutorRegistry stepExecutorRegistry;
    private final WorkflowStepRepository workflowStepRepository;
    private final JobRepository jobRepository;
    private final JobExecutionApplicationService jobExecutionApplicationService;
    public WorkflowWorker(StepExecutorRegistry stepExecutorRegistry, WorkflowStepRepository workflowStepRepository, JobRepository jobRepository, JobExecutionApplicationService jobExecutionApplicationService) {
        this.stepExecutorRegistry = stepExecutorRegistry;
        this.workflowStepRepository = workflowStepRepository;
        this.jobRepository = jobRepository;
        this.jobExecutionApplicationService = jobExecutionApplicationService;
    }

    public void execute(JobExecutionResponse jobExecution) {
        Job job = jobRepository.findById(jobExecution.jobId())
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobExecution.jobId()));

        List<WorkflowStep> steps =
                workflowStepRepository.getListWorkflowStepByWorkflowId(
                        job.workflowId()
                );

        try {
            for (WorkflowStep step : steps) {
                StepExecutor stepExecutor = stepExecutorRegistry.getExecutor(step.taskType().name());
                if (stepExecutor == null) {
                    throw new NonRetryableJobException(
                            "No executor for task type: " + step.taskType().name());
                }

                if (!stepExecutor.execute(step, job)) {
                    throw new NonRetryableJobException("Workflow step failed");
                }
            }

            jobExecutionApplicationService.finishExecution(
                    jobExecution.id(), JobExecutionStatus.SUCCEEDED);
        } catch (RetryableJobException exception) {
            jobExecutionApplicationService.failAndRequeue(jobExecution.id());
            throw exception;
        } catch (NonRetryableJobException exception) {
            jobExecutionApplicationService.finishExecution(
                    jobExecution.id(), JobExecutionStatus.FAILED);
            throw exception;
        }

    }
}
