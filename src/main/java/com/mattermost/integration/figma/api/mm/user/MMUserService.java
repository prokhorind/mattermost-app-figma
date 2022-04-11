package com.mattermost.integration.figma.api.mm.user;

import com.mattermost.integration.figma.input.mm.MMUser;

import java.util.List;

public interface MMUserService {
    List<MMUser> getUsersById(List<String> ids, String mattermostSiteUrl, String token);
}
