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
package priv.seventeen.artist.asteroid.nms.v1_19_4;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import priv.seventeen.artist.asteroid.attribute.IAttributeItemNMS;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AttributeItemNMSImpl implements IAttributeItemNMS {

    private static final AttributeModifier.Operation[] OPERATIONS = AttributeModifier.Operation.values();

    @Override
    public void addModifier(ItemMeta meta, String attribute, String name,
                            double amount, int operation, String slot) {
        Attribute attr = resolveAttribute(attribute);
        if (attr == null) return;
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        EquipmentSlot equipSlot = slot != null ? resolveSlot(slot) : null;
        AttributeModifier modifier = new AttributeModifier(uuid, name, amount, OPERATIONS[operation], equipSlot);
        meta.addAttributeModifier(attr, modifier);
    }

    @Override
    public void removeModifier(ItemMeta meta, String attribute) {
        Attribute attr = resolveAttribute(attribute);
        if (attr == null) return;
        meta.removeAttributeModifier(attr);
    }

    private Attribute resolveAttribute(String attribute) {
        String key = attribute.replace("minecraft:", "")
                .replace(".", "_")
                .toUpperCase();
        try {
            return Attribute.valueOf(key);
        } catch (IllegalArgumentException e) {
            for (Attribute attr : Attribute.values()) {
                if (attr.getKey().toString().equals(attribute)) {
                    return attr;
                }
            }
            return null;
        }
    }

    private EquipmentSlot resolveSlot(String slot) {
        switch (slot.toLowerCase()) {
            case "hand":
            case "mainhand":
            case "main_hand":
                return EquipmentSlot.HAND;
            case "offhand":
            case "off_hand":
                return EquipmentSlot.OFF_HAND;
            case "head":
            case "helmet":
                return EquipmentSlot.HEAD;
            case "chest":
            case "chestplate":
                return EquipmentSlot.CHEST;
            case "legs":
            case "leggings":
                return EquipmentSlot.LEGS;
            case "feet":
            case "boots":
                return EquipmentSlot.FEET;
            default:
                return null;
        }
    }
}
