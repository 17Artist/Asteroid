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
package priv.seventeen.artist.asteroid.nms.v1_21_5;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import priv.seventeen.artist.asteroid.attribute.IAttributeItemNMS;

public class AttributeItemNMSImpl implements IAttributeItemNMS {

    private static final AttributeModifier.Operation[] OPERATIONS = AttributeModifier.Operation.values();

    @Override
    public void addModifier(ItemMeta meta, String attribute, String name,
                            double amount, int operation, String slot) {
        Attribute attr = resolveAttribute(attribute);
        if (attr == null) return;
        NamespacedKey key = NamespacedKey.fromString(name);
        if (key == null) {
            key = new NamespacedKey("asteroid", name.toLowerCase().replace(" ", "_"));
        }
        EquipmentSlotGroup slotGroup = slot != null ? resolveSlotGroup(slot) : EquipmentSlotGroup.ANY;
        AttributeModifier modifier = new AttributeModifier(key, amount, OPERATIONS[operation], slotGroup);
        meta.addAttributeModifier(attr, modifier);
    }

    @Override
    public void removeModifier(ItemMeta meta, String attribute) {
        Attribute attr = resolveAttribute(attribute);
        if (attr == null) return;
        meta.removeAttributeModifier(attr);
    }

    private Attribute resolveAttribute(String attribute) {
        NamespacedKey key = NamespacedKey.fromString(attribute);
        if (key == null) return null;
        return Registry.ATTRIBUTE.get(key);
    }

    private EquipmentSlotGroup resolveSlotGroup(String slot) {
        switch (slot.toLowerCase()) {
            case "hand":
            case "mainhand":
            case "main_hand":
                return EquipmentSlotGroup.MAINHAND;
            case "offhand":
            case "off_hand":
                return EquipmentSlotGroup.OFFHAND;
            case "head":
            case "helmet":
                return EquipmentSlotGroup.HEAD;
            case "chest":
            case "chestplate":
                return EquipmentSlotGroup.CHEST;
            case "legs":
            case "leggings":
                return EquipmentSlotGroup.LEGS;
            case "feet":
            case "boots":
                return EquipmentSlotGroup.FEET;
            case "armor":
                return EquipmentSlotGroup.ARMOR;
            case "any":
            default:
                return EquipmentSlotGroup.ANY;
        }
    }
}
