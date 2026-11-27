package com.ixce.gradientglass.config;

import com.ixce.gradientglass.GradientGlass;
import com.ixce.gradientglass.model.PatternLayers;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class GlassConfigScreen {
    private GlassConfigScreen() {}

    static Screen create(Screen parent) {
        var saved = GlassConfig.current;
        var edit = saved.copy();
        var defaults = new GlassConfig();
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("gradientglass.config"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("gradientglass.config.gradient"))
                        .option(floatOption("transition_width", defaults.transitionWidth, () -> edit.transitionWidth, v -> edit.transitionWidth = v, 0.25f, GradientGlass.REACH, 0.25f))
                        .option(booleanOption("blur", defaults.blur, () -> edit.blur, v -> edit.blur = v))
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("gradientglass.config.pattern_layers"))
                                .description(OptionDescription.of(Component.translatable("gradientglass.config.pattern_layers.description")))
                                .binding(defaults.patternLayers, () -> edit.patternLayers, v -> edit.patternLayers = v)
                                .controller(option -> IntegerSliderControllerBuilder.create(option).range(0, PatternLayers.MAX_OTHERS).step(1))
                                .build())
                        .option(intOption("block_subdivisions", defaults.blockSubdivisions, () -> edit.blockSubdivisions, v -> edit.blockSubdivisions = v))
                        .option(intOption("pane_rows", defaults.paneRows, () -> edit.paneRows, v -> edit.paneRows = v))
                        .option(intOption("pane_segments", defaults.paneSegments, () -> edit.paneSegments, v -> edit.paneSegments = v))
                        .option(booleanOption("beacon_gradient", defaults.beaconGradient, () -> edit.beaconGradient, v -> edit.beaconGradient = v))
                        .option(booleanOption("beacon_dye", defaults.beaconDye, () -> edit.beaconDye, v -> edit.beaconDye = v))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("gradientglass.config.textures"))
                        .option(floatOption("weak_channel", defaults.weakChannel, () -> edit.weakChannel, v -> edit.weakChannel = v, 0, 1, 0.05f))
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("gradientglass.config.visible_alpha"))
                                .description(OptionDescription.of(Component.translatable("gradientglass.config.visible_alpha.description")))
                                .binding(defaults.visibleAlpha, () -> edit.visibleAlpha, v -> edit.visibleAlpha = v)
                                .controller(option -> IntegerSliderControllerBuilder.create(option).range(0, 255).step(1))
                                .build())
                        .build())
                .save(() -> {
                    boolean texturesChanged = !edit.sameTextures(GlassConfig.current);
                    GlassConfig.save(edit.copy());
                    rebuild(texturesChanged);
                })
                .build()
                .generateScreen(parent);
    }

    private static void rebuild(boolean textures) {
        var minecraft = Minecraft.getInstance();
        if (textures) {
            minecraft.reloadResourcePacks();
        } else if (minecraft.level != null) {
            //? if >=26.2 {
            minecraft.levelExtractor.allChanged();
            //?} else {
            /*minecraft.levelRenderer.allChanged();
            *///?}
        }
    }

    private static Option<Float> floatOption(String key, float fallback, Supplier<Float> get, Consumer<Float> set,
                                             float min, float max, float step) {
        return Option.<Float>createBuilder()
                .name(Component.translatable("gradientglass.config." + key))
                .description(OptionDescription.of(Component.translatable("gradientglass.config." + key + ".description")))
                .binding(fallback, get, set)
                .controller(option -> FloatSliderControllerBuilder.create(option).range(min, max).step(step))
                .build();
    }

    private static Option<Integer> intOption(String key, int fallback, Supplier<Integer> get, Consumer<Integer> set) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable("gradientglass.config." + key))
                .description(OptionDescription.of(Component.translatable("gradientglass.config." + key + ".description")))
                .binding(fallback, get, set)
                .controller(option -> IntegerSliderControllerBuilder.create(option).range(1, GlassConfig.MAX_SUBDIVISIONS).step(1))
                .build();
    }

    private static Option<Boolean> booleanOption(String key, boolean fallback, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("gradientglass.config." + key))
                .description(OptionDescription.of(Component.translatable("gradientglass.config." + key + ".description")))
                .binding(fallback, get, set)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }
}
