package com.mattermost.integration.figma.notification.service;

import com.mattermost.integration.figma.input.file.notification.FileCommentNotificationRequest;
import com.mattermost.integration.figma.provider.FigmaTokenProvider;
import com.mattermost.integration.figma.provider.NgrokLinkProvider;
import com.mattermost.integration.figma.security.dto.OAuthCredsDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FileNotificationService {
    private static final String FILE_NOTIFICATION_URL = "https://api.figma.com/v2/webhooks";
    private static final String PASSCODE = "Mattermost";
    private static final String FILE_COMMENT_EVENT_TYPE = "FILE_COMMENT";
    private static final String REDIRECT_URL = NgrokLinkProvider.REDIRECT_URL;
    private static final String WEBHOOK_URL = "/fileComment";

    private final RestTemplate restTemplate;

    public FileNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String subscribeToFileNotification(String teamId) {
        if (teamId != null && !teamId.isEmpty() && !teamId.isBlank()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", String.format("Bearer %s", FigmaTokenProvider.token.getAccessToken()));
            FileCommentNotificationRequest fileCommentNotificationRequest = new FileCommentNotificationRequest();
            fileCommentNotificationRequest.setEventType(FILE_COMMENT_EVENT_TYPE);
            fileCommentNotificationRequest.setTeamId(teamId);
            fileCommentNotificationRequest.setPasscode(PASSCODE);
            fileCommentNotificationRequest.setEndpoint(REDIRECT_URL.concat(WEBHOOK_URL));
            System.out.println(fileCommentNotificationRequest);
            HttpEntity<FileCommentNotificationRequest> request = new HttpEntity<>(fileCommentNotificationRequest, headers);
            restTemplate.postForEntity(FILE_NOTIFICATION_URL, request, String.class);
            return "{\"text\" : \"Success\"}";
        }
        return "{\"text\" : \"There is no such team id\"}";
    }
}
