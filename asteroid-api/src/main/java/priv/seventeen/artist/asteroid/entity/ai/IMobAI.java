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
package priv.seventeen.artist.asteroid.entity.ai;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Entity;

public interface IMobAI {

    void clearGoals(Mob mob);

    void clearTargetGoals(Mob mob);

    void addGoal(Mob mob, int priority, Object nmsGoal);

    void addTargetGoal(Mob mob, int priority, Object nmsGoal);

    void removeGoal(Mob mob, Class<?> goalClass);

    void removeTargetGoal(Mob mob, Class<?> goalClass);

    Object getNMSEntity(Entity entity);

    Object getGoalSelector(Mob mob);

    Object getTargetSelector(Mob mob);
}
