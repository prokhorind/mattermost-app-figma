package com.mattermost.integration.figma.input.file.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Headers {
    @JsonProperty("Accept-Encoding")
    public String acceptEncoding;
    @JsonProperty("Content-Length")
    public String contentLength;
    @JsonProperty("Content-Type")
    public String contentType;
    @JsonProperty("Mattermost-Session-Id")
    public String mattermostSessionId;
    @JsonProperty("User-Agent")
    public String userAgent;
    @JsonProperty("X-Datadog-Parent-Id")
    public String xDatadogParentId;
    @JsonProperty("X-Datadog-Sampling-Priority")
    public String xDatadogSamplingPriority;
    @JsonProperty("X-Datadog-Trace-Id")
    public String xDatadogTraceId;
    @JsonProperty("X-Forwarded-For")
    public String xForwardedFor;
    @JsonProperty("X-Forwarded-Proto")
    public String xForwardedProto;
}
