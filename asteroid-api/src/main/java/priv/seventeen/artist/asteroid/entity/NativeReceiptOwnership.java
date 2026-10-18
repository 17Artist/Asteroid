package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;

/**
 * Optional, owner-thread physical-receipt proof. This is NOT permission to trace, hurt, forward,
 * update or acquire an entity, and it does not replace a receipt's ordinary valid() predicate.
 *
 * A true result proves this exact native receipt is still registered/open, its native entity has
 * not been removed, its exact original owner is expectedOwner, and both remain in the same world.
 * It deliberately does not require the original owner's life state. The receipt's OWN native
 * registration, life/world/identity and close state remain mandatory, including failed removal
 * after close has already revoked native registration.
 *
 * Intended for an already authorized synchronous terminal-damage receipt; integrations must
 * separately retain their exact event/owner/geometry authority. Old providers simply omit this
 * additive interface. It does not grant support to NMS profiles which lack these receipts.
 */
public interface NativeReceiptOwnership {
    boolean retainedOwner(Entity expectedOwner);
}
