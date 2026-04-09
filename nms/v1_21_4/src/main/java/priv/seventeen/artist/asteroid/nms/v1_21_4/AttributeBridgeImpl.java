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
package priv.seventeen.artist.asteroid.nms.v1_21_4;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import priv.seventeen.artist.asteroid.attribute.AttributeBridge;

import java.util.ArrayList;
import java.util.List;

public class AttributeBridgeImpl implements AttributeBridge {

    private static final AttributeModifier.Operation[] OPERATIONS = {
            AttributeModifier.Operation.ADD_VALUE,
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    };

    private net.minecraft.world.entity.LivingEntity toNMS(LivingEntity entity) {
        return ((CraftLivingEntity) entity).getHandle();
    }

    private Holder<Attribute> resolveAttribute(String attribute) {
        ResourceLocation loc = ResourceLocation.parse(attribute);
        return BuiltInRegistries.ATTRIBUTE.get(loc).orElse(null);
    }

    @Override
    public void setModifier(LivingEntity entity, String attribute, String key, double amount, int operation) {
        net.minecraft.world.entity.LivingEntity nms = toNMS(entity);
        Holder<Attribute> holder = resolveAttribute(attribute);
        if (holder == null) return;
        AttributeInstance instance = nms.getAttribute(holder);
        if (instance == null) return;
        ResourceLocation resourceLocation = ResourceLocation.parse(key);
        instance.removeModifier(resourceLocation);
        AttributeModifier modifier = new AttributeModifier(resourceLocation, amount, OPERATIONS[operation]);
        instance.addTransientModifier(modifier);
    }

    @Override
    public void removeModifier(LivingEntity entity, String attribute, String key) {
        net.minecraft.world.entity.LivingEntity nms = toNMS(entity);
        Holder<Attribute> holder = resolveAttribute(attribute);
        if (holder == null) return;
        AttributeInstance instance = nms.getAttribute(holder);
        if (instance == null) return;
        instance.removeModifier(ResourceLocation.parse(key));
    }

    @Override
    public void removeAllModifiers(LivingEntity entity, String prefix) {
        net.minecraft.world.entity.LivingEntity nms = toNMS(entity);
        for (Holder<Attribute> holder : BuiltInRegistries.ATTRIBUTE.asHolderIdMap()) {
            AttributeInstance instance = nms.getAttribute(holder);
            if (instance == null) continue;
            List<ResourceLocation> toRemove = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.id().toString().startsWith(prefix)) {
                    toRemove.add(modifier.id());
                }
            }
            for (ResourceLocation id : toRemove) {
                instance.removeModifier(id);
            }
        }
    }

    @Override
    public double getBaseValue(LivingEntity entity, String attribute) {
        net.minecraft.world.entity.LivingEntity nms = toNMS(entity);
        Holder<Attribute> holder = resolveAttribute(attribute);
        if (holder == null) return 0.0;
        AttributeInstance instance = nms.getAttribute(holder);
        if (instance == null) return 0.0;
        return instance.getBaseValue();
    }

    @Override
    public double getFinalValue(LivingEntity entity, String attribute) {
        net.minecraft.world.entity.LivingEntity nms = toNMS(entity);
        Holder<Attribute> holder = resolveAttribute(attribute);
        if (holder == null) return 0.0;
        AttributeInstance instance = nms.getAttribute(holder);
        if (instance == null) return 0.0;
        return instance.getValue();
    }

    @Override
    public boolean hasAttribute(LivingEntity entity, String attribute) {
        net.minecraft.world.entity.LivingEntity nms = toNMS(entity);
        Holder<Attribute> holder = resolveAttribute(attribute);
        if (holder == null) return false;
        return nms.getAttribute(holder) != null;
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
