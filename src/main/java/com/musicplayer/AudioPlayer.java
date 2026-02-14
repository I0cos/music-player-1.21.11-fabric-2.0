package com.musicplayer;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Воспроизведение аудио через Java Sound API в отдельном потоке.
 * Не использует OpenAL игры — звук идёт отдельно, без конфликта с игровыми звуками.
 * Поддерживаются форматы: WAV (встроенно), MP3 (через JLayer).
 */
public class AudioPlayer {

    private static final int BUFFER_SIZE = 4096;
    private static final AudioFormat TARGET_FORMAT = new AudioFormat(44100, 16, 2, true, false);

    private volatile boolean playing;
    private volatile boolean paused;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private Thread playThread;
    private float volume = 1.0f; // 0.0 .. 1.0

    /** Создаёт папку music_player по указанному пути (обычно корень игры), если её нет */
    public static void ensureMusicFolderExists(Path musicFolder) {
        if (musicFolder == null) return;
        try {
            Files.createDirectories(musicFolder);
        } catch (IOException e) {
            // игнорируем — папка может уже существовать или нет прав
        }
    }

    /** Проверяет, поддерживается ли расширение файла */
    public static boolean isSupportedFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".wav") || lower.endsWith(".mp3");
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setVolume(float v) {
        this.volume = Math.max(0f, Math.min(1f, v));
    }

    public float getVolume() {
        return volume;
    }

    /** Остановить воспроизведение и дождаться завершения потока */
    public void stop() {
        stopRequested.set(true);
        if (playThread != null && playThread.isAlive()) {
            try {
                playThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        playThread = null;
        playing = false;
        paused = false;
        stopRequested.set(false);
    }

    /** Поставить на паузу (только для текущей реализации — остановка; полноценная пауза сложнее без буферизации) */
    public void pause() {
        this.paused = true;
        stopRequested.set(true);
    }

    /** Запуск воспроизведения файла по пути */
    public void play(Path file) {
        if (file == null || !Files.isRegularFile(file)) return;
        stop();
        stopRequested.set(false);
        String name = file.getFileName().toString().toLowerCase();
        playThread = new Thread(() -> playInThread(file, name), "MusicPlayer-Audio");
        playThread.setDaemon(true);
        playThread.start();
    }

    private void playInThread(Path file, String nameLower) {
        try {
            if (nameLower.endsWith(".wav")) {
                playWav(file);
            } else if (nameLower.endsWith(".mp3")) {
                playMp3(file);
            }
        } catch (Exception e) {
            // Логирование в консоль для отладки
            System.err.println("[MusicPlayer] Ошибка воспроизведения: " + e.getMessage());
        } finally {
            playing = false;
        }
    }

    private void playWav(Path file) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file.toFile())) {
            playAudioStream(ais);
        }
    }

    private void playMp3(Path file) throws Exception {
        // JLayer: декодируем MP3 в PCM и воспроизводим через SourceDataLine
        try (InputStream is = Files.newInputStream(file)) {
            javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(is);
            javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
            SourceDataLine line = null;
            try {
                line = AudioSystem.getSourceDataLine(TARGET_FORMAT);
                line.open(TARGET_FORMAT, BUFFER_SIZE * 4);
                line.start();
                playing = true;
                javazoom.jl.decoder.Header h;
                while (!stopRequested.get() && (h = bitstream.readFrame()) != null) {
                    if (paused) {
                        Thread.sleep(100);
                        continue;
                    }
                    Object decoded = decoder.decodeFrame(h, bitstream);
                    if (!(decoded instanceof javazoom.jl.decoder.SampleBuffer)) continue;
                    javazoom.jl.decoder.SampleBuffer sampleBuffer = (javazoom.jl.decoder.SampleBuffer) decoded;
                    short[] samples = sampleBuffer.getBuffer();
                    int len = sampleBuffer.getBufferLength();
                    byte[] bytes = shortsToBytes(samples, len);
                    applyVolume(bytes);
                    line.write(bytes, 0, bytes.length);
                    bitstream.closeFrame();
                }
            } finally {
                if (line != null) {
                    line.drain();
                    line.close();
                }
                bitstream.close();
            }
        }
    }

    private void playAudioStream(AudioInputStream ais) throws Exception {
        AudioFormat format = ais.getFormat();
        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.getSampleRate(),
                16,
                format.getChannels(),
                format.getChannels() * 2,
                format.getSampleRate(),
                false
            );
            ais = AudioSystem.getAudioInputStream(target, ais);
            format = target;
        }
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format, BUFFER_SIZE * 4);
        line.start();
        playing = true;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while (!stopRequested.get() && (read = ais.read(buffer)) != -1) {
            if (paused) {
                Thread.sleep(100);
                continue;
            }
            applyVolume(buffer, read);
            line.write(buffer, 0, read);
        }
        line.drain();
        line.close();
    }

    private static byte[] shortsToBytes(short[] samples, int length) {
        byte[] bytes = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            bytes[i * 2] = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    private void applyVolume(byte[] buffer) {
        applyVolume(buffer, buffer.length);
    }

    private void applyVolume(byte[] buffer, int len) {
        if (volume >= 1.0f) return;
        for (int i = 0; i < len - 1; i += 2) {
            int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8);
            sample = (int) (sample * volume);
            sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
            buffer[i] = (byte) (sample & 0xFF);
            buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }
}
