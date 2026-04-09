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
package priv.seventeen.artist.asteroid.attribute;

import org.bukkit.entity.LivingEntity;

import java.util.List;

public interface AttributeBridge {

    void setModifier(LivingEntity entity, String attribute, String key, double amount, int operation);

    void removeModifier(LivingEntity entity, String attribute, String key);

    void removeAllModifiers(LivingEntity entity, String prefix);

    double getBaseValue(LivingEntity entity, String attribute);

    double getFinalValue(LivingEntity entity, String attribute);

    boolean hasAttribute(LivingEntity entity, String attribute);

    List<String> getAvailableAttributes();
}
