package com.nom1s.percentboosts.logic;

import java.util.random.RandomGenerator;

/** Decompiled from the uploaded 1.21.1 JAR's drop logic; no Minecraft dependencies. */
public final class DropMath {
    private DropMath() {}

    /** Strength = max HP + 4 x attack damage + 2.5 x armor + 12 if hostile. */
    public static double strength(double maxHp, double attack, double armor, boolean hostile) {
        return maxHp + 4.0 * attack + 2.5 * armor + (hostile ? 12.0 : 0.0);
    }

    /** Roll once on death of a mob killed directly by a player (25% chance). */
    public static boolean shouldDrop(RandomGenerator random) {
        return random.nextFloat() <= 0.25f;
    }

    /** Value distribution and strength bonuses recovered from the original bytecode. */
    public static int rollPercent(double strength, RandomGenerator random) {
        double roll = random.nextDouble();
        int value;
        if (roll < 0.72) value = 1 + random.nextInt(10);
        else if (roll < 0.92) value = 11 + random.nextInt(20);
        else if (roll < 0.985) value = 31 + random.nextInt(40);
        else value = 71 + random.nextInt(30);

        if (strength >= 120) value += random.nextInt(15, 35);
        else if (strength >= 60) value += random.nextInt(5, 18);
        else if (strength >= 30) value += random.nextInt(0, 8);
        return Math.max(1, Math.min(100, value));
    }
}
