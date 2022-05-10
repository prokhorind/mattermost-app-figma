package com.mattermost.integration.figma.api.mm.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mattermost.integration.figma.api.mm.kv.dto.FileInfo;
import com.mattermost.integration.figma.utils.json.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class SubscriptionKVServiceImpl implements SubscriptionKVService {

    private static final String ALL_FILES = "ALL_FILES";
    private static final String ALL_CHANNELS = "ALL_CHANNELS";


    @Autowired
    private KVService kvService;

    @Autowired
    private JsonUtils jsonUtils;

    @Override
    public void putFile(String fileKey, String fileName, String mmChanelId, String mattermostSiteUrl, String token) {
        mapChannelToFile(fileKey, mmChanelId, mattermostSiteUrl, token);
        mapFileToAllFiles(fileKey, mattermostSiteUrl, token);
        mapFileToChannel(fileKey, fileName, mmChanelId, mattermostSiteUrl, token);
        mapChannelToAllChannels(mmChanelId, mattermostSiteUrl, token);
    }

    private void mapChannelToAllChannels(String mmChanelId, String mattermostSiteUrl, String token) {
        String allChannels = kvService.get(ALL_CHANNELS, mattermostSiteUrl, token);
        Set<String> channels = (Set<String>) jsonUtils.convertStringToObject(allChannels, new TypeReference<Set<String>>() {
        }).orElse(new HashSet<String>());
        channels.add(mmChanelId);
        kvService.put(ALL_CHANNELS, channels, mattermostSiteUrl, token);
    }

    private void mapFileToAllFiles(String fileKey, String mattermostSiteUrl, String token) {
        String allFiles = kvService.get(ALL_FILES, mattermostSiteUrl, token);
        Set<String> files = (Set<String>) jsonUtils.convertStringToObject(allFiles, new TypeReference<Set<String>>() {
        }).orElse(new HashSet<String>());
        files.add(fileKey);
        kvService.put(ALL_FILES, files, mattermostSiteUrl, token);
    }

    private void mapFileToChannel(String fileKey, String fileName, String mmChannelId, String mattermostSiteUrl, String token) {
        String mmChanelSubscribedFiles = kvService.get(mmChannelId, mattermostSiteUrl, token);
        Set<FileInfo> files = (Set<FileInfo>) jsonUtils.convertStringToObject(mmChanelSubscribedFiles, new TypeReference<Set<FileInfo>>() {
        }).orElse(new HashSet<FileInfo>());
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(fileKey);
        fileInfo.setFileName(fileName);
        files.add(fileInfo);
        kvService.put(mmChannelId, files, mattermostSiteUrl, token);
    }

    private void mapChannelToFile(String fileKey, String mmChanelId, String mattermostSiteUrl, String token) {
        String mmSubscribedChannels = kvService.get(fileKey, mattermostSiteUrl, token);
        Set<String> channels = (Set<String>) jsonUtils.convertStringToObject(mmSubscribedChannels, new TypeReference<Set<String>>() {
        }).orElse(new HashSet<String>());
        channels.add(mmChanelId);
        kvService.put(fileKey, channels, mattermostSiteUrl, token);
    }

    @Override
    public Set<FileInfo> getFilesByMMChannelId(String mmChannelId, String mattermostSiteUrl, String token) {
        String mmChanelSubscribedFiles = kvService.get(mmChannelId, mattermostSiteUrl, token);
        return (Set<FileInfo>) jsonUtils.convertStringToObject(mmChanelSubscribedFiles, new TypeReference<Set<String>>() {
        }).orElse(new HashSet<FileInfo>());
    }

    @Override
    public Set<String> getMMChannelIdsByFileId(String figmaFileId, String mattermostSiteUrl, String token) {
        String mmSubscribedChannels = kvService.get(figmaFileId, mattermostSiteUrl, token);
        return (Set<String>) jsonUtils.convertStringToObject(mmSubscribedChannels, new TypeReference<Set<String>>() {
        }).orElse(new HashSet<String>());
    }

    @Override
    public void unsubscribeFileFromChannel(String fileKey, String mmChannelId, String mattermostSiteUrl, String token) {
        removeFileFromChannel(fileKey, mmChannelId, mattermostSiteUrl, token);
        removeChannelFromFile(fileKey, mmChannelId, mattermostSiteUrl, token);
    }

    private void removeFileFromChannel(String fileKey, String mmChannelId, String mattermostSiteUrl, String token) {
        String mmChanelSubscribedFiles = kvService.get(mmChannelId, mattermostSiteUrl, token);
        Set<FileInfo> files = (Set<FileInfo>) jsonUtils.convertStringToObject(mmChanelSubscribedFiles, new TypeReference<Set<String>>() {
        }).get();
        files.removeIf(f -> f.getFileId().equals(fileKey));
        kvService.put(mmChannelId, files, mattermostSiteUrl, token);
    }

    private void removeChannelFromFile(String fileKey, String mmChannelId, String mattermostSiteUrl, String token) {
        String mmSubscribedChannelsToFile = kvService.get(fileKey, mattermostSiteUrl, token);
        Set<String> mmChannels = (Set<String>) jsonUtils.convertStringToObject(mmSubscribedChannelsToFile, new TypeReference<String>() {
        }).get();
        mmChannels.removeIf(channel -> channel.equals(mmChannelId));
        kvService.put(fileKey, mmChannels, mattermostSiteUrl, token);
    }

}
