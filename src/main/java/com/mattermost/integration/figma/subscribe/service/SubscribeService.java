package com.mattermost.integration.figma.subscribe.service;

import com.mattermost.integration.figma.input.oauth.Context;
import com.mattermost.integration.figma.input.oauth.InputPayload;

import java.util.Set;

public interface SubscribeService {
    void subscribeToFile(InputPayload payload);

    void sendSubscriptionFilesToMMChannel(InputPayload payload);

    void unsubscribeFromFile(InputPayload payload, String fileKey);

    Set<String> getMMChannelIdsByFileId(Context context, String fileKey);

    boolean isBotExistsInChannel(InputPayload payload);

    Set<String> getMMChannelIdsByProjectId(Context context, String projectId);

    void unsubscribeFromProject(InputPayload payload, String projectId);

    void subscribeToProject(InputPayload payload);
}
