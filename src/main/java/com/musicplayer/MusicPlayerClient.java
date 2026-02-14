package com.musicplayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Клиентская инициализация мода: привязка клавиши M и открытие экрана плеера.
 */
public class MusicPlayerClient implements ClientModInitializer {

    /** Единственный экземпляр плеера (живёт на клиенте) */
    public static final AudioPlayer AUDIO_PLAYER = new AudioPlayer();

    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        // Регистрируем клавишу M для открытия меню
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.musicplayer.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.category.musicplayer"
        ));

        // При нажатии M открываем экран (только в игре, не в главном меню)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new MusicPlayerScreen(AUDIO_PLAYER));
            }
        });
    }
}
