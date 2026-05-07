package com.ceres;

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
import com.playerapi.SoundActions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

    public static KeyBinding keyToggleGui;
    public static KeyBinding keyOpenPathGui;
    public static KeyBinding keyOpenConfig;
    public static KeyBinding keyStartPrimary;
    public static KeyBinding keyStartSecondary;
    public static KeyBinding keyPause;
    public static KeyBinding keyResume;
    public static KeyBinding keyStop;

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

        HudRenderCallback.EVENT.register(BotHudRenderer::render);

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

        BotLogger.getInstance().logInfo("Ceres ready.");
    }

    private void onTick() {
        if (!com.playerapi.PlayerInfo.isInWorld()) return;

        if (openConfigNextTick) {
            openConfigNextTick = false;
            try {
                MinecraftClient.getInstance().setScreen(CeresConfigScreen.create(null));
            } catch (Exception e) {
                BotLogger.getInstance().logError("Failed to open config screen: " + e.getMessage());
            }
        }

        long currentTick = Scheduler.getCurrentTick();

        CeresTabListReader.update();
        PestRepellentManager.getInstance().tick(currentTick);

        BotStateManager state = BotStateManager.getInstance();
        BotState botState = state.getCurrentState();

        if (botState == BotState.RUNNING) {
            PathManager.getInstance().tick();
            CheckerController.getInstance().tick(currentTick);
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
        checkForUpdate();
    }

    private static void checkForUpdate() {
        String currentVersion = FabricLoader.getInstance()
                .getModContainer("ceres")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_RELEASES_URL))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "CeresMod-UpdateCheck/1.0")
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String latestVersion = parseTagName(response.body());
                    if (latestVersion != null && isNewerVersion(latestVersion, currentVersion)) {
                        // Deliver the notification on the main thread after the world has settled
                        Scheduler.schedule(60, () -> sendUpdateNotification(latestVersion, currentVersion));
                    }
                }
            } catch (Exception e) {
                // Silent fail — update check is optional and best-effort
                BotLogger.getInstance().logInfo("Update check failed (offline?): " + e.getMessage());
            }
        }, "ceres-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private static String parseTagName(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String tag = obj.get("tag_name").getAsString();
            return tag.startsWith("v") ? tag.substring(1) : tag;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if {@code remote} is a strictly higher semantic version than {@code local}.
     * Compares each dot-separated numeric segment left-to-right. Non-numeric segments are ignored.
     */
    private static boolean isNewerVersion(String remote, String local) {
        try {
            String[] r = remote.replaceAll("[^0-9.]", "").split("\\.");
            String[] l = local.replaceAll("[^0-9.]", "").split("\\.");
            int len = Math.max(r.length, l.length);
            for (int i = 0; i < len; i++) {
                int rv = i < r.length && !r[i].isEmpty() ? Integer.parseInt(r[i]) : 0;
                int lv = i < l.length && !l[i].isEmpty() ? Integer.parseInt(l[i]) : 0;
                if (rv > lv) return true;
                if (rv < lv) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void sendUpdateNotification(String latestVersion, String currentVersion) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(
            Text.literal("[Ceres] ").formatted(Formatting.AQUA)
                .append(Text.literal("Update available! ").formatted(Formatting.GREEN))
                .append(Text.literal("v" + latestVersion).formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" (you have v" + currentVersion + ") ").formatted(Formatting.GRAY))
                .append(Text.literal("— " + GITHUB_RELEASES_URL
                        .replace("api.github.com/repos", "github.com")
                        .replace("/releases/latest", "/releases"))
                        .formatted(Formatting.YELLOW)),
            false);
        BotLogger.getInstance().logInfo(
                "Update available: v" + latestVersion + " (current: v" + currentVersion + ")");
    }

    // ── Keybinds ──────────────────────────────────────────────────────────────

    private void handleKeybinds(BotStateManager state) {
        if (keyToggleGui.wasPressed())      state.toggleGuiVisible();
        if (keyOpenPathGui.wasPressed())    MinecraftClient.getInstance().setScreen(new PathEditorScreen());
        if (keyOpenConfig.wasPressed())     MinecraftClient.getInstance().setScreen(
                                                CeresConfigScreen.create(null));
        if (keyStartPrimary.wasPressed()) {
            if (state.isGuiVisible()) autoLoadAndStart(state, PathType.PRIMARY);
            else BotLogger.getInstance().logWarn("Open the HUD first (;) before starting.");
        }
        if (keyStartSecondary.wasPressed()) {
            if (state.isGuiVisible()) autoLoadAndStart(state, PathType.SECONDARY);
            else BotLogger.getInstance().logWarn("Open the HUD first (;) before starting.");
        }
        if (keyPause.wasPressed())          state.pauseBot();
        if (keyResume.wasPressed())         state.resumeBot();
        if (keyStop.wasPressed())           state.stopBot();
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
    }

    private static final KeyBinding.Category CERES_CATEGORY =
            KeyBinding.Category.create(Identifier.of("ceres", "controls"));

    private void registerKeybinds() {
        keyToggleGui      = register("Toggle HUD",        InputUtil.GLFW_KEY_SEMICOLON);
        keyOpenPathGui    = register("Open Path Editor",  InputUtil.GLFW_KEY_I);
        keyOpenConfig     = register("Open Config",       -1);
        keyStartPrimary   = register("Start Primary",     InputUtil.GLFW_KEY_O);
        keyStartSecondary = register("Start Secondary",   InputUtil.GLFW_KEY_U);
        keyPause          = register("Pause Bot",         InputUtil.GLFW_KEY_P);
        keyResume         = register("Resume Bot",        InputUtil.GLFW_KEY_J);
        keyStop           = register("Stop Bot",          InputUtil.GLFW_KEY_K);
    }

    private static KeyBinding register(String name, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ceres." + name.toLowerCase().replace(' ', '_'),
                InputUtil.Type.KEYSYM,
                defaultKey,
                CERES_CATEGORY
        ));
    }
}
