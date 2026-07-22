package com.meekdev.psyhou.game;

public final class VfxExampleGraphsCheck {

    private VfxExampleGraphsCheck() {
    }

    public static void main(String[] arguments) {
        try {
            VfxExampleGraphs.validateAll();
        } catch (RuntimeException failure) {
            System.out.println("[vfx-examples] FAIL: " + failure.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("[vfx-examples] PASS: the four example graphs parse and compile");
    }
}
