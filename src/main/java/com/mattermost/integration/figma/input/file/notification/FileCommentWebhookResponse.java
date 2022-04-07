package com.mattermost.integration.figma.input.file.notification;

import com.mattermost.integration.figma.input.oauth.Context;
import lombok.Data;

@Data
public class FileCommentWebhookResponse {
    public String path;
    public FigmaValues values;
    public Context context;
}
