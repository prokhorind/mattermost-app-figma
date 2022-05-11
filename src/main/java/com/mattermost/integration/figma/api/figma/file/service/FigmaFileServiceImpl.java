package com.mattermost.integration.figma.api.figma.file.service;

import com.mattermost.integration.figma.api.figma.file.dto.FigmaProjectFilesDTO;
import com.mattermost.integration.figma.input.oauth.InputPayload;
import com.mattermost.integration.figma.security.service.OAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FigmaFileServiceImpl implements FigmaFileService {

    private static final String FILES_URL = "https://api.figma.com/v1/projects/%s/files";


    @Autowired
    private OAuthService oAuthService;

    @Autowired
    @Qualifier("figmaRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public FigmaProjectFilesDTO getProjectFiles(InputPayload inputPayload) {
        String projectId = inputPayload.getValues().getProject().getValue();
        String refreshToken = inputPayload.getContext().getOauth2().getUser().getRefreshToken();
        String clientId = inputPayload.getContext().getOauth2().getClientId();
        String clientSecret = inputPayload.getContext().getOauth2().getClientSecret();
        String accessToken = oAuthService.refreshToken(clientId, clientSecret, refreshToken).getAccessToken();


        String url = String.format(FILES_URL, projectId);

        HttpHeaders headers = new HttpHeaders();

        headers.set("Authorization", String.format("Bearer %s", accessToken));
        HttpEntity<Object> request = new HttpEntity<>(headers);

        ResponseEntity<FigmaProjectFilesDTO> resp = restTemplate.exchange(url, HttpMethod.GET, request, FigmaProjectFilesDTO.class);
        return resp.getBody();
    }

}
