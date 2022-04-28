package com.mattermost.integration.figma.api.mm.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mattermost.integration.figma.input.oauth.InputPayload;
import com.mattermost.integration.figma.security.dto.UserDataDto;
import com.mattermost.integration.figma.security.service.DataEncryptionService;
import com.mattermost.integration.figma.utils.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class UserDataKVServiceImpl implements UserDataKVService {
    private final KVService kvService;
    private final JsonUtils jsonUtils;
    private final DataEncryptionService dataEncryptionService;

    public UserDataKVServiceImpl(KVService kvService, JsonUtils jsonUtils, DataEncryptionService dataEncryptionService) {
        this.kvService = kvService;
        this.jsonUtils = jsonUtils;
        this.dataEncryptionService = dataEncryptionService;
    }

    public UserDataDto getUserData(String userId, String mmSiteUrl, String botAccessToken) {
        UserDataDto currentUserData = (UserDataDto) jsonUtils.convertStringToObject(kvService.get(userId, mmSiteUrl,
                botAccessToken), UserDataDto.class).get();
        try {
            currentUserData.setClientSecret(dataEncryptionService.decrypt(currentUserData.getClientSecret()));
            currentUserData.setRefreshToken(dataEncryptionService.decrypt(currentUserData.getRefreshToken()));
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            log.error(e.getMessage());
        }
        return currentUserData;
    }

    public Set<String> getUserIdsByTeamId(String teamId, String mmSiteUrl, String botAccessToken) {
        return (Set<String>) jsonUtils.convertStringToObject(kvService.get(teamId, mmSiteUrl,
                botAccessToken), new TypeReference<Set<String>>(){}).orElse(null);
    }


    @Override
    public void saveUserData(InputPayload inputPayload) {
        String userId = inputPayload.getContext().getOauth2().getUser().getUserId();
        String mmSiteUrl = inputPayload.getContext().getMattermostSiteUrl();
        String botAccessToken = inputPayload.getContext().getBotAccessToken();
        String teamId = inputPayload.getValues().getTeamId();

        UserDataDto currentData = getUserData(userId, mmSiteUrl, botAccessToken);
        if (Objects.nonNull(currentData.getTeamIds()) && !currentData.getTeamIds().isEmpty()) {
            currentData.getTeamIds().add(teamId);
            updateUserData(inputPayload, currentData);
        } else {
            UserDataDto newUserData = new UserDataDto();
            newUserData.setTeamIds(new HashSet<>(Collections.singletonList(teamId)));
            updateUserData(inputPayload, newUserData);
        }

        Set<String> userIds = getUserIdsByTeamId(teamId, mmSiteUrl, botAccessToken);

        if (Objects.isNull(userIds)) {
            userIds = new HashSet<>();
        }
        userIds.add(userId);
        kvService.put(teamId, userIds, mmSiteUrl, botAccessToken);
    }

    private void updateUserData(InputPayload inputPayload, UserDataDto currentData) {
        String userId = inputPayload.getContext().getOauth2().getUser().getUserId();
        String mmSiteUrl = inputPayload.getContext().getMattermostSiteUrl();
        String botAccessToken = inputPayload.getContext().getBotAccessToken();

        currentData.setClientId(inputPayload.getContext().getOauth2().getClientId());
        try {
            currentData.setClientSecret(dataEncryptionService.encrypt(inputPayload.getContext().getOauth2().getClientSecret()));
            currentData.setRefreshToken(dataEncryptionService.encrypt(inputPayload.getContext().getOauth2().getUser().getRefreshToken()));
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            log.error(e.getMessage());
        }

        currentData.setMmUserId(inputPayload.getContext().getActingUser().getId());

        kvService.put(userId, currentData, mmSiteUrl, botAccessToken);
    }
}
