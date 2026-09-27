package com.atlasflow.worker.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StepExecutorRegistry {

    private  final Map<String, StepExecutor> stepExecutors ;

    public StepExecutorRegistry(List<StepExecutor> executorList) {

        this.stepExecutors = executorList.stream()
                .collect(Collectors.toMap(
                        StepExecutor::taskType,
                        Function.identity()
                ));
    }

    public StepExecutor getExecutor(String taskType){

        StepExecutor executor = stepExecutors.get(taskType);
        if (executor == null){
            throw new IllegalArgumentException("No executor found for task type: " + taskType);
        }
        return executor;
    }


}
