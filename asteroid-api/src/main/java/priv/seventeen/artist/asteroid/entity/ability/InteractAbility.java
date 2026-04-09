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

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import priv.seventeen.artist.asteroid.entity.CustomEntity;

public class InteractAbility implements Ability {

    @FunctionalInterface
    public interface Callback {
        void onInteract(@NotNull Entity player, boolean mainHand);
    }

    private Callback callback;

    public InteractAbility(Callback callback) {
        this.callback = callback;
    }

    @Override
    public String id() { return "interact"; }

    @Override
    public void onAttach(CustomEntity entity) {}

    @Override
    public void onDetach(CustomEntity entity) {}

    public Callback getCallback() { return callback; }

    public void setCallback(Callback callback) { this.callback = callback; }
}
