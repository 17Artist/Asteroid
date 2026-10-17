package priv.seventeen.artist.asteroid.entity;

import org.bukkit.World;

/** Read-only Paper owner-thread lookup. Never requests or loads a missing chunk. */
public interface NativeOcclusionNMS {
    enum Cell { CLEAR, FULL, UNLOADED }
    @FunctionalInterface interface View { Cell at(int x,int y,int z); }
    /** Short-lived owner-thread view reuses its block cursor and last loaded chunk. Never retain across ticks. */
    View occlusionView(World world);
    default Cell occlusionCell(World world,int x,int y,int z){return occlusionView(world).at(x,y,z);}
}
