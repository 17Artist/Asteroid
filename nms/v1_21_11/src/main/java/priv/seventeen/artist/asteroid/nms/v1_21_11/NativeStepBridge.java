package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import priv.seventeen.artist.asteroid.entity.NativeStepNMS;

import java.util.Objects;

/** One-shot native state change. No per-tick maintenance or model-state broadcast. */
final class NativeStepBridge {
    private static void check(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Step height requires the entity thread");}
    private static net.minecraft.world.entity.Entity handle(Entity entity){
        check();if(!(entity instanceof CraftEntity craft)||!entity.isValid())throw new IllegalArgumentException("Step host is not loaded");
        return craft.getHandle();
    }
    static double effective(Entity entity){return handle(entity).maxUpStep();}

    static double base(Entity entity) {
        var nativeEntity=handle(entity);
        if(!(nativeEntity instanceof LivingEntity living))return nativeEntity.maxUpStep();
        var attribute=living.getAttribute(Attributes.STEP_HEIGHT);
        return attribute==null?Attributes.STEP_HEIGHT.value().getDefaultValue():attribute.getBaseValue();
    }
    static NativeStepNMS.StepChange change(Entity entity, double height) {
        check();NativeStepNMS.validate(height);
        var nativeEntity=handle(entity);
        if(!(nativeEntity instanceof LivingEntity living))return NativeStepNMS.NO_CHANGE;
        var key=Attributes.STEP_HEIGHT;
        if(living.getAttribute(key)==null)living.getAttributes().registerAttribute(key);
        final var attribute=Objects.requireNonNull(living.getAttribute(key));
        final double previous=attribute.getBaseValue();
        if(Double.compare(previous,height)==0)return NativeStepNMS.NO_CHANGE;
        attribute.setBaseValue(height);
        return new NativeStepNMS.StepChange() {
            private boolean open=true;
            public boolean changed(){return true;}
            public boolean rollback(){
                check();if(!open)return false;open=false;
                if(nativeEntity.isRemoved()||living.getAttribute(key)!=attribute||Double.compare(attribute.getBaseValue(),height)!=0)return false;
                // Keep native attribute registration; restore only the base value this transaction owns.
                attribute.setBaseValue(previous);return true;
            }
        };
    }
}

