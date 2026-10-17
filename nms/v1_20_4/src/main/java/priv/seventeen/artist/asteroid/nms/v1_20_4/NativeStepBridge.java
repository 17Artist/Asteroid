package priv.seventeen.artist.asteroid.nms.v1_20_4;

import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import priv.seventeen.artist.asteroid.entity.NativeStepNMS;

/** One-shot native state change. No per-tick maintenance or model-state broadcast. */
final class NativeStepBridge {
    private static void check(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Step height requires the entity thread");}
    private static net.minecraft.world.entity.Entity handle(Entity entity){
        check();if(!(entity instanceof CraftEntity craft)||!entity.isValid())throw new IllegalArgumentException("Step host is not loaded");
        return craft.getHandle();
    }
    static double effective(Entity entity){return handle(entity).maxUpStep();}

    static double base(Entity entity) { return handle(entity).maxUpStep(); }
    static NativeStepNMS.StepChange change(Entity entity, double height) {
        check();NativeStepNMS.validate(height);
        var nativeEntity=handle(entity);
        final float previous=nativeEntity.maxUpStep(), applied=(float)height;
        if(Float.compare(previous,applied)==0)return NativeStepNMS.NO_CHANGE;
        nativeEntity.setMaxUpStep(applied);
        return new NativeStepNMS.StepChange() {
            private boolean open=true;
            public boolean changed(){return true;}
            public boolean rollback(){
                check();if(!open)return false;open=false;
                if(nativeEntity.isRemoved()||Float.compare(nativeEntity.maxUpStep(),applied)!=0)return false;
                nativeEntity.setMaxUpStep(previous);return true;
            }
        };
    }
}

