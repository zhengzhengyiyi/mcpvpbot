package net.zhengzhengyiyi.mixin;

import carpet.script.value.EntityValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Function;

@Mixin(EntityValue.class)
public class EntityValueMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void forceVulnerable(CallbackInfo ci) {
        try {
            // Access the featureAccessors field using reflection
            Field field = EntityValue.class.getDeclaredField("featureAccessors");
            field.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<String, Function<Object, Object>> featureAccessors = (Map<String, Function<Object, Object>>) field.get(null);
            
            // Replace the "invulnerable" accessor to always return false
            // This prevents Carpet scripts from reading invulnerability as true
            featureAccessors.put("invulnerable", e -> false);
            
        } catch (Exception e) {
            // If reflection fails, silently continue
            // This shouldn't happen in normal operation
        }
    }
}
