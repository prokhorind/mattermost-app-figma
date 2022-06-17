package com.mattermost.integration.figma.api.mm.bindings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattermost.integration.figma.api.mm.kv.KVService;
import com.mattermost.integration.figma.api.mm.user.MMUserService;
import com.mattermost.integration.figma.input.mm.binding.Binding;
import com.mattermost.integration.figma.input.mm.binding.BindingsDTO;
import com.mattermost.integration.figma.input.mm.binding.Command;
import com.mattermost.integration.figma.input.mm.user.MMUser;
import com.mattermost.integration.figma.input.oauth.InputPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class BindingServiceImpl implements BindingService {
    private static final String DEFAULT_BINDINGS_PATH = "src\\main\\resources\\static\\bindings.json";
    private static final String ADMIN_ROLE = "system_admin system_user";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MMUserService mmUserService;

    public BindingsDTO filterBindingsDependingOnUser(InputPayload payload) {
        BindingsDTO defaultBindings = getDefaultBindings();
        String mmSiteUrl = payload.getContext().getMattermostSiteUrl();
        String botAccessToken = payload.getContext().getBotAccessToken();

        MMUser currentUser = mmUserService.getUserById(payload.getContext().getActingUser().getId(), mmSiteUrl, botAccessToken);

        if (!currentUser.getRoles().contains(ADMIN_ROLE)) {
            removeCommandFromBindings(defaultBindings, Command.CONFIGURE);
        }

        if (Objects.isNull(payload.getContext().getOauth2().getUser().getUserId())) {
            removeCommandFromBindings(defaultBindings, Command.SUBSCRIBE);
            removeCommandFromBindings(defaultBindings, Command.LIST);
            removeCommandFromBindings(defaultBindings, Command.DISCONNECT);
        }

        else {
            removeCommandFromBindings(defaultBindings, Command.CONNECT);
        }

        return defaultBindings;
    }

    private BindingsDTO getDefaultBindings() {
        try {
            String bindingsJson = new String(Files.readAllBytes(Paths.get(DEFAULT_BINDINGS_PATH)));
            BindingsDTO root = objectMapper.readValue(bindingsJson, BindingsDTO.class);
            return root;
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    private void removeCommandFromBindings(BindingsDTO bindings, Command command) {
        List<Binding> commands = bindings.getData().get(0).getBindings().get(0).getBindings();
        commands.removeIf(mmCommand -> mmCommand.getLabel().equals(command.getTitle()));
    }
}
