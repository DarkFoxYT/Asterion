package net.krodark.asterion.compat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Use Essential's native replay injector without depending on its late-loaded classes in a mixin. */
public final class FlashbackEssentialNetworkCompatibility {
    private static final String INJECTOR_NAME = "essential_outfits_injector";
    private FlashbackEssentialNetworkCompatibility() {}

    public static void protectReplayConnection(Object networkConnection) {
        if (!FlashbackAppearanceCompatibility.isPlayingReplay() || Api.GET_CHANNEL == null
                || Api.NOOP == null || !Api.GET_CHANNEL.getDeclaringClass().isInstance(networkConnection)) return;
        try {
            Object value = Api.GET_CHANNEL.invoke(networkConnection);
            if (!(value instanceof Channel channel)) return;
            if (channel.eventLoop().inEventLoop()) install(channel);
            else channel.eventLoop().execute(() -> install(channel));
        } catch (ReflectiveOperationException | LinkageError | java.util.concurrent.RejectedExecutionException unavailable) {
            // Optional API changes or a closing replay connection must not interrupt the client.
        }
    }

    private static void install(Channel channel) {
        if (!channel.isOpen() || !FlashbackAppearanceCompatibility.isPlayingReplay()) return;
        var pipeline = channel.pipeline();
        if (Api.NOOP.getDeclaringClass().isInstance(pipeline.get(INJECTOR_NAME))) return;
        try {
            ChannelHandler noop = (ChannelHandler) Api.NOOP.newInstance();
            if (pipeline.get(INJECTOR_NAME) != null) {
                pipeline.replace(INJECTOR_NAME, INJECTOR_NAME, noop);
            } else {
                // This handler forwards inbound replay packets, but ignores synthetic live outfit injection.
                pipeline.addFirst(INJECTOR_NAME, noop);
            }
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            // Unknown Essential versions retain their normal network behavior.
        }
    }

    private static final class Api {
        private static final Method GET_CHANNEL;
        private static final Constructor<?> NOOP;
        static {
            Method getChannel = null;
            Constructor<?> noop = null;
            try {
                ClassLoader loader = FlashbackEssentialNetworkCompatibility.class.getClassLoader();
                getChannel = Class.forName("gg.essential.mixins.transformers.network.NetworkManagerAccessor", false, loader)
                        .getMethod("getChannel");
                noop = Class.forName("gg.essential.cosmetics.IngameEquippedOutfitsUpdateDispatcher$OurChannelHandlerNoop", false, loader)
                        .getDeclaredConstructor();
                Class<?> injector = Class.forName(
                        "gg.essential.cosmetics.IngameEquippedOutfitsUpdateDispatcher$OurChannelHandler", false, loader);
                if (!noop.trySetAccessible() || !ChannelHandler.class.isAssignableFrom(noop.getDeclaringClass())
                        || !injector.isAssignableFrom(noop.getDeclaringClass())) noop = null;
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                // Essential is optional.
            }
            GET_CHANNEL = getChannel;
            NOOP = noop;
        }
    }
}
