package com.mattermost.integration.figma.input.mm.manifest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetOauth2ConnectUrl {
    private String path;
    private Expand expand;
}
