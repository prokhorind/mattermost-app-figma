package com.mattermost.integration.figma.input.file.notification;

import lombok.Data;

@Data
public class FigmaValues {
    public FigmaWebhookResponse data;
    public Headers headers;
    public String httpMethod;
    public String rawQuery;
}
