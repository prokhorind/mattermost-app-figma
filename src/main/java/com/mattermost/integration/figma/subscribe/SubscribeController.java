package com.mattermost.integration.figma.subscribe;


import com.mattermost.integration.figma.api.figma.file.dto.FigmaProjectFilesDTO;
import com.mattermost.integration.figma.api.figma.file.service.FigmaFileService;
import com.mattermost.integration.figma.api.figma.notification.service.FileNotificationService;
import com.mattermost.integration.figma.api.figma.project.dto.TeamProjectDTO;
import com.mattermost.integration.figma.api.figma.project.service.FigmaProjectService;
import com.mattermost.integration.figma.api.mm.dm.component.ProjectFormReplyCreator;
import com.mattermost.integration.figma.api.mm.kv.UserDataKVService;
import com.mattermost.integration.figma.api.mm.kv.dto.FileInfo;
import com.mattermost.integration.figma.api.mm.user.MMUserService;
import com.mattermost.integration.figma.config.exception.exceptions.figma.FigmaNoFilesInProjectSubscriptionException;
import com.mattermost.integration.figma.config.exception.exceptions.figma.FigmaNoProjectsInTeamSubscriptionException;
import com.mattermost.integration.figma.config.exception.exceptions.mm.MMFigmaUserNotSavedException;
import com.mattermost.integration.figma.config.exception.exceptions.mm.MMSubscriptionFromDMChannelException;
import com.mattermost.integration.figma.config.exception.exceptions.mm.MMSubscriptionInChannelWithoutBotException;
import com.mattermost.integration.figma.input.mm.form.FormType;
import com.mattermost.integration.figma.input.mm.form.MMStaticSelectField;
import com.mattermost.integration.figma.input.oauth.InputPayload;
import com.mattermost.integration.figma.input.oauth.OAuth2;
import com.mattermost.integration.figma.input.oauth.User;
import com.mattermost.integration.figma.subscribe.service.SubscribeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RestController
@Slf4j
public class SubscribeController {
    @Autowired
    private FileNotificationService fileNotificationService;
    @Autowired
    private SubscribeService subscribeService;
    @Autowired
    private UserDataKVService userDataKVService;
    @Autowired
    private FigmaProjectService figmaProjectService;
    @Autowired
    private FigmaFileService figmaFileService;
    @Autowired
    private MMUserService mmUserService;

    private String ALL_FILES = "all_files";

    @PostMapping("/subscribe")
    public FormType subscribe(@RequestBody InputPayload request) {
        log.debug(request.toString());
        if ("D".equalsIgnoreCase(request.getContext().getChannel().getType())) {
            log.error("Subscription from DM channel:" + request);
            throw new MMSubscriptionFromDMChannelException();
        }

        if (!subscribeService.isBotExistsInTeam(request)) {
            log.info("Add Figma bot to team:" + request);
            String teamId = request.getContext().getChannel().getTeamId();
            String actingUserAccessToken = request.getContext().getActingUserAccessToken();
            String botUserId = request.getContext().getBotUserId();
            String mattermostSiteUrl = request.getContext().getMattermostSiteUrl();
            mmUserService.addUserToTeam(teamId, botUserId, mattermostSiteUrl, actingUserAccessToken);

        }

        if (!subscribeService.isBotExistsInChannel(request)) {
            log.info("Add Figma bot to channel:" + request);
            String channelId = request.getContext().getChannel().getId();
            String actingUserAccessToken = request.getContext().getActingUserAccessToken();
            String botUserId = request.getContext().getBotUserId();
            String mattermostSiteUrl = request.getContext().getMattermostSiteUrl();
            mmUserService.addUserToChannel(channelId, botUserId, mattermostSiteUrl, actingUserAccessToken);
        }

        if (!isFigmaUserStored(request)) {
            log.error("Figma user was not stored:" + request);
            throw new MMFigmaUserNotSavedException();
        }

        System.out.println(request);
        log.info("Subscription to file comment from user with id: " + request.getContext().getUserAgent() + " has come");
        log.debug("Subscription to file comment request: " + request);

        TeamProjectDTO projects = figmaProjectService.getProjectsByTeamId(request);

        if (projects.getProjects().isEmpty()) {
            throw new FigmaNoProjectsInTeamSubscriptionException();
        }

        String teamId = request.getValues().getTeamId();
        ProjectFormReplyCreator projectFormReplyCreator = new ProjectFormReplyCreator();

        return projectFormReplyCreator.create(projects, teamId);
    }

    @PostMapping("{teamId}/projects")
    public Object sendProjectFiles(@RequestBody InputPayload request, @PathVariable String teamId) {
        log.debug(request.toString());
        if (ALL_FILES.equals(request.getValues().getFile().getValue())) {
            request.getValues().setTeamId(teamId);
            fileNotificationService.createTeamWebhook(request);
            userDataKVService.saveUserData(request);
            subscribeService.subscribeToProject(request);
            String projectName = request.getValues().getProject().getLabel();
            return String.format("{\"text\":\"You’ve successfully subscribed [channel] to %s notifications\"}", projectName);
        }

        return sendProjectFile(request, teamId);
    }

    @PostMapping("{teamId}/projectFiles")
    public Object sendProjectFileSelection(@RequestBody InputPayload request, @PathVariable String teamId) {
        FigmaProjectFilesDTO projectFiles = figmaFileService.getProjectFiles(request);

        if (projectFiles.getFiles().isEmpty()) {
            throw new FigmaNoFilesInProjectSubscriptionException();
        }

        request.getValues().setTeamId(teamId);
        TeamProjectDTO projects = figmaProjectService.getProjectsByTeamId(request);

        ProjectFormReplyCreator projectFormReplyCreator = new ProjectFormReplyCreator();
        MMStaticSelectField project = request.getValues().getProject();
        FormType formType = projectFormReplyCreator.create(projects, teamId);
        projectFormReplyCreator.addFilesToForm(projectFiles.getFiles(), formType, project.getLabel(), project.getValue());
        return formType;
    }

    private String sendProjectFile(@RequestBody InputPayload request, String teamId) {
        log.debug(request.toString());
        String fileKey = request.getValues().getFile().getValue();
        request.getValues().setTeamId(teamId);
        log.info("Get files: " + request.getValues().getFile().getValue() + " has come");
        log.debug("Get files request: " + request);

        Set<FileInfo> filesByChannelId = subscribeService.getFilesByChannelId(request);
        Optional<FileInfo> file = filesByChannelId.stream().filter(f -> fileKey.equals(f.getFileId())).findAny();

        if (file.isPresent()) {
            return String.format("{\"text\":\"This channel is already subscribed to updates about %s\"}", file.get().getFileName());
        }

        fileNotificationService.createTeamWebhook(request);
        userDataKVService.saveUserData(request);
        subscribeService.subscribeToFile(request);
        String fileName = request.getValues().getFile().getLabel();
        return String.format("{\"text\":\"You’ve successfully subscribed [channel] to %s notifications\"}", fileName);
    }

    @PostMapping("/subscriptions")
    public String sendChannelSubscriptions(@RequestBody InputPayload request) {
        System.out.println(request);
        log.info("Get Subscriptions for channel: " + request.getContext().getChannel().getId() + " has come");
        log.debug("Get files request: " + request);


        if (!subscribeService.isBotExistsInTeam(request)) {
            throw new MMSubscriptionInChannelWithoutBotException("Please add Figma bot to this team");
        }

        if (!subscribeService.isBotExistsInChannel(request)) {
            throw new MMSubscriptionInChannelWithoutBotException("Please add Figma bot to this channel");
        }

        if (!isFigmaUserStored(request)) {
            log.error("Figma user was not stored:" + request);
            throw new MMFigmaUserNotSavedException();
        }

        subscribeService.sendSubscriptionFilesToMMChannel(request);
        return "{\"type\":\"ok\"}";
    }

    @PostMapping("/project-files/file/{fileId}/remove")
    public String unsubscribe(@PathVariable String fileId, @RequestBody InputPayload request) {
        subscribeService.unsubscribeFromFile(request, fileId);
        return "{\"text\":\"Unsubscribed\"}";
    }

    @PostMapping("/project/{projectId}/remove")
    public String unsubscribeFromProject(@PathVariable String projectId, @RequestBody InputPayload request) {
        subscribeService.unsubscribeFromProject(request, projectId);
        return "{\"text\":\"Unsubscribed\"}";
    }


    private boolean isFigmaUserStored(InputPayload payload) {
        OAuth2 oauth2 = payload.getContext().getOauth2();
        boolean hasNoOauth = Objects.isNull(oauth2);
        if (hasNoOauth) {
            return false;
        }
        User oauth2User = oauth2.getUser();
        boolean hasNoUser = Objects.isNull(oauth2User);
        if (hasNoUser) {
            return false;
        }

        boolean hasBlankRefreshToken = StringUtils.isBlank(oauth2User.getRefreshToken());
        if (hasBlankRefreshToken) {
            return false;
        }

        boolean hasBlankClient = StringUtils.isBlank(oauth2.getClientId());
        if (hasBlankClient) {
            return false;
        }

        boolean hasBlankSecret = StringUtils.isBlank(oauth2.getClientSecret());
        if (hasBlankSecret) {
            return false;
        }
        return true;
    }
}
