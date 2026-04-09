/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package priv.seventeen.artist.asteroid.nms.v1_20_6;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import priv.seventeen.artist.asteroid.attribute.AttributeBridge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AttributeBridgeImpl implements AttributeBridge {

    private static final AttributeModifier.Operation[] OPERATIONS = AttributeModifier.Operation.values();

    private AttributeInstance getInstance(LivingEntity entity, String attribute) {
        net.minecraft.world.entity.LivingEntity nmsEntity = ((CraftLivingEntity) entity).getHandle();
        Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute> holder =
                BuiltInRegistries.ATTRIBUTE.getHolder(new ResourceLocation(attribute)).orElse(null);
        if (holder == null) return null;
        return nmsEntity.getAttribute(holder);
    }

    @Override
    public void setModifier(LivingEntity entity, String attribute, String key, double amount, int operation) {
        AttributeInstance instance = getInstance(entity, attribute);
        if (instance == null) return;
        UUID uuid = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(uuid);
        instance.addTransientModifier(new AttributeModifier(uuid, key, amount, OPERATIONS[operation]));
    }

    @Override
    public void removeModifier(LivingEntity entity, String attribute, String key) {
        AttributeInstance instance = getInstance(entity, attribute);
        if (instance == null) return;
        UUID uuid = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(uuid);
    }

    @Override
    public void removeAllModifiers(LivingEntity entity, String prefix) {
        net.minecraft.world.entity.LivingEntity nmsEntity = ((CraftLivingEntity) entity).getHandle();
        for (net.minecraft.world.entity.ai.attributes.Attribute nmsAttr : BuiltInRegistries.ATTRIBUTE) {
            Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute> holder =
                    BuiltInRegistries.ATTRIBUTE.getHolder(BuiltInRegistries.ATTRIBUTE.getKey(nmsAttr)).orElse(null);
            if (holder == null) continue;
            AttributeInstance instance = nmsEntity.getAttribute(holder);
            if (instance == null) continue;
            List<AttributeModifier> toRemove = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.name.startsWith(prefix)) {
                    toRemove.add(modifier);
                }
            }
            for (AttributeModifier modifier : toRemove) {
                instance.removeModifier(modifier.id());
            }
        }
    }

    @Override
    public double getBaseValue(LivingEntity entity, String attribute) {
        AttributeInstance instance = getInstance(entity, attribute);
        return instance != null ? instance.getBaseValue() : 0.0;
    }

    @Override
    public double getFinalValue(LivingEntity entity, String attribute) {
        AttributeInstance instance = getInstance(entity, attribute);
        return instance != null ? instance.getValue() : 0.0;
    }

    @Override
    public boolean hasAttribute(LivingEntity entity, String attribute) {
        return getInstance(entity, attribute) != null;
    }

    @Override
    public List<String> getAvailableAttributes() {
        List<String> result = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.ATTRIBUTE.keySet()) {
            result.add(key.toString());
        }
        return result;
    }
}
