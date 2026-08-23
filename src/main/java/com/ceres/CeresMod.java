package com.ceres;

import com.ceres.alerts.RebootAlertManager;
import com.ceres.checkers.CheckerController;
import com.ceres.commands.CeresCommands;
import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import com.ceres.gui.BotHudRenderer;
import com.ceres.gui.CeresConfigScreen;
import com.ceres.gui.PathEditorScreen;
import com.ceres.path.CropToolMapper;
import com.ceres.path.PathConfig;
import com.ceres.path.PathManager;
import com.ceres.path.PathType;
import com.ceres.path.ProfileManager;
import com.ceres.repellent.PestRepellentManager;
import com.ceres.tablist.CeresTabListReader;
import com.playerapi.MovementActions;
import com.playerapi.PlayerAPIEvents;
import com.playerapi.Scheduler;
import com.playerapi.UpdateChecker;
import com.playerapi.SoundActions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.lwjgl.glfw.GLFW.*;

public class CeresMod implements ClientModInitializer {

    /**
     * GitHub Releases API endpoint for update checks.
     * Replace GITHUB_USERNAME and the repo name once you have created the repository.
     * Format: https://api.github.com/repos/GITHUB_USERNAME/REPO_NAME/releases/latest
     */
    private static final String GITHUB_RELEASES_URL =
            "https://api.github.com/repos/noname-mods/Ceres/releases/latest";

    /** Set to true to open the config screen on the next tick (avoids chat-close race). */
    public static boolean openConfigNextTick = false;

    public static KeyMapping keyToggleGui;
    public static KeyMapping keyOpenPathGui;
    public static KeyMapping keyOpenConfig;
    public static KeyMapping keyStartPrimary;
    public static KeyMapping keyStartSecondary;
    public static KeyMapping keyPause;
    public static KeyMapping keyResume;
    public static KeyMapping keyStop;

    @Override
    public void onInitializeClient() {
        BotLogger.getInstance().logInfo("Ceres initialising...");
        // Note to self: do not add in-game guidance messages. Tell the user in chat instead.

        BotConfig.getInstance().load();
        PathConfig.getInstance().load();

        // Sounds stay under "farmbot" namespace — assets/farmbot/sounds/ cannot be moved at build time
        SoundActions.registerSound("farmbot", "level_up");
        SoundActions.registerSound("farmbot", "soft_alert");

        CeresCommands.register();
        registerKeybinds();

        BotHudRenderer.register(); // register the HUD panel with the shared editor
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("ceres", "hud"),
                BotHudRenderer::render);

        // Fallback: fires when ClientCommandManager did NOT handle the command (server overrode tree).
        // Sets a flag — the tick loop opens the screen next tick, safely after chat-close cleanup.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (command.trim().equalsIgnoreCase("ceres")) {
                BotLogger.getInstance().logInfo(
                    "/ceres caught via ALLOW_COMMAND — ClientCommandManager did not intercept (server command tree override).");
                openConfigNextTick = true;
                return false;
            }
            return true;
        });

        PlayerAPIEvents.TICK.register(this::onTick);
        PlayerAPIEvents.WORLD_JOIN.register(this::onWorldJoin);

        PlayerAPIEvents.BLOCK_BROKEN.register(() -> {
            BotStateManager s = BotStateManager.getInstance();
            if (s.getCurrentState() == BotState.RUNNING)
                s.recordBlockBroken(Scheduler.getCurrentTick());
        });

        PlayerAPIEvents.CHAT_RECEIVED.register((sender, message) ->
                RebootAlertManager.getInstance().onChatReceived(sender, message));

        BotLogger.getInstance().logInfo("Ceres ready.");
    }

    private void onTick() {
        if (!com.playerapi.PlayerInfo.isInWorld()) return;

        if (openConfigNextTick) {
            openConfigNextTick = false;
            try {
                Minecraft.getInstance().gui.setScreen(CeresConfigScreen.create(null));
            } catch (Exception e) {
                BotLogger.getInstance().logError("Failed to open config screen: " + e.getMessage());
            }
        }

        long currentTick = Scheduler.getCurrentTick();

        CeresTabListReader.update();
        PestRepellentManager.getInstance().tick(currentTick);
        RebootAlertManager.getInstance().tick();

        BotStateManager state = BotStateManager.getInstance();
        BotState botState = state.getCurrentState();

        if (botState == BotState.RUNNING) {
            PathManager.getInstance().tick();
            CheckerController.getInstance().tick(currentTick);
            com.ceres.checkers.CropChecker.getInstance().tick(currentTick);
            // Heartbeat: re-press attack every second in case it was dropped
            if (currentTick % 20 == 0) {
                MovementActions.pressKey("attack");
            }
        } else if (botState == BotState.PAUSED) {
            MovementActions.releaseAll();
        }

        handleKeybinds(state);
    }

    // ── Update checker ────────────────────────────────────────────────────────

    private void onWorldJoin() {
        if (!BotConfig.getInstance().isUpdateCheckEnabled()) return;
        UpdateChecker.check("ceres", GITHUB_RELEASES_URL);
    }

    // ── Keybinds ──────────────────────────────────────────────────────────────

    private void handleKeybinds(BotStateManager state) {
        if (keyToggleGui.consumeClick())      state.toggleGuiVisible();
        if (keyOpenPathGui.consumeClick())    Minecraft.getInstance().gui.setScreen(new PathEditorScreen());
        if (keyOpenConfig.consumeClick())     Minecraft.getInstance().gui.setScreen(
                                                CeresConfigScreen.create(null));
        if (keyStartPrimary.consumeClick()) {
            if (state.isGuiVisible()) autoLoadAndStart(state, PathType.PRIMARY);
            else BotLogger.getInstance().logWarn("Open the HUD first (;) before starting.");
        }
        if (keyStartSecondary.consumeClick()) {
            if (state.isGuiVisible()) autoLoadAndStart(state, PathType.SECONDARY);
            else BotLogger.getInstance().logWarn("Open the HUD first (;) before starting.");
        }
        if (keyPause.consumeClick())          state.pauseBot();
        if (keyResume.consumeClick())         state.resumeBot();
        if (keyStop.consumeClick())           state.stopBot();
    }

    /**
     * Checks the player's held tool, looks up the matching crop profile, and loads it into
     * PathConfig before starting the bot. If no match is found (unrecognised tool, or the
     * expected profile hasn't been saved yet) the bot starts with whatever paths are already
     * loaded — a warning is logged but the start is not blocked.
     */
    private void autoLoadAndStart(BotStateManager state, PathType pathType) {
        BotConfig cfg = BotConfig.getInstance();
        if (cfg.isAutoLoadEnabled()) {
            String profileName = CropToolMapper.resolveProfile();
            if (profileName != null && cfg.isAutoLoadCropEnabled(profileName)) {
                ProfileManager.ProfileData data = ProfileManager.getInstance().loadProfile(profileName);
                if (data != null) {
                    // Apply sprint first so it is included when setAllPaths() triggers save()
                    PathConfig.getInstance().setAllSprintSettings(data.sprint());
                    PathConfig.getInstance().setAllPaths(data.paths());
                    state.setActiveProfileName(profileName);
                    BotLogger.getInstance().logInfo("Auto-loaded profile: " + profileName + " (held tool)");
                } else {
                    BotLogger.getInstance().logWarn(
                        "Auto-detect: profile '" + profileName + "' not found — using current paths");
                }
            }
        }
        state.startBot(pathType);
        com.ceres.checkers.CropChecker.getInstance().begin();
    }

    private static final KeyMapping.Category CERES_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ceres", "category"));

    private void registerKeybinds() {
        keyToggleGui      = register("Toggle HUD",        GLFW_KEY_SEMICOLON);
        keyOpenPathGui    = register("Open Path Editor",  GLFW_KEY_I);
        keyOpenConfig     = register("Open Config",       InputConstants.UNKNOWN.getValue());
        keyStartPrimary   = register("Start Primary",     GLFW_KEY_O);
        keyStartSecondary = register("Start Secondary",   GLFW_KEY_U);
        keyPause          = register("Pause Bot",         GLFW_KEY_P);
        keyResume         = register("Resume Bot",        GLFW_KEY_J);
        keyStop           = register("Stop Bot",          GLFW_KEY_K);
    }

    private static KeyMapping register(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ceres." + name.toLowerCase().replace(' ', '_'),
                InputConstants.Type.KEYSYM,
                defaultKey,
                CERES_CATEGORY
        ));
    }
}
