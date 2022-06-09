package com.mattermost.integration.figma.api.figma.user.service;

import com.mattermost.integration.figma.api.figma.user.service.dto.FileOwnerResponseDto;
import com.mattermost.integration.figma.api.figma.webhook.service.FigmaWebhookService;
import com.mattermost.integration.figma.api.mm.kv.KVService;
import com.mattermost.integration.figma.api.mm.kv.UserDataKVService;
import com.mattermost.integration.figma.security.dto.FigmaOAuthRefreshTokenResponseDTO;
import com.mattermost.integration.figma.security.dto.UserDataDto;
import com.mattermost.integration.figma.security.service.OAuthService;
import com.mattermost.integration.figma.utils.json.JsonUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class FileOwnerServiceImpl implements FileOwnerService {

    private static final String FIGMA_FILE_URL = "https://api.figma.com/v1/files/%s";
    private static final String OWNER = "owner";

    private final UserDataKVService userDataKVService;
    private final RestTemplate restTemplate;
    private final JsonUtils jsonUtils;
    private final OAuthService oAuthService;
    private final FigmaWebhookService figmaWebhookService;

    public FileOwnerServiceImpl(UserDataKVService userDataKVService, RestTemplate restTemplate, JsonUtils jsonUtils, OAuthService oAuthService, FigmaWebhookService figmaWebhookService) {
        this.userDataKVService = userDataKVService;
        this.restTemplate = restTemplate;
        this.jsonUtils = jsonUtils;
        this.oAuthService = oAuthService;
        this.figmaWebhookService = figmaWebhookService;
    }

    @Override
    public String findFileOwnerId(String fileKey, String webhookId, String figmaUserId, String mmSiteUrl, String botAccessToken) {
        String teamId = figmaWebhookService.getCurrentUserTeamId(webhookId, mmSiteUrl, botAccessToken);
        Set<String> userIds = userDataKVService.getUserIdsByTeamId(teamId, mmSiteUrl, botAccessToken);
        String fileOwnerId = checkIfSetContainsFileOwnerId(userIds, mmSiteUrl, botAccessToken, fileKey);
        if (Objects.isNull(fileOwnerId)) {
            Set<String> allUserIds = userDataKVService.getAllFigmaUserIds(mmSiteUrl, botAccessToken);
            allUserIds.removeAll(userIds);
            return checkIfSetContainsFileOwnerId(allUserIds, mmSiteUrl, botAccessToken, fileKey);
        }
        return fileOwnerId;
    }

    private String checkIfSetContainsFileOwnerId(Set<String> userIds, String mmSiteUrl, String botAccessToken, String fileKey) {
        for (String userId : userIds) {
            Optional<UserDataDto> currentUserData = userDataKVService.getUserData(userId, mmSiteUrl, botAccessToken);
            if (currentUserData.isEmpty()) {
                return null;
            }
            if (checkIfUserIsOwner(getToken(currentUserData.get()), fileKey)) {
                return userId;
            }
        }
        return null;
    }

    private boolean checkIfUserIsOwner(String figmaAccessToken, String fileKey) {
        FileOwnerResponseDto fileOwnerResponseDto = sendGetFileRequest(fileKey, figmaAccessToken);
        return OWNER.equals(fileOwnerResponseDto.getRole());
    }

    private FileOwnerResponseDto sendGetFileRequest(String fileKey, String figmaAccessToken) {
        HttpHeaders headers = new HttpHeaders();

        headers.set("Authorization", String.format("Bearer %s", figmaAccessToken));
        HttpEntity<Object> request = new HttpEntity<>(headers);

        ResponseEntity<String> resp = restTemplate.exchange(String.format(FIGMA_FILE_URL, fileKey), HttpMethod.GET, request, String.class);

        return (FileOwnerResponseDto) jsonUtils.convertStringToObject(resp.getBody(), FileOwnerResponseDto.class).get();
    }

    private String getToken(UserDataDto userDataDto) {
        String refreshToken = userDataDto.getRefreshToken();
        String clientId = userDataDto.getClientId();
        String clientSecret = userDataDto.getClientSecret();

        FigmaOAuthRefreshTokenResponseDTO refreshTokenDTO = oAuthService.refreshToken(clientId, clientSecret, refreshToken);
        return refreshTokenDTO.getAccessToken();
    }
}
