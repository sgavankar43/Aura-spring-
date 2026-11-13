package com.aura.util;

import java.nio.charset.StandardCharsets;

public final class MurmurHash3 {

    private MurmurHash3() {
    }

    /**
     * Standard MurmurHash3 32-bit implementation
     */
    public static int hash32(byte[] data, int length, int seed) {
        int h1 = seed;
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;

        int len4 = length / 4;
        for (int i = 0; i < len4; i++) {
            int i4 = i * 4;
            int k1 = (data[i4] & 0xff)
                    | ((data[i4 + 1] & 0xff) << 8)
                    | ((data[i4 + 2] & 0xff) << 16)
                    | ((data[i4 + 3] & 0xff) << 24);

            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        int tail = len4 * 4;
        switch (length & 3) {
            case 3:
                k1 ^= (data[tail + 2] & 0xff) << 16;
            case 2:
                k1 ^= (data[tail + 1] & 0xff) << 8;
            case 1:
                k1 ^= (data[tail] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
                break;
            default:
                break;
        }

        h1 ^= length;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }

    public static int hash32(String text) {
        if (text == null) {
            return 0;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return hash32(bytes, bytes.length, 0);
    }

    /**
     * Compute deterministic percentage bucket [0..99] for user and feature
     */
    public static int getRolloutBucket(String userId, String featureKey) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        String combined = userId.trim() + ":" + (featureKey != null ? featureKey.trim() : "");
        int hash = hash32(combined);
        return Math.abs(hash % 100);
    }
}
