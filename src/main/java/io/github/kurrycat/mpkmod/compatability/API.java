package io.github.kurrycat.mpkmod.compatability;

import io.github.kurrycat.mpkmod.compatability.MCClasses.FunctionHolder;
import io.github.kurrycat.mpkmod.compatability.MCClasses.Minecraft;
import io.github.kurrycat.mpkmod.compatability.MCClasses.Renderer3D;
import io.github.kurrycat.mpkmod.discord.DiscordRPC;
import io.github.kurrycat.mpkmod.events.Event;
import io.github.kurrycat.mpkmod.events.*;
import io.github.kurrycat.mpkmod.gui.ComponentScreen;
import io.github.kurrycat.mpkmod.gui.MPKGuiScreen;
import io.github.kurrycat.mpkmod.gui.MainGuiScreen;
import io.github.kurrycat.mpkmod.gui.components.Component;
import io.github.kurrycat.mpkmod.gui.screens.MapOverviewGUI;
import io.github.kurrycat.mpkmod.landingblock.LandingBlock;
import io.github.kurrycat.mpkmod.save.Serializer;
import io.github.kurrycat.mpkmod.util.BoundingBox3D;
import io.github.kurrycat.mpkmod.util.JSONConfig;
import io.github.kurrycat.mpkmod.util.MathUtil;
import io.github.kurrycat.mpkmod.util.Vector2D;

import java.awt.*;
import java.time.Instant;
import java.util.Optional;

public class API {
    public static final String MODID = "mpkmod";
    public static final String NAME = "MPK Mod";
    public static final String VERSION = "2.0";
    public static final String KEYBINDING_CATEGORY = NAME;
    public static Instant gameStartedInstant;
    private static MPKGuiScreen guiScreen;
    private static FunctionHolder functionHolder;

    public static MPKGuiScreen getGuiScreen() {
        if (guiScreen == null) {
            guiScreen = new MainGuiScreen();
        }
        return guiScreen;
    }

    /**
     * Gets called once at the end of the mod loader initialization event
     *
     * @param mcVersion String containing the current minecraft version (e.g. "1.8.9")
     */
    public static void init(String mcVersion) {
        Minecraft.version = mcVersion;

        gameStartedInstant = Instant.now();

        JSONConfig.setupFile();
        Serializer.registerSerializer();

        EventAPI.init();

        DiscordRPC.init();

        EventAPI.addListener(
                EventAPI.EventListener.onRenderOverlay(
                        e -> {
                            for (Component c : ((MainGuiScreen) getGuiScreen()).movableComponents) {
                                c.render(new Vector2D(-1, -1));
                            }
                        }
                )
        );

        EventAPI.addListener(
                new EventAPI.EventListener<OnRenderWorldOverlayEvent>(
                        e -> {
                            MapOverviewGUI.bbs.forEach(bb ->
                                    Renderer3D.drawBox(
                                            bb.expand(0.005D),
                                            new Color(255, 68, 68, 157),
                                            e.partialTicks
                                    )
                            );
                        },
                        Event.EventType.RENDER_WORLD_OVERLAY
                )
        );

        EventAPI.addListener(
                EventAPI.EventListener.onTickEnd(
                        e -> {
                            BoundingBox3D playerBB = LandingBlock.landingMode.getPlayerBB();
                            if (playerBB == null) return;
                            MapOverviewGUI.bbs.stream()
                                    .filter(LandingBlock::isTryingToLandOn)
                                    .map(bb -> bb.distanceTo(playerBB).mult(-1D))
                                    .filter(vec -> vec.getX() > -0.3 && vec.getZ() > -0.3)
                                    .forEach(offset -> {
                                        MPKGuiScreen screen = getGuiScreen();
                                        if (screen instanceof ComponentScreen)
                                            ((ComponentScreen) screen).postMessage(
                                                    "offset",
                                                    MathUtil.formatDecimals(offset.getX(), 5, false) +
                                                            ", " + MathUtil.formatDecimals(offset.getZ(), 5, false)
                                            );
                                    });
                        }
                )
        );
    }

    public static void registerFunctionHolder(FunctionHolder holder) {
        functionHolder = holder;
    }

    public static <T extends FunctionHolder> Optional<T> getFunctionHolder(Class<T> subClass) {
        return Optional.ofNullable(subClass.isInstance(functionHolder) ? subClass.cast(functionHolder) : null);
    }

    public static class Events {
        public static void onTickStart() {
            EventAPI.postEvent(new OnTickStartEvent());
        }

        public static void onTickEnd() {
            EventAPI.postEvent(new OnTickEndEvent());
        }

        public static void onRenderOverlay() {
            EventAPI.postEvent(new OnRenderOverlayEvent());
        }

        public static void onRenderWorldOverlay(float partialTicks) {
            EventAPI.postEvent(new OnRenderWorldOverlayEvent(partialTicks));
        }

        public static void onLoadComplete() {
            getGuiScreen().onGuiInit();
        }

        public static void onServerConnect(boolean isLocal) {
            Minecraft.updateWorldState(Event.EventType.SERVER_CONNECT, isLocal);
            DiscordRPC.updateWorldAndPlayState();
        }

        public static void onServerDisconnect() {
            Minecraft.updateWorldState(Event.EventType.SERVER_DISCONNECT, false);
            DiscordRPC.updateWorldAndPlayState();
        }
    }
}
