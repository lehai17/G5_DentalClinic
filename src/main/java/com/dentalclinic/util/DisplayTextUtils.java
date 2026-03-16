package com.dentalclinic.util;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DisplayTextUtils {

    private static final Map<Character, Byte> WINDOWS_1252_BYTES = new LinkedHashMap<>();

    static {
        WINDOWS_1252_BYTES.put('€', (byte) 0x80);
        WINDOWS_1252_BYTES.put('‚', (byte) 0x82);
        WINDOWS_1252_BYTES.put('ƒ', (byte) 0x83);
        WINDOWS_1252_BYTES.put('„', (byte) 0x84);
        WINDOWS_1252_BYTES.put('…', (byte) 0x85);
        WINDOWS_1252_BYTES.put('†', (byte) 0x86);
        WINDOWS_1252_BYTES.put('‡', (byte) 0x87);
        WINDOWS_1252_BYTES.put('ˆ', (byte) 0x88);
        WINDOWS_1252_BYTES.put('‰', (byte) 0x89);
        WINDOWS_1252_BYTES.put('Š', (byte) 0x8A);
        WINDOWS_1252_BYTES.put('‹', (byte) 0x8B);
        WINDOWS_1252_BYTES.put('Œ', (byte) 0x8C);
        WINDOWS_1252_BYTES.put('Ž', (byte) 0x8E);
        WINDOWS_1252_BYTES.put('‘', (byte) 0x91);
        WINDOWS_1252_BYTES.put('’', (byte) 0x92);
        WINDOWS_1252_BYTES.put('“', (byte) 0x93);
        WINDOWS_1252_BYTES.put('”', (byte) 0x94);
        WINDOWS_1252_BYTES.put('•', (byte) 0x95);
        WINDOWS_1252_BYTES.put('–', (byte) 0x96);
        WINDOWS_1252_BYTES.put('—', (byte) 0x97);
        WINDOWS_1252_BYTES.put('˜', (byte) 0x98);
        WINDOWS_1252_BYTES.put('™', (byte) 0x99);
        WINDOWS_1252_BYTES.put('š', (byte) 0x9A);
        WINDOWS_1252_BYTES.put('›', (byte) 0x9B);
        WINDOWS_1252_BYTES.put('œ', (byte) 0x9C);
        WINDOWS_1252_BYTES.put('ž', (byte) 0x9E);
        WINDOWS_1252_BYTES.put('Ÿ', (byte) 0x9F);
    }

    private DisplayTextUtils() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String normalized = value;
        for (int i = 0; i < 5 && looksMojibake(normalized); i++) {
            String repaired = repairOnce(normalized);
            if (repaired.isBlank() || repaired.equals(normalized)) {
                break;
            }
            normalized = repaired;
        }

        return normalized;
    }

    private static String repairOnce(String value) {
        byte[] bytes = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch <= 0xFF) {
                bytes[i] = (byte) ch;
                continue;
            }
            Byte mapped = WINDOWS_1252_BYTES.get(ch);
            if (mapped == null) {
                return value;
            }
            bytes[i] = mapped;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean looksMojibake(String value) {
        return value.contains("?")
                || value.contains("?")
                || value.contains("??")
                || value.contains("??")
                || value.contains("?")
                || value.contains("?")
                || value.contains("Kh?")
                || value.contains("Ch?")
                || value.contains("D?")
                || value.contains("Ng?")
                || value.contains("Vui l?")
                || value.contains("�")
                || value.contains("???")
                || value.contains("??");
    }
}
