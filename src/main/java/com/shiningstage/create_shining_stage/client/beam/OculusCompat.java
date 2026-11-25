package com.shiningstage.create_shining_stage.client.beam;

import com.shiningstage.create_shining_stage.CreateShiningStage;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.fml.ModList;

/** Optional-mod gate kept free of Iris types so installations without Oculus can load safely. */
final class OculusCompat {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private OculusCompat() {
    }

    static void apply(BeamShaderInstance shader) {
        if (!ModList.get().isLoaded("oculus")) {
            return;
        }
        try {
            OculusIntegration.apply(shader);
        } catch (LinkageError | RuntimeException exception) {
            warnOnce(exception);
        }
    }

    static void restore(BeamShaderInstance shader) {
        if (!shader.isRouted()) {
            return;
        }
        try {
            // The route only exists after the optional integration has loaded successfully.
            OculusIntegration.restore(shader);
        } catch (LinkageError | RuntimeException exception) {
            shader.recoverFramebufferBindings();
            warnOnce(exception);
        }
    }

    static void warnOnce(Throwable exception) {
        if (WARNED.compareAndSet(false, true)) {
            CreateShiningStage.LOGGER.warn("Oculus beam target routing is unavailable; Oculus routing is disabled",
                    exception);
        }
    }

    static void warnOnce(String message) {
        if (WARNED.compareAndSet(false, true)) {
            CreateShiningStage.LOGGER.warn(message);
        }
    }
}
