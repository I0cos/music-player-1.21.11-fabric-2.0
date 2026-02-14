package com.musicplayer;

import net.fabricmc.api.ModInitializer;

/**
 * Точка входа мода (общая).
 * Логика полностью на клиенте — см. MusicPlayerClient.
 */
public class MusicPlayerMod implements ModInitializer {
    public static final String MOD_ID = "musicplayer";

    @Override
    public void onInitialize() {
        // Клиентский мод — инициализация в MusicPlayerClient
    }
}
