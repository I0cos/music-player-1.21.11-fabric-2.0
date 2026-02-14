package com.musicplayer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI-экран музыкального плеера в стиле Minecraft.
 * Список файлов из папки music_player, кнопки Играть/Пауза/Стоп и регулятор громкости.
 */
@Environment(EnvType.CLIENT)
public class MusicPlayerScreen extends Screen {

    private final AudioPlayer audioPlayer;
    /** Список путей к поддерживаемым файлам в папке music_player */
    private List<Path> fileList = new ArrayList<>();
    /** Индекс выбранного файла в списке (-1 если ничего не выбрано) */
    private int selectedIndex = -1;
    /** Смещение прокрутки списка (сколько элементов скрыто сверху) */
    private int scrollOffset = 0;
    /** Высота одной строки в списке в пикселях */
    private static final int ROW_HEIGHT = 20;
    /** Область списка: отступы и размеры */
    private static final int LIST_LEFT = 40;
    private static final int LIST_TOP = 60;
    private static final int LIST_WIDTH = 280;
    private static final int LIST_ROWS = 10;

    public MusicPlayerScreen(AudioPlayer audioPlayer) {
        super(Text.translatable("gui.musicplayer.title"));
        this.audioPlayer = audioPlayer;
    }

    /** Папка с музыкой в корне игры */
    private static Path getMusicFolder() {
        return MinecraftClient.getInstance().getRunDirectory().toPath().resolve("music_player");
    }

    @Override
    protected void init() {
        super.init();
        // Гарантируем, что папка music_player существует, и обновляем список файлов
        AudioPlayer.ensureMusicFolderExists(getMusicFolder());
        refreshFileList();

        int centerX = width / 2;
        int buttonY = height - 32;
        int buttonWidth = 80;
        int spacing = 86;

        // Кнопка «Играть»
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.musicplayer.play"), button -> playSelected())
            .dimensions(centerX - spacing * 2, buttonY, buttonWidth, 20)
            .build());

        // Кнопка «Пауза»
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.musicplayer.pause"), button -> audioPlayer.pause())
            .dimensions(centerX - spacing, buttonY, buttonWidth, 20)
            .build());

        // Кнопка «Стоп»
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.musicplayer.stop"), button -> audioPlayer.stop())
            .dimensions(centerX, buttonY, buttonWidth, 20)
            .build());

        // Слайдер громкости (0 .. 100%)
        VolumeSlider volumeSlider = new VolumeSlider(
            centerX + spacing + 20, buttonY, 120, 20,
            (float) (audioPlayer.getVolume() * 100)
        );
        addDrawableChild(volumeSlider);
    }

    /** Обновляет список файлов из папки music_player */
    private void refreshFileList() {
        fileList.clear();
        Path folder = getMusicFolder();
        if (!Files.isDirectory(folder)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && AudioPlayer.isSupportedFile(p.getFileName().toString())) {
                    fileList.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        fileList.sort(Path::compareTo);
        selectedIndex = Math.min(selectedIndex, fileList.size() - 1);
        if (fileList.isEmpty()) selectedIndex = -1;
    }

    private void playSelected() {
        if (selectedIndex >= 0 && selectedIndex < fileList.size()) {
            audioPlayer.play(fileList.get(selectedIndex));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Заголовок
        context.drawCenteredTextWithShadow(textRenderer, title.getString(), width / 2, 20, 0xFFFFFF);

        // Подсказка
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("gui.musicplayer.hint").getString(),
            width / 2, 42, 0xA0A0A0
        );

        // Рисуем список файлов
        int listHeight = ROW_HEIGHT * LIST_ROWS;
        int visibleCount = Math.min(LIST_ROWS, fileList.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int idx = scrollOffset + i;
            Path path = fileList.get(idx);
            String name = path.getFileName().toString();
            int y = LIST_TOP + i * ROW_HEIGHT;
            boolean selected = (idx == selectedIndex);
            boolean hovered = mouseX >= LIST_LEFT && mouseX < LIST_LEFT + LIST_WIDTH
                && mouseY >= y && mouseY < y + ROW_HEIGHT;

            // Фон строки
            int bgColor = selected ? 0x40FFFFFF : (hovered ? 0x20FFFFFF : 0x10FFFFFF);
            context.fill(LIST_LEFT, y, LIST_LEFT + LIST_WIDTH, y + ROW_HEIGHT, bgColor);

            // Обрезаем длинное имя
            String display = name.length() > 35 ? name.substring(0, 32) + "..." : name;
            context.drawTextWithShadow(textRenderer, display, LIST_LEFT + 4, y + 6, 0xE0E0E0);
        }

        // Рамка вокруг списка
        context.drawBorder(LIST_LEFT - 1, LIST_TOP - 1, LIST_WIDTH + 2, listHeight + 2, 0xFF808080);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < LIST_ROWS; i++) {
                int idx = scrollOffset + i;
                if (idx >= fileList.size()) break;
                int y = LIST_TOP + i * ROW_HEIGHT;
                if (mouseX >= LIST_LEFT && mouseX < LIST_LEFT + LIST_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    selectedIndex = idx;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= LIST_LEFT && mouseX < LIST_LEFT + LIST_WIDTH
            && mouseY >= LIST_TOP && mouseY < LIST_TOP + ROW_HEIGHT * LIST_ROWS) {
            int maxScroll = Math.max(0, fileList.size() - LIST_ROWS);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** Слайдер громкости (0–100%), обновляет AudioPlayer */
    private class VolumeSlider extends SliderWidget {
        public VolumeSlider(int x, int y, int width, int height, float initialPercent) {
            super(x, y, width, height, Text.translatable("gui.musicplayer.volume", (int) initialPercent), initialPercent / 100.0);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("gui.musicplayer.volume", (int) (value * 100)));
        }

        @Override
        protected void applyValue() {
            audioPlayer.setVolume((float) value);
        }
    }
}
