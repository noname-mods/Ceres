package com.ceres.commands;

import com.ceres.CeresMod;
import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

public class CeresCommands {

    // Prefix colour: §6 gold (wheat), body: §f white — warm harvest/sunset feel
    private static final String PFX = "§6[Ceres]§f ";

    private CeresCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // /ceres — open the central config screen (sets flag; tick loop opens safely next frame)
            dispatcher.register(ClientCommands.literal("ceres")
                .executes(ctx -> {
                    if (BotConfig.getInstance().isDebugMode()) {
                        ctx.getSource().sendFeedback(Component.literal(
                            PFX + "§eDEBUG: ClientCommandManager executor fired — opening config next tick"));
                        BotLogger.getInstance().logInfo(
                            "[Debug] /ceres executor fired — scheduling screen open via tick flag");
                    }
                    CeresMod.openConfigNextTick = true;
                    return 1;
                })
            );
        });
    }
}
