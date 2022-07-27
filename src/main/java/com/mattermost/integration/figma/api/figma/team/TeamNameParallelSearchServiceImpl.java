package com.mattermost.integration.figma.api.figma.team;

import com.mattermost.integration.figma.api.figma.project.dto.TeamProjectDTO;
import com.mattermost.integration.figma.api.figma.project.service.FigmaProjectService;
import com.mattermost.integration.figma.api.figma.team.dto.TeamNameDto;
import com.mattermost.integration.figma.api.mm.kv.UserDataKVService;
import com.mattermost.integration.figma.security.dto.UserDataDto;
import com.mattermost.integration.figma.security.service.OAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TeamNameParallelSearchServiceImpl implements TeamNameParallelSearchService {
    @Autowired
    private FigmaProjectService figmaProjectService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserDataKVService userDataKVService;

    @Autowired
    private OAuthService oAuthService;

    public List<TeamNameDto> doTeamNameSearchTask(List<TeamNameDto> teamNameDtos, String userId,
                                                  String mmSiteUrl, String botAccessToken) {
        Optional<UserDataDto> userDataOptional = userDataKVService.getUserData(userId, mmSiteUrl, botAccessToken);

        if (userDataOptional.isEmpty()) {
            return new ArrayList<>();
        }
        UserDataDto userData = userDataOptional.get();
        String accessToken = oAuthService.refreshToken(userData.getClientId(), userData.getClientSecret(),
                userData.getRefreshToken()).getAccessToken();
        return teamNameDtos.parallelStream().peek(dto -> {
            Optional<TeamProjectDTO> projectsByTeamId = figmaProjectService.getProjectsByTeamIdWithCustomRestTemplate(dto.getTeamId(), accessToken, restTemplate);
            projectsByTeamId.ifPresent(teamProjectDTO -> dto.setTeamName(teamProjectDTO.getName()));
        }).filter(dto -> Objects.nonNull(dto.getTeamName())).collect(Collectors.toList());
    }
}
