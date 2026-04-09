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
package priv.seventeen.artist.asteroid.entity.ability;

import priv.seventeen.artist.asteroid.entity.CustomEntity;

public class FollowOwnerAbility implements Ability {

    private boolean enabled = true;

    private boolean removeOnOwnerInvalid = true;

    public FollowOwnerAbility() {}

    public FollowOwnerAbility(boolean removeOnOwnerInvalid) {
        this.removeOnOwnerInvalid = removeOnOwnerInvalid;
    }

    @Override
    public String id() { return "follow_owner"; }

    @Override
    public void onAttach(CustomEntity entity) {}

    @Override
    public void onDetach(CustomEntity entity) {}

    @Override
    public boolean requiresTick() { return false; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isRemoveOnOwnerInvalid() { return removeOnOwnerInvalid; }

    public void setRemoveOnOwnerInvalid(boolean removeOnOwnerInvalid) {
        this.removeOnOwnerInvalid = removeOnOwnerInvalid;
    }
}
