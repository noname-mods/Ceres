package com.ceres.gui;

import com.ceres.core.BotConfig;
import com.playerapi.config.PlayerConfig;
import com.playerapi.config.theme.ConfigTheme;
import com.playerapi.config.theme.Surface;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * Ceres config screen. The screen is defined declaratively by the annotations on {@link BotConfig} and
 * rendered by PlayerAPI's built-in config library; this class is the thin factory used by ModMenu and
 * the {@code /ceres} command, and it supplies Ceres' green/nature theme. Persistence stays with
 * {@code BotConfig}.
 */
public final class CeresConfigScreen {

    private CeresConfigScreen() {}

    public static Screen create(Screen parent) {
        BotConfig cfg = BotConfig.getInstance();
        // The config screen always uses Ceres' full custom look; the Custom/Toned/Flat style setting
        // now drives the live HUD instead (see BotHudRenderer).
        return PlayerConfig.createScreen("Ceres", cfg, cfg::save, parent, fullTheme());
    }

    /** Ceres' full custom look — dark leaf-green + earthy panels, flowers/grass/food backdrop. */
    private static ConfigTheme fullTheme() {
        ConfigTheme t = new ConfigTheme();
        t.background       = Surface.stretch(tex("background"), 512); // bright farm art, opaque
        t.sidebar         = null; // translucent sidebar colour lets the art glow through
        t.selection       = Surface.nineSlice(tex("selection"), 16, 5);
        t.inputBox        = Surface.nineSlice(tex("input"), 16, 5);
        t.accordionHeader = Surface.nineSlice(tex("accordion"), 16, 5);
        t.toggleOnTex     = Surface.nineSlice(tex("toggle_on"), 16, 6);
        t.toggleOffTex    = Surface.nineSlice(tex("toggle_off"), 16, 6);
        t.toggleKnobTex   = Surface.stretch(tex("toggle_knob"), 16);
        t.buttonTex       = Surface.nineSlice(tex("button"), 16, 5);
        t.buttonHoverTex  = Surface.nineSlice(tex("button_hover"), 16, 5);

        t.screenBg    = 0xFF0C1509;
        t.sidebarBg   = 0x8C0E1A0C;   // translucent over the art
        t.contentScrim = 0xA60C1509;  // keeps light text readable over the bright background
        t.textShadow   = true;
        t.divider     = 0xFF294020;
        t.catSelected = 0xFFFFFFFF;
        t.rowLine     = 0x338FC080;
        t.labelFg     = 0xFFEAF3E6;
        t.descFg      = 0xFF9BB090;
        t.catFg       = 0xFFD4E4CC;
        t.subFg       = 0xFFAEC6A2;
        t.chevronFg   = 0xFF8FC080;
        t.accFg       = 0xFFE0F0D8;
        t.widgetFg    = 0xFFFFFFFF;
        t.boxBg       = 0xFF101710;
        t.boxFocus    = 0xFF182214;
        t.border      = 0xFF4E9A54;
        t.enumArrow   = 0xFF3E8A48;
        t.enumBox     = 0xFF1C2818;
        t.sliderTrack = 0xFF294020;
        t.sliderFill  = 0xFF4E9A54;
        t.sliderKnob  = 0xFFD4E4CC;
        t.buttonBg    = 0xFF4E9A54;
        t.buttonHover = 0xFF62B468;
        t.buttonFg    = 0xFFFFFFFF;
        t.toggleOn    = 0xFF5AA860;
        t.toggleOff   = 0xFF3A403A;
        t.toggleKnob  = 0xFFEAF3E6;
        return t;
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("playerapi", "textures/config/ceres/" + name + ".png");
    }
}
