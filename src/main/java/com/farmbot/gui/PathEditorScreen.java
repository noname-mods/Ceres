package com.ceres.gui;

import com.ceres.core.BotStateManager;
import com.ceres.path.PathConfig;
import com.ceres.path.PathType;
import com.ceres.path.ProfileManager;
import com.ceres.path.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Path editor screen. Open with I key or /pathgui.
 *
 * Layout (top to bottom):
 *   Profile: [__name__________] [Save] [Load▼] [Delete] [Folder]
 *   ─────────────────────────────────────────────────────────────
 *   [PRIMARY] [SECONDARY]                     Sprint: [ON]   N waypoints
 *   ─────────────────────────────────────────────────────────────
 *   <waypoint rows  OR  profile list>
 *   ─────────────────────────────────────────────────────────────
 *                [Done]                               [+ Add Here]
 *
 * Profiles are saved to config/farmbot/profiles/<name>.json.
 * Done always commits editPaths/editSprint to paths.json (unchanged behaviour).
 */
public class PathEditorScreen extends Screen {

    // ── Key options per waypoint ──────────────────────────────────────────────
    private static final String[] KEY_OPTIONS = {"forward", "back", "left", "right", "sneak"};

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PROFILE_H  = 22;                         // profile bar height
    private static final int ROW_H      = 20;
    private static final int HEADER_H   = 26;                         // tab/sprint bar height
    private static final int TAB_Y_BASE = PROFILE_H + 4;             // tab row top  (= 26)
    private static final int LIST_Y     = TAB_Y_BASE + HEADER_H + 2; // list top     (= 52)
    private static final int BTN_SMALL  = 14;
    private static final int FOOTER_H   = 28;
    /** Extra vertical space consumed by the inline key editor when a row is expanded. */
    private static final int EDITOR_H   = 36;

    // ── Path editing state ────────────────────────────────────────────────────
    private PathType activeTab = PathType.PRIMARY;
    private Map<PathType, List<Waypoint>> editPaths;
    private Map<PathType, Boolean> editSprint;
    private int scrollOffset = 0;
    private int expandedRow  = -1;

    // ── Profile state ─────────────────────────────────────────────────────────
    private boolean showProfileList   = false;
    private int profileScrollOffset   = 0;
    private TextFieldWidget profileNameField;

    // ── Session tracking (profile name → HUD) ─────────────────────────────────
    /** Name of the profile last loaded in this session; null if none loaded. */
    private String loadedProfileName  = null;
    /** True if any edit was made AFTER the last profile load (marks paths as "Custom"). */
    private boolean dirtyAfterLoad    = false;
    /** True if any load or edit happened at all this session (gate for close() update). */
    private boolean sessionTouched    = false;

    public PathEditorScreen() {
        super(Text.literal("Path Editor"));
    }

    @Override
    protected void init() {
        editPaths  = PathConfig.getInstance().getAllPaths();
        editSprint = PathConfig.getInstance().getAllSprintSettings();
        scrollOffset    = 0;
        expandedRow     = -1;
        showProfileList = false;
        rebuildWidgets();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Session dirty tracking
    // ─────────────────────────────────────────────────────────────────────────

    /** Call whenever the player edits paths (add/remove/move waypoint, toggle sprint/keys). */
    private void markDirty() {
        dirtyAfterLoad = true;
        sessionTouched = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Widget construction
    // ─────────────────────────────────────────────────────────────────────────

    private void rebuildWidgets() {
        // Preserve text across rebuilds — clearChildren() destroys the widget reference
        String savedProfileName = profileNameField != null ? profileNameField.getText() : "";

        clearChildren();

        buildProfileBar(savedProfileName);

        if (showProfileList) {
            buildProfileListArea();
        } else {
            buildTabBar();
            buildWaypointRows();
        }

        buildFooter();
    }

    // ── Profile bar (always visible at top) ───────────────────────────────────

    private void buildProfileBar(String savedName) {
        int labelW  = textRenderer.getWidth("Profile: ") + 4;
        int btnH    = PROFILE_H - 6;
        int btnY    = 3;

        // Buttons right-aligned: Folder | Delete | Load | Save
        int folderX = width - 46;
        int deleteX = folderX - 46;
        int loadX   = deleteX - 50;
        int saveX   = loadX   - 40;

        int fieldW  = Math.max(60, saveX - labelW - 6);

        // Text field
        profileNameField = new TextFieldWidget(
                textRenderer, labelW, btnY + 1, fieldW, btnH + 2,
                Text.literal("Profile name"));
        profileNameField.setMaxLength(64);
        profileNameField.setPlaceholder(Text.literal("profile name..."));
        profileNameField.setText(savedName);
        addDrawableChild(profileNameField);

        // [Save]
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> onSaveProfile())
                .dimensions(saveX, btnY, 36, btnH).build());

        // [Load ▼ / ▲]
        addDrawableChild(ButtonWidget.builder(
                Text.literal(showProfileList ? "Load ▲" : "Load ▼"),
                btn -> {
                    showProfileList = !showProfileList;
                    profileScrollOffset = 0;
                    rebuildWidgets();
                }).dimensions(loadX, btnY, 46, btnH).build());

        // [Delete]
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), btn -> onDeleteProfile())
                .dimensions(deleteX, btnY, 42, btnH).build());

        // [Folder]
        addDrawableChild(ButtonWidget.builder(Text.literal("Folder"),
                btn -> ProfileManager.getInstance().openProfilesFolder())
                .dimensions(folderX, btnY, 42, btnH).build());
    }

    // ── Tab / sprint bar ──────────────────────────────────────────────────────

    private void buildTabBar() {
        int tabY = TAB_Y_BASE + 4;
        PathType[] types = PathType.values();
        int tabW = 95, tabH = 18;

        for (int i = 0; i < types.length; i++) {
            PathType t = types[i];
            boolean active = t == activeTab;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(active ? "▶ " + t.name() : t.name()),
                    btn -> { activeTab = t; scrollOffset = 0; expandedRow = -1; rebuildWidgets(); })
                    .dimensions(8 + i * (tabW + 3), tabY, tabW, tabH).build());
        }

        boolean sprint = editSprint.getOrDefault(activeTab, true);
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Sprint: " + (sprint ? "§aON" : "§cOFF")),
                btn -> { editSprint.put(activeTab, !sprint); markDirty(); rebuildWidgets(); })
                .dimensions(width - 88, tabY, 80, tabH).build());
    }

    // ── Profile list (replaces waypoint list when showProfileList) ────────────

    private void buildProfileListArea() {
        List<String> profiles = ProfileManager.getInstance().listProfiles();
        int listH      = height - LIST_Y - FOOTER_H;
        int maxVisible = listH / ROW_H;

        for (int i = profileScrollOffset;
             i < Math.min(profiles.size(), profileScrollOffset + maxVisible);
             i++) {
            final String name = profiles.get(i);
            int rowY = LIST_Y + (i - profileScrollOffset) * ROW_H;
            addDrawableChild(ButtonWidget.builder(Text.literal(name),
                    btn -> onLoadProfile(name))
                    .dimensions(8, rowY + 2, width - 16, ROW_H - 4).build());
        }
    }

    // ── Waypoint rows ─────────────────────────────────────────────────────────

    private void buildWaypointRows() {
        List<Waypoint> waypoints = editPaths.get(activeTab);
        int listH      = height - LIST_Y - FOOTER_H;
        // When an editor panel is open it steals EDITOR_H from the list area
        int usableH    = listH - (expandedRow >= 0 ? EDITOR_H : 0);
        int maxVisible = Math.max(1, usableH / ROW_H);

        for (int i = scrollOffset; i < Math.min(waypoints.size(), scrollOffset + maxVisible); i++) {
            final int idx = i;
            // Rows after the expanded editor are pushed down by its height so they never overlap
            int extraY = (expandedRow >= scrollOffset && i > expandedRow) ? EDITOR_H : 0;
            int rowY = LIST_Y + (i - scrollOffset) * ROW_H + extraY;
            int bx   = 8;

            if (i > 0)
                addDrawableChild(ButtonWidget.builder(Text.literal("↑"),
                        btn -> moveWaypoint(idx, idx - 1))
                        .dimensions(bx, rowY + 3, BTN_SMALL, BTN_SMALL).build());
            bx += BTN_SMALL + 2;

            if (i < waypoints.size() - 1)
                addDrawableChild(ButtonWidget.builder(Text.literal("↓"),
                        btn -> moveWaypoint(idx, idx + 1))
                        .dimensions(bx, rowY + 3, BTN_SMALL, BTN_SMALL).build());
            bx += BTN_SMALL + 2;

            addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
                    btn -> { waypoints.remove(idx); if (expandedRow == idx) expandedRow = -1; markDirty(); rebuildWidgets(); })
                    .dimensions(bx, rowY + 3, BTN_SMALL, BTN_SMALL).build());
            bx += BTN_SMALL + 4;

            Waypoint w = waypoints.get(i);
            String keysLabel = w.hasForcedKeys()
                    ? "[" + String.join(", ", w.forcedKeys) + "]"
                    : "None";
            addDrawableChild(ButtonWidget.builder(Text.literal(keysLabel),
                    btn -> { expandedRow = (expandedRow == idx) ? -1 : idx; rebuildWidgets(); })
                    .dimensions(bx, rowY + 3, 90, BTN_SMALL).build());
        }

        buildExpandedKeyEditor(waypoints);
    }

    private void buildExpandedKeyEditor(List<Waypoint> waypoints) {
        if (expandedRow < 0 || expandedRow >= waypoints.size()) return;

        Waypoint w   = waypoints.get(expandedRow);
        int editorY  = LIST_Y + (expandedRow - scrollOffset) * ROW_H + ROW_H + 2;
        int kx       = 10;
        int keyBtnW  = 52;

        for (String key : KEY_OPTIONS) {
            boolean on = w.forcedKeys.contains(key);
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(on ? "§e[" + key + "]" : key),
                    btn -> {
                        if (w.forcedKeys.contains(key)) w.forcedKeys.remove(key);
                        else w.forcedKeys.add(key);
                        markDirty();
                        rebuildWidgets();
                    })
                    .dimensions(kx, editorY, keyBtnW, 14).build());
            kx += keyBtnW + 2;
            if (kx + keyBtnW > width - 70) { kx = 10; editorY += 16; }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"),
                btn -> { w.forcedKeys.clear(); markDirty(); rebuildWidgets(); })
                .dimensions(kx, editorY, 46, 14).build());
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void buildFooter() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
                .dimensions(width / 2 - 50, height - FOOTER_H + 4, 100, 20).build());

        if (!showProfileList) {
            addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Here"),
                    btn -> {
                        ClientPlayerEntity player = MinecraftClient.getInstance().player;
                        if (player != null) {
                            List<Waypoint> waypoints = editPaths.get(activeTab);
                            waypoints.add(new Waypoint(player.getX(), player.getY(), player.getZ()));
                            int listH2 = height - LIST_Y - FOOTER_H;
                            scrollOffset = Math.max(0, waypoints.size() - listH2 / ROW_H);
                            expandedRow  = -1;
                            markDirty();
                            rebuildWidgets();
                        }
                    })
                    .dimensions(width - 118, height - FOOTER_H + 4, 110, 20).build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Profile actions
    // ─────────────────────────────────────────────────────────────────────────

    private String profileName() {
        return profileNameField != null ? profileNameField.getText().trim() : "";
    }

    private void onSaveProfile() {
        String name = profileName();
        if (name.isEmpty()) return;
        ProfileManager.getInstance().saveProfile(name, editPaths, editSprint);
    }

    private void onLoadProfile(String name) {
        ProfileManager.ProfileData data = ProfileManager.getInstance().loadProfile(name);
        if (data == null) return;

        // Defensive copy so editor mutations don't affect the loaded record directly
        editPaths  = new EnumMap<>(PathType.class);
        editSprint = new EnumMap<>(data.sprint());
        for (PathType t : PathType.values())
            editPaths.put(t, new ArrayList<>(data.paths().getOrDefault(t, List.of())));

        if (profileNameField != null) profileNameField.setText(name);

        // Track which profile is loaded so close() can update the HUD name
        loadedProfileName = name;
        dirtyAfterLoad    = false;
        sessionTouched    = true;

        showProfileList  = false;
        scrollOffset     = 0;
        expandedRow      = -1;
        rebuildWidgets();
    }

    private void onDeleteProfile() {
        String name = profileName();
        if (name.isEmpty()) return;
        ProfileManager.getInstance().deleteProfile(name);
        if (showProfileList) rebuildWidgets(); // refresh list
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Waypoint helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void moveWaypoint(int from, int to) {
        List<Waypoint> wp = editPaths.get(activeTab);
        wp.add(to, wp.remove(from));
        expandedRow = to;
        markDirty();
        rebuildWidgets();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rendering
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC0101010);

        // Profile bar tint
        ctx.fill(0, 0, width, PROFILE_H + 2, 0x28FFFFFF);
        ctx.fill(0, PROFILE_H + 2, width, PROFILE_H + 3, 0x40FFFFFF);
        ctx.drawText(textRenderer, "Profile:", 8, 7, 0xFFAAAAAA, false);

        if (showProfileList) {
            // Hint bar in place of the tab row
            ctx.fill(0, TAB_Y_BASE, width, LIST_Y - 2, 0x18FFFFFF);
            ctx.fill(0, LIST_Y - 2, width, LIST_Y - 1, 0x40FFFFFF);
            ctx.drawText(textRenderer, "Select a profile to load:",
                    8, TAB_Y_BASE + 8, 0xFF888888, false);

            // Alternating row tints for profile buttons
            List<String> profiles = ProfileManager.getInstance().listProfiles();
            if (profiles.isEmpty()) {
                ctx.drawText(textRenderer, "No profiles found — save one first.",
                        8, LIST_Y + 6, 0xFF555555, false);
            } else {
                int listH = height - LIST_Y - FOOTER_H;
                int maxVisible = listH / ROW_H;
                for (int i = profileScrollOffset;
                     i < Math.min(profiles.size(), profileScrollOffset + maxVisible);
                     i++) {
                    int rowY = LIST_Y + (i - profileScrollOffset) * ROW_H;
                    if (i % 2 == 0) ctx.fill(0, rowY, width, rowY + ROW_H, 0x0CFFFFFF);
                }
            }
        } else {
            // Tab / sprint bar tint
            ctx.fill(0, TAB_Y_BASE, width, TAB_Y_BASE + HEADER_H, 0x20FFFFFF);
            ctx.fill(0, TAB_Y_BASE + HEADER_H, width, TAB_Y_BASE + HEADER_H + 1, 0x40FFFFFF);

            // Waypoint count (top-right of tab bar)
            List<Waypoint> waypoints = editPaths.get(activeTab);
            String count = waypoints.size() + " waypoints";
            ctx.drawText(textRenderer, count,
                    width - textRenderer.getWidth(count) - 96,
                    TAB_Y_BASE + 9, 0xFF555555, false);

            // Waypoint row backgrounds + coord labels
            int listH      = height - LIST_Y - FOOTER_H;
            int maxVisible = listH / ROW_H;
            for (int i = scrollOffset;
                 i < Math.min(waypoints.size(), scrollOffset + maxVisible);
                 i++) {
                int rowY = LIST_Y + (i - scrollOffset) * ROW_H;
                if (i % 2 == 0) ctx.fill(0, rowY, width, rowY + ROW_H, 0x0CFFFFFF);

                Waypoint w = waypoints.get(i);
                String label = String.format("#%d   %.1f / %.1f / %.1f", i + 1, w.x, w.y, w.z);
                ctx.drawText(textRenderer, label, 154, rowY + 6, 0xFFCCCCCC, false);
            }
        }

        // Footer separator
        ctx.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0x30FFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double vert) {
        if (showProfileList) {
            List<String> profiles = ProfileManager.getInstance().listProfiles();
            int maxScroll = Math.max(0, profiles.size() - (height - LIST_Y - FOOTER_H) / ROW_H);
            profileScrollOffset = (int) Math.max(0, Math.min(maxScroll, profileScrollOffset - vert));
        } else {
            List<Waypoint> wp = editPaths.get(activeTab);
            int maxScroll = Math.max(0, wp.size() - (height - LIST_Y - FOOTER_H) / ROW_H);
            scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - vert));
        }
        rebuildWidgets();
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Close / save
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void close() {
        PathConfig.getInstance().setAllSprintSettings(editSprint);
        PathConfig.getInstance().setAllPaths(editPaths); // also calls save()

        // Update the HUD profile name only if this session actually changed something.
        // If nothing was touched (user just opened and closed), preserve the existing name.
        if (sessionTouched) {
            String profileForHud = (!dirtyAfterLoad && loadedProfileName != null)
                    ? loadedProfileName : "Custom";
            BotStateManager.getInstance().setActiveProfileName(profileForHud);
        }

        client.setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }
}
