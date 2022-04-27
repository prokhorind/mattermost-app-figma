package com.mattermost.integration.figma.api.mm.dm.service;

import com.mattermost.integration.figma.api.figma.comment.service.CommentService;
import com.mattermost.integration.figma.api.figma.user.service.FileOwnerService;
import com.mattermost.integration.figma.api.mm.dm.component.DMFormMessageCreator;
import com.mattermost.integration.figma.api.mm.dm.dto.DMChannelPayload;
import com.mattermost.integration.figma.api.mm.dm.dto.DMMessageWithPropsFields;
import com.mattermost.integration.figma.api.mm.kv.UserDataKVService;
import com.mattermost.integration.figma.api.mm.user.MMUserService;
import com.mattermost.integration.figma.input.figma.notification.Comment;
import com.mattermost.integration.figma.input.figma.notification.CommentDto;
import com.mattermost.integration.figma.input.figma.notification.FigmaWebhookResponse;
import com.mattermost.integration.figma.input.figma.notification.Mention;
import com.mattermost.integration.figma.input.mm.user.MMUser;
import com.mattermost.integration.figma.input.oauth.ActingUser;
import com.mattermost.integration.figma.input.oauth.Context;
import com.mattermost.integration.figma.security.dto.FigmaOAuthRefreshTokenResponseDTO;
import com.mattermost.integration.figma.security.dto.UserDataDto;
import com.mattermost.integration.figma.security.service.OAuthService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.util.Strings.isBlank;

@Service
public class DMMessageSenderServiceImpl implements DMMessageSenderService {

    private static final String FILE_URL = "https://www.figma.com/file/%s";
    private static final String UNTITLED = "Untitled";
    private static final String REPLY_NOTIFICATION_ROOT = "replied to you on";
    private static final String AUTHOR_ID_MATCHED_COMMENTER_ID = "";
    private static final String FILE_OWNER_ID_MATCHED_COMMENTER_ID = "";
    private static final String COMMENTED_IN_YOUR_FILE = "commented in your file";

    private final MMUserService mmUserService;
    private final DMFormMessageCreator formMessageCreator;
    private final CommentService commentService;
    private final UserDataKVService userDataKVService;
    private final DMMessageService messageService;
    private final OAuthService oAuthService;
    private final FileOwnerService fileOwnerService;

    public DMMessageSenderServiceImpl(MMUserService mmUserService, DMFormMessageCreator formMessageCreator,
                                      CommentService commentService, UserDataKVService userDataKVService,
                                      DMMessageService messageService, OAuthService oAuthService, FileOwnerService fileOwnerService) {
        this.mmUserService = mmUserService;
        this.formMessageCreator = formMessageCreator;
        this.commentService = commentService;
        this.userDataKVService = userDataKVService;
        this.messageService = messageService;
        this.oAuthService = oAuthService;
        this.fileOwnerService = fileOwnerService;
    }

    public String sendMessageToCommentAuthor(FigmaWebhookResponse figmaWebhookResponse, Context context, String fileOwnerId) {
        String token = getToken(figmaWebhookResponse, context);
        CommentDto comment = commentService.getCommentById(figmaWebhookResponse.getParentId(),
                figmaWebhookResponse.getFileKey(), token).get();
        if (figmaWebhookResponse.getTriggeredBy().getId().equals(comment.getUser().getId())) {
            return AUTHOR_ID_MATCHED_COMMENTER_ID;
        }

        if (!fileOwnerId.equals(comment.getUser().getId())) {
            UserDataDto currentUserData = userDataKVService.getUserData(comment.getUser().getId(),
                    context.getMattermostSiteUrl(), context.getBotAccessToken());
            sendMessageToSpecificReceiver(context, currentUserData, figmaWebhookResponse, REPLY_NOTIFICATION_ROOT);
            return comment.getUser().getId();
        }
        return fileOwnerId;
    }

    public String sendMessageToFileOwner(FigmaWebhookResponse figmaWebhookResponse, Context context) {
        String fileOwnerId = fileOwnerService.findFileOwnerId(figmaWebhookResponse.getFileKey(), figmaWebhookResponse.getTriggeredBy().getId(),
                context.getMattermostSiteUrl(), context.getBotAccessToken());
        if (!fileOwnerId.equals(figmaWebhookResponse.getTriggeredBy().getId())) {
            UserDataDto fileOwnerData = userDataKVService.getUserData(fileOwnerId, context.getMattermostSiteUrl(), context.getBotAccessToken());
            sendMessageToSpecificReceiver(context, fileOwnerData, figmaWebhookResponse, COMMENTED_IN_YOUR_FILE);
            return fileOwnerId;
        }
        return FILE_OWNER_ID_MATCHED_COMMENTER_ID;
    }

    public void sendMessageToSpecificReceiver(Context context, UserDataDto specificUserData,
                                              FigmaWebhookResponse figmaWebhookResponse, String notificationMessageRoot) {
        context.setActingUser(new ActingUser());
        context.getActingUser().setId(specificUserData.getMmUserId());
        String channelId = messageService.createDMChannel(createDMChannelPayload(context));

        DMMessageWithPropsFields messageWithPropsFields = getMessageWithPropsFields(context, figmaWebhookResponse,
                channelId, notificationMessageRoot);
        messageService.sendDMMessage(formMessageCreator.createDMMessageWithPropsPayload(messageWithPropsFields, context.getBotAccessToken(),
                context.getMattermostSiteUrl()));
    }

    private String getToken(FigmaWebhookResponse figmaWebhookResponse, Context context) {
        String mmSiteUrl = context.getMattermostSiteUrl();
        String botAccessToken = context.getBotAccessToken();
        UserDataDto userDataDto = userDataKVService.getUserData(figmaWebhookResponse.getTriggeredBy().getId(),
                mmSiteUrl, botAccessToken);
        FigmaOAuthRefreshTokenResponseDTO figmaOAuthRefreshTokenResponseDTO =
                oAuthService.refreshToken(userDataDto.getClientId(), userDataDto.getClientSecret(), userDataDto.getRefreshToken());
        return figmaOAuthRefreshTokenResponseDTO.getAccessToken();
    }

    private DMMessageWithPropsFields getMessageWithPropsFields(Context context, FigmaWebhookResponse figmaWebhookResponse,
                                                               String channelId, String notificationMessageRoot) {
        String mmSiteUrl = context.getMattermostSiteUrl();
        String botAccessToken = context.getBotAccessToken();

        UserDataDto userDataDto = userDataKVService.getUserData(figmaWebhookResponse.getTriggeredBy().getId(), mmSiteUrl, botAccessToken);
        MMUser mmUsers = mmUserService.getUserById(userDataDto.getMmUserId(), mmSiteUrl, botAccessToken);

        DMMessageWithPropsFields msg = new DMMessageWithPropsFields();
        msg.setAppId(context.getAppId());
        msg.setLabel(buildLabel(mmUsers.getUsername(), figmaWebhookResponse.getFileName(),
                figmaWebhookResponse.getFileKey(), notificationMessageRoot));
        msg.setChannelId(channelId);
        msg.setDescription(buildComment(figmaWebhookResponse.getComment(),
                figmaWebhookResponse.getMentions(), mmSiteUrl, botAccessToken));
        msg.setReplyFileId(figmaWebhookResponse.getFileKey());
        msg.setReplyCommentId(isBlank(figmaWebhookResponse.getParentId()) ? figmaWebhookResponse.getCommentId() : figmaWebhookResponse.getParentId());
        return msg;
    }

    private String buildLabel(String author, String fileName, String fileKey, String root) {
        if (fileName.isBlank()) {
            fileName = UNTITLED;
        }
        return String.format("@%s %s [%s](%s):", author, root, fileName, String.format(FILE_URL, fileKey));
    }

    private String buildComment(List<Comment> comments, List<Mention> mentions, String mmSiteUrl,
                                String botToken) {
        List<UserDataDto> userData = mentions.stream().map(mention ->
                userDataKVService.getUserData(mention.getId(), mmSiteUrl, botToken)).collect(Collectors.toList());
        List<String> userIds = userData.stream().map(UserDataDto::getMmUserId).collect(Collectors.toList());
        List<MMUser> mmUsers = new ArrayList<>();
        if (!userIds.isEmpty()) {
            mmUsers = getMMUsersByIds(userIds, mmSiteUrl, botToken);
        }

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

    private DMChannelPayload createDMChannelPayload(Context context) {
        String botAccessToken = context.getBotAccessToken();
        String botUserId = context.getBotUserId();
        String userId = context.getActingUser().getId();
        String mattermostSiteUrl = context.getMattermostSiteUrl();
        return new DMChannelPayload(userId, botUserId, botAccessToken, mattermostSiteUrl);
    }

}