package com.atlasflow.message.config;

public class RabbitNames {

    public static final String EVENTS_EXCHANGE =
            "atlasflow.events";
    public static final String RETRY_EXCHANGE =
            "atlasflow.events.retry";
    public static final String DLQ_EXCHANGE =
            "atlasflow.events.dlx";

    public static final String JOB_SUBMITTED_RETRY_5S_QUEUE =
            "atlasflow.job.submitted.retry.5s";

    public static final String JOB_SUBMITTED_RETRY_30S_QUEUE =
            "atlasflow.job.submitted.retry.30s";

    public static final String JOB_SUBMITTED_QUEUE =
            "atlasflow.job.submitted";

    public static final String JOB_SUBMITTED_RETRY_QUEUE =
            "atlasflow.job.submitted.retry";

    public static final String JOB_SUBMITTED_DLQ =
            "atlasflow.job.submitted.dlq";

    public static final String JOB_SUBMITTED_ROUTING_KEY =
            "job.submitted";
    public static final String JOB_SUBMITTED_DLQ_ROUTING_KEY =
            "job.submitted.dlq";

    public static final String RETRY_5S_ROUTING_KEY =
            "job.submitted.retry.5s";

    public static final String RETRY_30S_ROUTING_KEY =
            "job.submitted.retry.30s";

    public static final String DLQ_ROUTING_KEY =
            "job.submitted.dlq";

    public static final String JOB_SUBMITTED_RETRY_2M_QUEUE =
            "atlasflow.job.submitted.retry.2m";

    public static final String RETRY_2M_ROUTING_KEY =
            "job.submitted.retry.2m";

    private RabbitNames() {
    }

}
