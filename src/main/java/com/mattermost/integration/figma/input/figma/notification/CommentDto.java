package com.mattermost.integration.figma.input.figma.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Date;

@Data
public class CommentDto {
    public String id;
    @JsonProperty("file_key")
    public String fileKey;
    @JsonProperty("parent_id")
    public String parentId;
    public User user;
    @JsonProperty("created_at")
    public Date createdAt;
    @JsonProperty("resolved_at")
    public Object resolvedAt;
    public String message;
    @JsonProperty("client_meta")
    public ClientMeta clientMeta;
    @JsonProperty("order_id")
    public String orderId;
}
