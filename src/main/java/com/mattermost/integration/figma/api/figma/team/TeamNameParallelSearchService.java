package com.mattermost.integration.figma.api.figma.team;

import com.mattermost.integration.figma.api.figma.project.dto.TeamProjectDTO;
import com.mattermost.integration.figma.api.figma.team.dto.TeamNameDto;

import java.util.List;
import java.util.Set;

public interface TeamNameParallelSearchService {
    List<TeamNameDto> doTeamNameSearchTask(List<TeamNameDto> teamNameDtos, String userId,
                                           String mmSiteUrl, String botAccessToken);
}
