package com.kidstory.backend.service.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Ham 16 bit PCM sesi WAV dosyasına sarma ve WAV süresini okuma (ek kütüphane gerekmez). */
final class Wav {

    private Wav() {
    }

    static byte[] fromPcm16(byte[] pcm, int sampleRate, int channels) {
        int byteRate = sampleRate * channels * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(byteRate).putShort((short) (channels * 2)).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return b.array();
    }

    /** WAV başlığındaki bayt hızına göre süre; başlık okunamazsa null. */
    static BigDecimal durationSeconds(byte[] wav) {
        if (wav.length < 44 || !"RIFF".equals(new String(wav, 0, 4, StandardCharsets.US_ASCII))) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = b.getInt(28);
        // "data" bölümünü bul (fmt dışında bölümler olabilir)
        int pos = 12;
        while (pos + 8 <= wav.length) {
            String id = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(pos + 4);
            if ("data".equals(id)) {
                int dataBytes = size < 0 || size > wav.length - pos - 8 ? wav.length - pos - 8 : size;
                return byteRate <= 0 ? null
                        : BigDecimal.valueOf(dataBytes).divide(BigDecimal.valueOf(byteRate), 2, RoundingMode.HALF_UP);
            }
            if (size < 0) {
                return null;
            }
            pos += 8 + size + (size & 1);
        }
        return null;
    }
}
