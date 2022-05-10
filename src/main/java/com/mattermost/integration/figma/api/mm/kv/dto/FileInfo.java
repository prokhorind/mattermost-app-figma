package com.mattermost.integration.figma.api.mm.kv.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FileInfo {
    @EqualsAndHashCode.Include
    private String fileId;
    private String fileName;
}
