package com.mattermost.integration.figma.input.figma.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClientMeta {
    @JsonProperty("node_id")
    public String nodeId;
    @JsonProperty("node_offset")
    public NodeOffset nodeOffset;
}
