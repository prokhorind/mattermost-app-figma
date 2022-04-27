package com.mattermost.integration.figma.api.figma.user.service;

public interface FileOwnerService {
    String findFileOwnerId(String fileKey, String figmaUserId, String mmSiteUrl, String botAccessToken);
}
