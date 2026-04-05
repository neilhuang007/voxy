package me.cortex.voxy.client.config;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.ClientSessionEvents;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.SSAO;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gui.options.*;
import net.caffeinemc.mods.sodium.client.gui.options.control.CyclingControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class VoxyConfigScreenPages {
    private VoxyConfigScreenPages() {}

    public static OptionPage voxyOptionPage = null;

    public static OptionPage page() {
        List<OptionGroup> groups = new ArrayList<>();
        VoxyConfig storage = VoxyConfig.CONFIG;

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.enabled"))
                        .setTooltip(Component.translatable("voxy.config.general.enabled.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> {
                            s.enabled = v;
                            if (v) {
                                if (ClientSessionEvents.inSession) {
                                    VoxyCommon.createInstance();
                                }
                            } else {
                                var vrsh = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
                                if (vrsh != null) {
                                    vrsh.voxy$shutdownRenderer();
                                }
                                VoxyCommon.shutdownInstance();
                            }
                        }, s -> s.enabled)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.translatable("voxy.config.general.serviceThreads"))
                        .setTooltip(Component.translatable("voxy.config.general.serviceThreads.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 1, CpuLayout.CORES.length, 1, v -> Component.literal(Integer.toString(v))))
                        .setBinding((s, v) -> {
                            s.serviceThreads = v;
                            var instance = VoxyCommon.getInstance();
                            if (instance != null) {
                                instance.updateDedicatedThreads();
                            }
                        }, s -> s.serviceThreads)
                        .setImpact(OptionImpact.HIGH)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.useSodiumBuilder"))
                        .setTooltip(Component.translatable("voxy.config.general.useSodiumBuilder.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.VARIES)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .setBinding((s, v) -> {
                            s.dontUseSodiumBuilderThreads = !v;
                            var instance = VoxyCommon.getInstance();
                            if (instance != null) {
                                instance.updateDedicatedThreads();
                            }
                        }, s -> !s.dontUseSodiumBuilderThreads)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.ingest"))
                        .setTooltip(Component.translatable("voxy.config.general.ingest.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.ingestEnabled = v, s -> s.ingestEnabled)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.rendering"))
                        .setTooltip(Component.translatable("voxy.config.general.rendering.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> {
                            s.enableRendering = v;
                            var vrsh = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
                            if (vrsh != null) {
                                if (v) {
                                    vrsh.voxy$createRenderer();
                                } else {
                                    vrsh.voxy$shutdownRenderer();
                                }
                            }
                        }, s -> s.enableRendering)
                        .setImpact(OptionImpact.HIGH)
                        .build())
                .add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.translatable("voxy.config.general.subDivisionSize"))
                        .setTooltip(Component.translatable("voxy.config.general.subDivisionSize.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, SUBDIV_IN_MAX, 1, v -> Component.literal(Integer.toString(Math.round(ln2subDiv(v))))))
                        .setBinding((s, v) -> s.subDivisionSize = ln2subDiv(v), s -> subDiv2ln(s.subDivisionSize))
                        .setImpact(OptionImpact.HIGH)
                        .build())
                .add(OptionImpl.createBuilder(int.class, storage)
                        .setName(Component.translatable("voxy.config.general.renderDistance"))
                        .setTooltip(Component.translatable("voxy.config.general.renderDistance.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 10, 64 * 16, 1, v -> Component.literal(Integer.toString(v * 2))))
                        .setBinding((s, v) -> {
                            s.sectionRenderDistance = ((float) v) / 16.0f;
                            var vrsh = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
                            if (vrsh != null) {
                                var vrs = vrsh.voxy$getRenderSystem();
                                if (vrs != null) {
                                    vrs.setRenderDistance(s.sectionRenderDistance);
                                }
                            }
                        }, s -> Math.round(s.sectionRenderDistance * 16f))
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, storage)
                        .setName(Component.translatable("voxy.config.general.environmental_fog"))
                        .setTooltip(Component.translatable("voxy.config.general.environmental_fog.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((s, v) -> s.useEnvironmentalFog = v, s -> s.useEnvironmentalFog)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(SSAO.SSAOMode.class, storage)
                        .setName(Component.translatable("voxy.config.general.ssao_mode"))
                        .setTooltip(Component.translatable("voxy.config.general.ssao_mode.tooltip"))
                        .setControl(opt -> new CyclingControl<>(opt, SSAO.SSAOMode.class))
                        .setBinding((s, v) -> s.setSSAOMode(v), VoxyConfig::getSSAOMode)
                        .setImpact(OptionImpact.MEDIUM)
                        .setEnabled(() -> !IrisUtil.irisShadersEnabledInConfig())
                        .build())
                .build());

        return voxyOptionPage = new OptionPage(Component.translatable("voxy.config.title"), ImmutableList.copyOf(groups));
    }

    private static final int SUBDIV_IN_MAX = 100;
    private static final double SUBDIV_MIN = 28;
    private static final double SUBDIV_MAX = 256;
    private static final double SUBDIV_CONST = Math.log(SUBDIV_MAX / SUBDIV_MIN) / Math.log(2);

    private static float ln2subDiv(int in) {
        return (float) (SUBDIV_MIN * Math.pow(2, SUBDIV_CONST * ((double) in / SUBDIV_IN_MAX)));
    }

    private static int subDiv2ln(float in) {
        return (int) (((Math.log(((double) in) / SUBDIV_MIN) / Math.log(2)) / SUBDIV_CONST) * SUBDIV_IN_MAX);
    }
}
