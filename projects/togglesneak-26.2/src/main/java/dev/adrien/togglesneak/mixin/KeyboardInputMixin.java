package dev.adrien.togglesneak.mixin;

import dev.adrien.togglesneak.ToggleSneakClient;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void togglesneak$afterKeyboardInput(CallbackInfo ci) {
        final KeyboardInput self = (KeyboardInput) (Object) this;

        if (!ToggleSneakClient.shouldApplyForcedSneak(self)) {
            return;
        }

        final Input input = self.keyPresses;
        if (input.shift()) {
            return;
        }

        // Preserve every vanilla movement bit and only OR in sneak.
        self.keyPresses = new Input(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                true,
                input.sprint()
        );
    }
}
