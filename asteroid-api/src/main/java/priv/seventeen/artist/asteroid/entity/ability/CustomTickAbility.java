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

import java.util.function.Consumer;

public class CustomTickAbility implements TickableAbility {

    private Consumer<CustomEntity> tickAction;

    public CustomTickAbility(Consumer<CustomEntity> tickAction) {
        this.tickAction = tickAction;
    }

    @Override
    public String id() { return "custom_tick"; }

    @Override
    public void onAttach(CustomEntity entity) {}

    @Override
    public void onDetach(CustomEntity entity) {}

    @Override
    public void tick(CustomEntity entity) {
        if (tickAction != null) {
            tickAction.accept(entity);
        }
    }

    public void setTickAction(Consumer<CustomEntity> tickAction) {
        this.tickAction = tickAction;
    }
}
