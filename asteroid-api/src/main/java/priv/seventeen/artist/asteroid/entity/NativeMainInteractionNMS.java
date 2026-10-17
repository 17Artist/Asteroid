package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Native phases for a caller-authorized root click. The caller owns Bukkit events, reach,
 * audience and cancellation/consumption sequencing; old child interaction APIs are unchanged.
 */
public interface NativeMainInteractionNMS {
    /** Opaque, owner-thread, one-use native item receipt. Never persist or share between hands. */
    interface Phase { }
    /** Before the phase's Bukkit event: validate native guards and capture held reference + copy; null rejects. */
    Phase prepareMainInteraction(Player player, Entity target, EquipmentSlot hand);
    /** Claim one receipt once, then native At and its listener completion. Local point has packet-float precision. */
    NativeInteractionNMS.Result interactMainAt(Phase phase, double x, double y, double z);
    /** Claim a fresh pre-Entity-event receipt; Player.interactOn still reads the actual post-event held item. */
    NativeInteractionNMS.Result interactMain(Phase phase);
}
