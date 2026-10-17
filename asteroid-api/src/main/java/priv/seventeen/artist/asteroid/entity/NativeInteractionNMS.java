package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Runs an already authorized interaction, retaining native hand/item behavior.
 * The caller owns reach, visibility, rate limiting and Bukkit interact-at events. */
public interface NativeInteractionNMS {
    enum Result { PASS, CONSUMED, FAIL }
    Result interact(Player player,Entity target,EquipmentSlot hand);
}
