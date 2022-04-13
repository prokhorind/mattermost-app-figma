package com.mattermost.integration.figma.notification.service;

import com.mattermost.integration.figma.api.figma.webhook.dto.TeamWebhookInfoResponseDto;
import com.mattermost.integration.figma.api.figma.webhook.service.FigmaWebhookService;
import com.mattermost.integration.figma.api.mm.dm.dto.*;
import com.mattermost.integration.figma.api.mm.dm.service.DMMessageService;
import com.mattermost.integration.figma.api.mm.kv.KVService;
import com.mattermost.integration.figma.api.mm.user.MMUserService;
import com.mattermost.integration.figma.input.figma.notification.*;
import com.mattermost.integration.figma.input.mm.MMUser;
import com.mattermost.integration.figma.input.oauth.ActingUser;
import com.mattermost.integration.figma.input.oauth.Context;
import com.mattermost.integration.figma.input.oauth.InputPayload;
import com.mattermost.integration.figma.security.dto.FigmaOAuthRefreshTokenResponseDTO;
import com.mattermost.integration.figma.security.dto.UserDataDto;
import com.mattermost.integration.figma.security.service.OAuthService;
import com.mattermost.integration.figma.utils.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.util.Strings.isBlank;

@Service
@Slf4j
public class FileNotificationService {
    private static final String BASE_WEBHOOK_URL = "https://api.figma.com/v2/webhooks";
    private static final String PASSCODE = "Mattermost";
    private static final String FILE_COMMENT_EVENT_TYPE = "FILE_COMMENT";
    private static final String REDIRECT_URL = "%s%s?secret=%s";
    private static final String FILE_COMMENT_URL = "/webhook/comment";
    private static final String FILE_URL = "https://www.figma.com/file/%s";
    private static final String UNTITLED = "Untitled";

    private final RestTemplate restTemplate;
    private final DMMessageService messageService;
    private final KVService kvService;
    private final JsonUtils jsonUtils;
    private final FigmaWebhookService figmaWebhookService;
    private final OAuthService oAuthService;
    private final MMUserService mmUserService;

    public FileNotificationService(RestTemplate restTemplate, DMMessageService messageService, KVService kvService, JsonUtils jsonUtils, FigmaWebhookService figmaWebhookService, OAuthService oAuthService, MMUserService mmUserService) {
        this.restTemplate = restTemplate;
        this.messageService = messageService;
        this.kvService = kvService;
        this.jsonUtils = jsonUtils;
        this.figmaWebhookService = figmaWebhookService;
        this.oAuthService = oAuthService;
        this.mmUserService = mmUserService;
    }

    public SubscribeToFileNotification subscribeToFileNotification(InputPayload inputPayload) {
        String teamId = inputPayload.getValues().getTeamId();
        if (teamId == null || teamId.isEmpty() || teamId.isBlank()) {
            return SubscribeToFileNotification.BAD_TEAM_ID;
        }

        String accessToken = getToken(inputPayload);

        if (hasFileCommentWebhook(teamId, accessToken)) {
            return SubscribeToFileNotification.WEBHOOK_ALREADY_EXISTS;
        }
        HttpEntity<FileCommentNotificationRequest> request = createFileCommentNotificationRequest(inputPayload, accessToken);
        log.debug("File notification request : " + request);
        log.info("Sending comment request for team with id: " + teamId);
        restTemplate.postForEntity(BASE_WEBHOOK_URL, request, String.class);
        return SubscribeToFileNotification.SUBSCRIBED;
    }

    private String getToken(InputPayload inputPayload) {
        String refreshToken = inputPayload.getContext().getOauth2().getUser().getRefreshToken();
        String clientId = inputPayload.getContext().getOauth2().getClientId();
        String clientSecret = inputPayload.getContext().getOauth2().getClientSecret();

        FigmaOAuthRefreshTokenResponseDTO refreshTokenDTO = oAuthService.refreshToken(clientId, clientSecret, refreshToken);
        String accessToken = refreshTokenDTO.getAccessToken();
        return accessToken;
    }

    public void saveUserData(InputPayload inputPayload) {
        String userId = inputPayload.getContext().getOauth2().getUser().getUserId();
        String mmSiteUrl = inputPayload.getContext().getMattermostSiteUrl();
        String botAccessToken = inputPayload.getContext().getBotAccessToken();
        String teamId = inputPayload.getValues().getTeamId();

        UserDataDto currentData = getCurrentUserData(userId, mmSiteUrl, botAccessToken);
        if (Objects.nonNull(currentData.getTeamIds()) && !currentData.getTeamIds().isEmpty()) {
            currentData.getTeamIds().add(teamId);
            kvService.put(userId, currentData, mmSiteUrl, botAccessToken);
        } else {
            UserDataDto newUserData = new UserDataDto();
            newUserData.setTeamIds(new HashSet<>(Collections.singletonList(teamId)));
            newUserData.setMmUserId(inputPayload.getContext().getActingUser().getId());
            kvService.put(userId, newUserData, mmSiteUrl, botAccessToken);
        }
    }

    public void sendFileNotificationMessageToMM(FileCommentWebhookResponse fileCommentWebhookResponse) {
        FigmaWebhookResponse figmaWebhookResponse = fileCommentWebhookResponse.getValues().getData();
        Context context = fileCommentWebhookResponse.getContext();
        if (!figmaWebhookResponse.getMentions().isEmpty()) {
            figmaWebhookResponse.getMentions().stream().distinct().map((mention ->
                            getCurrentUserData(mention.getId(), context.getMattermostSiteUrl(), context.getBotAccessToken())))
                    .forEach(userData -> sendMessageToSpecificReceiver(context, userData, figmaWebhookResponse));
        }
    }

    private void sendMessageToSpecificReceiver(Context context, UserDataDto specificUserData, FigmaWebhookResponse figmaWebhookResponse) {
        context.setActingUser(new ActingUser());
        context.getActingUser().setId(specificUserData.getMmUserId());
        String channelId = messageService.createDMChannel(createDMChannelPayload(context));

        DMMessageWithPropsFields messageWithPropsFields = getMessageWithPropsFields(context, figmaWebhookResponse, channelId);
        messageService.sendDMMessage(createDMMessageWithPropsPayload(messageWithPropsFields, context.getBotAccessToken(), context.getMattermostSiteUrl()));
    }

    private DMMessageWithPropsFields getMessageWithPropsFields(Context context, FigmaWebhookResponse figmaWebhookResponse, String channelId) {
        DMMessageWithPropsFields msg = new DMMessageWithPropsFields();
        msg.setAppId(context.getAppId());
        msg.setLabel("my label");
        msg.setChannelId(channelId);
        msg.setDescription("my description");
        msg.setReplyFileId(figmaWebhookResponse.getFileKey());
        msg.setReplyCommentId(isBlank(figmaWebhookResponse.getParentId()) ? figmaWebhookResponse.getCommentId() : figmaWebhookResponse.getParentId());
        return msg;
    }

    private boolean hasFileCommentWebhook(String teamId, String figmaToken) {
        TeamWebhookInfoResponseDto teamWebhooks = figmaWebhookService.getTeamWebhooks(teamId, figmaToken);
        teamWebhooks.getWebhooks().forEach(webhook -> figmaWebhookService.deleteWebhook(webhook.getId(), figmaToken));
        return teamWebhooks.getWebhooks().stream().anyMatch(webhook -> webhook.getEventType().equals(FILE_COMMENT_EVENT_TYPE));
    }

    private DMChannelPayload createDMChannelPayload(Context context) {
        String botAccessToken = context.getBotAccessToken();
        String botUserId = context.getBotUserId();
        String userId = context.getActingUser().getId();
        String mattermostSiteUrl = context.getMattermostSiteUrl();
        return new DMChannelPayload(userId, botUserId, botAccessToken, mattermostSiteUrl);
    }

    private DMMessagePayload createDMMessagePayload(String channelId, String botAccessToken,
                                                    String mmSiteUrl, FigmaWebhookResponse figmaWebhookResponse) {
        DMMessagePayload message = new DMMessagePayload();
        message.setChannelId(channelId);
        message.setMessage(createFileNotificationMessage(figmaWebhookResponse, mmSiteUrl, botAccessToken));
        message.setToken(botAccessToken);
        message.setMmSiteUrlBase(mmSiteUrl);
        return message;
    }

    private DMMessageWithPropsPayload createDMMessageWithPropsPayload(DMMessageWithPropsFields fields, String botAccessToken,
                                                                      String mmSiteUrl) {

        String body = messageService.getMessageWithReplyButton(fields);
        DMMessageWithPropsPayload payload = new DMMessageWithPropsPayload();
        payload.setBody(body);
        payload.setMmSiteUrl(mmSiteUrl);
        payload.setToken(botAccessToken);
        return payload;
    }

    private UserDataDto getCurrentUserData(String userId, String mmSiteUrl, String botAccessToken) {
        return (UserDataDto) jsonUtils.convertStringToObject(kvService.get(userId, mmSiteUrl,
                botAccessToken), UserDataDto.class).get();
    }

    private String createFileNotificationMessage(FigmaWebhookResponse figmaWebhookResponse, String mmSiteUrl,
                                                 String botToken) {
        UserDataDto userDataDto = getCurrentUserData(figmaWebhookResponse.getTriggeredBy().getId(), mmSiteUrl, botToken);
        MMUser mmUsers = mmUserService.getUserById(userDataDto.getMmUserId(), mmSiteUrl, botToken);
        return buildComment(mmUsers.getUsername(), figmaWebhookResponse.getFileName(),
                prepareComment(figmaWebhookResponse.getComment(), figmaWebhookResponse.getMentions(), mmSiteUrl, botToken),
                figmaWebhookResponse.getFileKey());
    }

    private String buildComment(String author, String fileName, String comment, String fileKey) {
        if (fileName.isBlank()) {
            fileName = UNTITLED;
        }
        return String.format("@%s commented on [%s](%s):\n \"%s\"", author,
                fileName, String.format(FILE_URL, fileKey), comment);
    }

    private String prepareComment(List<Comment> comments, List<Mention> mentions, String mmSiteUrl,
                                  String botToken) {
        List<UserDataDto> userData = mentions.stream().map(mention ->
                getCurrentUserData(mention.getId(), mmSiteUrl, botToken)).collect(Collectors.toList());
        List<MMUser> mmUsers = getMMUsersByIds(userData.stream().map(UserDataDto::getMmUserId).collect(Collectors.toList()),
                mmSiteUrl, botToken);

        StringBuilder stringBuilder = new StringBuilder();
        int mentionCounter = 0;
        for (Comment comment : comments) {
            if (Objects.nonNull(comment.getText())) {
                stringBuilder.append(comment.getText());
            } else {
                stringBuilder.append("@").append(mmUsers.get(mentionCounter++).getUsername());
            }
        }

        return stringBuilder.toString();
    }

    private List<MMUser> getMMUsersByIds(List<String> ids, String mmSiteUrl, String botAccessToken) {
        return mmUserService.getUsersById(ids, mmSiteUrl, botAccessToken);
    }

    private HttpEntity<FileCommentNotificationRequest> createFileCommentNotificationRequest(InputPayload inputPayload, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", String.format("Bearer %s", token));

        FileCommentNotificationRequest fileCommentNotificationRequest = new FileCommentNotificationRequest();
        fileCommentNotificationRequest.setEventType(FILE_COMMENT_EVENT_TYPE);
        fileCommentNotificationRequest.setTeamId(inputPayload.getValues().getTeamId());
        fileCommentNotificationRequest.setPasscode(PASSCODE);
        fileCommentNotificationRequest.setEndpoint(String.format(
                inputPayload.getContext().getMattermostSiteUrl().concat(REDIRECT_URL),
                inputPayload.getContext().getAppPath(), FILE_COMMENT_URL,
                inputPayload.getContext().getApp().getWebhookSecret()));

        return new HttpEntity<>(fileCommentNotificationRequest, headers);
    }
}
