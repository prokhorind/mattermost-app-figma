package com.mattermost.integration.figma.webhook;


import com.mattermost.integration.figma.input.InputPayload;
import com.mattermost.integration.figma.notification.service.FileNotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final FileNotificationService fileNotificationService;

    public WebhookController(FileNotificationService fileNotificationService) {
        this.fileNotificationService = fileNotificationService;
    }

    @PostMapping("/comment")
    public void comment(@RequestBody String request) {
        System.out.println("Got it "+ request);
    }

    @PostMapping("/subscribe/fileComment")
    public String subscribeToFileComment(@RequestBody InputPayload request) {
        System.out.println(request);
        return fileNotificationService.subscribeToFileNotification(request.getValues().getTeamId());
    }
}
