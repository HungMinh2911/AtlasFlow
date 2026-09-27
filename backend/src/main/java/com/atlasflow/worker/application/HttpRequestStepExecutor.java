package com.atlasflow.worker.application;

import com.atlasflow.job.domain.Job;
import com.atlasflow.message.domain.NonRetryableJobException;
import com.atlasflow.message.domain.RetryableJobException;
import com.atlasflow.workflow.step.domain.InvalidWorkflowStepConfigException;
import com.atlasflow.workflow.step.domain.WorkflowStep;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

@Component
public class HttpRequestStepExecutor implements StepExecutor {

    private final RestClient restClient;

    public HttpRequestStepExecutor(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String taskType() {
        return "HTTP_REQUEST";
    }

    @Override
    public boolean supports(String stepType) {
        return "HTTP_REQUEST".equals(stepType);
    }


//    {
//        "url": "http://localhost:8081/demo",
//            "method": "POST",
//            "headers": {
//        "Content-Type": "application/json"
//    },
//        "body": {
//        "job_id": "{{job.id}}",
//                "file_id": "{{job.file_id}}"
//    },
//        "expected_statuses": [200, 201, 202]
//    }
    @Override
    public boolean execute(WorkflowStep step, Job job) {
        JsonNode config = step.configJson();

        JsonNode urlNode = config.get("url");
        JsonNode methodNode = config.get("method");
        JsonNode headersNode = config.get("headers");
        JsonNode bodyNode = config.get("body");
        JsonNode expectedStatusesNode = config.get("expected_statuses");

        if (urlNode == null || !urlNode.isTextual() || urlNode.asText().isBlank()) {
            throw new InvalidWorkflowStepConfigException(
                    "url is required and must be text"
            );
        }
        if (methodNode == null || !methodNode.isTextual() || methodNode.asText().isBlank()) {
            throw new InvalidWorkflowStepConfigException(
                    "method is required and must be text"
            );
        }

        String url = urlNode.asText().trim();
        String methodValue = methodNode.asText().trim().toUpperCase();
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(methodValue);
        } catch (IllegalArgumentException exception) {
            throw new InvalidWorkflowStepConfigException(
                    "Unsupported HTTP method: " + methodValue
            );
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new InvalidWorkflowStepConfigException(
                    "Invalid HTTP URL: " + url
            );
        }

        Set<Integer> expectedStatuses = readExpectedStatuses(config.get("expected_statuses"));

        HttpHeaders headers = new HttpHeaders();
        readHeaders(config.get("headers"), headers);

        JsonNode body = config.get("body");


        try {
            ResponseEntity<Void> response;

            if(body == null || body.isNull()){

                response = restClient
                        .method(httpMethod)
                        .uri(uri)
                        .headers( httpHeaders ->
                            httpHeaders.addAll(headers)
                        )
                        .retrieve()
                        .toBodilessEntity();
            }
            else {
                response = restClient
                        .method(httpMethod)
                        .uri(uri)
                        .headers(httpHeaders ->
                                httpHeaders.addAll(headers)
                        )
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
            }

            int statusCode = response.getStatusCode().value();

            return expectedStatuses.contains(statusCode);

        }
        catch (HttpClientErrorException exception) {
            int status = exception.getStatusCode().value();

            if (status == 408 || status == 429) {
                throw new RetryableJobException(
                        "Temporary HTTP error: " + status,
                        exception
                );
            }

            throw new NonRetryableJobException(
                    "Permanent HTTP error: " + status
            );
        }
        catch (HttpServerErrorException exception) {
            throw new RetryableJobException(
                    "Temporary server error: "
                            + exception.getStatusCode().value(),
                    exception
            );
        }
        catch (ResourceAccessException exception) {
            throw new RetryableJobException(
                    "Connection timeout or refused",
                    exception
            );
        }
        catch (RestClientException exception) {
            // Các lỗi transport còn lại thường là lỗi tạm thời của
            // downstream/client; cho phép consumer áp dụng retry policy.
            throw new RetryableJobException(
                    "Temporary HTTP client failure",
                    exception
            );
        }
    }

    private void readHeaders(
            JsonNode headersNode,
            HttpHeaders headers
    ){
        if (headersNode == null || headersNode.isNull()){
            return;
        }

        if (!headersNode.isObject()) {
            throw new InvalidWorkflowStepConfigException(
                    "headers must be a JSON object"
            );
        }

        java.util.Iterator<String> fieldNames = headersNode.fieldNames();

        while (fieldNames.hasNext()){

            String name = fieldNames.next();
            JsonNode valueNode = headersNode.get(name);

            if (!valueNode.isValueNode()){
                throw new InvalidWorkflowStepConfigException(
                        "Header value must be a primitive value: " + name
                );
            }
            headers.add(name, valueNode.asText());
        }




    }


    private Set<Integer> readExpectedStatuses(JsonNode statusesNode){

        if (statusesNode == null || statusesNode.isNull()){
            return Set.of(200,201,202,204);
        }

        if (!statusesNode.isArray()
                || statusesNode.isEmpty()) {
            throw new InvalidWorkflowStepConfigException(
                    "expected_statuses must be a non-empty array"
            );
        }

        Set<Integer> statuses = new HashSet<>();

        for (JsonNode statusNode : statusesNode){

            if (!statusNode.isInt()){
                throw new InvalidWorkflowStepConfigException(
                        "expected_statuses must be an array of integers"
                );
            }
            int status = statusNode.asInt();

            if (status < 100 || status > 599) {
                throw new InvalidWorkflowStepConfigException(
                        "Invalid HTTP status: " + status
                );
            }

            statuses.add(status);
        }

        return statuses;
    }

}
