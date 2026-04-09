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
package priv.seventeen.artist.asteroid.nms.v1_18_2;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftLivingEntity;
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
        net.minecraft.world.entity.ai.attributes.Attribute nmsAttr =
                Registry.ATTRIBUTE.get(new ResourceLocation(attribute));
        if (nmsAttr == null) return null;
        return nmsEntity.getAttribute(nmsAttr);
    }

    @Override
    public void setModifier(LivingEntity entity, String attribute, String key, double amount, int operation) {
        AttributeInstance instance = getInstance(entity, attribute);
        if (instance == null) return;
        UUID uuid = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        // Remove existing modifier with same UUID before adding
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
        for (net.minecraft.world.entity.ai.attributes.Attribute nmsAttr : Registry.ATTRIBUTE) {
            AttributeInstance instance = nmsEntity.getAttribute(nmsAttr);
            if (instance == null) continue;
            List<AttributeModifier> toRemove = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.getName().startsWith(prefix)) {
                    toRemove.add(modifier);
                }
            }
            for (AttributeModifier modifier : toRemove) {
                instance.removeModifier(modifier.getId());
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
        for (net.minecraft.world.entity.ai.attributes.Attribute attr : Registry.ATTRIBUTE) {
            ResourceLocation key = Registry.ATTRIBUTE.getKey(attr);
            if (key != null) {
                result.add(key.toString());
            }
        }
        return result;
    }
}
