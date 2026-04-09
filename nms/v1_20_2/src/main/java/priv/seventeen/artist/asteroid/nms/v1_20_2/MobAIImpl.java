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
package priv.seventeen.artist.asteroid.nms.v1_20_2;

import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import priv.seventeen.artist.asteroid.entity.ai.IMobAI;

import java.util.Iterator;
import java.util.Set;

public class MobAIImpl implements IMobAI {

    @Override
    public void clearGoals(Mob mob) {
        net.minecraft.world.entity.Mob nmsMob = getNMSMob(mob);
        getAvailableGoals(nmsMob.goalSelector).clear();
    }

    @Override
    public void clearTargetGoals(Mob mob) {
        net.minecraft.world.entity.Mob nmsMob = getNMSMob(mob);
        getAvailableGoals(nmsMob.targetSelector).clear();
    }

    @Override
    public void addGoal(Mob mob, int priority, Object nmsGoal) {
        net.minecraft.world.entity.Mob nmsMob = getNMSMob(mob);
        nmsMob.goalSelector.addGoal(priority, (net.minecraft.world.entity.ai.goal.Goal) nmsGoal);
    }

    @Override
    public void addTargetGoal(Mob mob, int priority, Object nmsGoal) {
        net.minecraft.world.entity.Mob nmsMob = getNMSMob(mob);
        nmsMob.targetSelector.addGoal(priority, (net.minecraft.world.entity.ai.goal.Goal) nmsGoal);
    }

    @Override
    public void removeGoal(Mob mob, Class<?> goalClass) {
        net.minecraft.world.entity.Mob nmsMob = getNMSMob(mob);
        removeGoalByClass(nmsMob.goalSelector, goalClass);
    }

    @Override
    public void removeTargetGoal(Mob mob, Class<?> goalClass) {
        net.minecraft.world.entity.Mob nmsMob = getNMSMob(mob);
        removeGoalByClass(nmsMob.targetSelector, goalClass);
    }

    @Override
    public Object getNMSEntity(Entity entity) {
        return ((CraftEntity) entity).getHandle();
    }

    @Override
    public Object getGoalSelector(Mob mob) {
        return getNMSMob(mob).goalSelector;
    }

    @Override
    public Object getTargetSelector(Mob mob) {
        return getNMSMob(mob).targetSelector;
    }

    private net.minecraft.world.entity.Mob getNMSMob(Mob mob) {
        return ((CraftMob) mob).getHandle();
    }

    private Set<WrappedGoal> getAvailableGoals(GoalSelector selector) {
        return selector.getAvailableGoals();
    }

    private void removeGoalByClass(GoalSelector selector, Class<?> goalClass) {
        Set<WrappedGoal> goals = getAvailableGoals(selector);
        Iterator<WrappedGoal> it = goals.iterator();
        while (it.hasNext()) {
            WrappedGoal wrapped = it.next();
            if (goalClass.isInstance(wrapped.getGoal())) {
                it.remove();
            }
        }
    }
}
