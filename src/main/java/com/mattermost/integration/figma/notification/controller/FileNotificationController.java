package com.mattermost.integration.figma.notification.controller;

import com.mattermost.integration.figma.input.file.notification.FileCommentWebhookResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileNotificationController {
    @PostMapping("/fileComment")
    public void getUserFileNotification(@RequestBody FileCommentWebhookResponse response) {
        System.out.println(response);
    }
}
