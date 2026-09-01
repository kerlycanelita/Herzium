package dev.zymekoh.herzium.debug;

import net.fabricmc.api.ClientModInitializer;

public final class HerziumDebugClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DebugCollector.start();
    }
}
