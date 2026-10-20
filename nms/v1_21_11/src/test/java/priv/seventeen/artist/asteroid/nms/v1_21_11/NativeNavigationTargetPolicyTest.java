package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual mapped goal-flag admission tests, not real entity movement/flying acceptance. */
class NativeNavigationTargetPolicyTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static final class UpstreamTarget extends Goal {
        UpstreamTarget(Flag... flags){replace(flags);}
        void replace(Flag... flags){var selected=EnumSet.noneOf(Flag.class);java.util.Collections.addAll(selected,flags);setFlags(selected);}
        @Override public boolean canUse(){return false;}
    }
    private static GoalSelector selector(Set<WrappedGoal> goals){
        GoalSelector selector=mock(GoalSelector.class);when(selector.getAvailableGoals()).thenReturn(goals);return selector;
    }
    private static boolean accepts(GoalSelector selector,boolean preserve,boolean facing)throws Exception {
        var method=NativeNavigationBridge.class.getDeclaredMethod("clearGoals",GoalSelector.class,boolean.class,boolean.class);
        method.setAccessible(true);return (boolean)method.invoke(null,selector,preserve,facing);
    }
    @Test void targetOnlyIsPreservedButNeverSilentlyAcceptedByStrictMode()throws Exception {
        var target=new UpstreamTarget(Goal.Flag.TARGET);var wrapped=new WrappedGoal(1,target);
        Set<WrappedGoal> goals=new LinkedHashSet<>();goals.add(wrapped);var selector=selector(goals);
        assertFalse(accepts(selector,false,false));assertFalse(accepts(selector,false,true));
        assertTrue(accepts(selector,true,false));assertTrue(accepts(selector,true,true));
        assertEquals(Set.of(wrapped),goals);assertSame(target,wrapped.getGoal());
        assertEquals(1L<<Goal.Flag.TARGET.ordinal(),wrapped.getFlags().getBackingSet());
        verify(selector,atLeastOnce()).getAvailableGoals();verifyNoMoreInteractions(selector);
    }
    @Test void everyMixedMovementWriterRejectsIncludingTargetFlag()throws Exception {
        for(Goal.Flag flag:new Goal.Flag[]{Goal.Flag.MOVE,Goal.Flag.LOOK,Goal.Flag.JUMP}){
            var selector=selector(Set.of(new WrappedGoal(1,new UpstreamTarget(Goal.Flag.TARGET,flag))));
            assertFalse(accepts(selector,true,true));
            if(flag==Goal.Flag.MOVE)assertFalse(accepts(selector,true,false));
        }
    }
    @Test void allForeignFlagCombinationsAdmitOnlyExactTarget()throws Exception {
        var flags=Goal.Flag.values();
        for(int bits=0;bits<(1<<flags.length);bits++){
            var selected=EnumSet.noneOf(Goal.Flag.class);
            for(int bit=0;bit<flags.length;bit++)if((bits&(1<<bit))!=0)selected.add(flags[bit]);
            var goal=new UpstreamTarget(selected.toArray(Goal.Flag[]::new));
            var selector=selector(Set.of(new WrappedGoal(1,goal)));
            boolean targetOnly=selected.equals(EnumSet.of(Goal.Flag.TARGET));
            assertEquals(targetOnly,accepts(selector,true,false),"ground "+selected);
            assertEquals(targetOnly,accepts(selector,true,true),"facing "+selected);
            assertFalse(accepts(selector,false,false),"strict "+selected);
        }
    }
    @Test void changedFlagsRevokeReadbackWithoutRemovingOrRewritingGoal()throws Exception {
        var target=new UpstreamTarget(Goal.Flag.TARGET);var wrapped=new WrappedGoal(1,target);
        Set<WrappedGoal> goals=new LinkedHashSet<>();goals.add(wrapped);var selector=selector(goals);
        assertTrue(accepts(selector,true,false));target.replace(Goal.Flag.TARGET,Goal.Flag.MOVE);
        assertFalse(accepts(selector,true,false));assertFalse(accepts(selector,true,true));
        assertEquals(Set.of(wrapped),goals);
        assertEquals((1L<<Goal.Flag.TARGET.ordinal())|(1L<<Goal.Flag.MOVE.ordinal()),wrapped.getFlags().getBackingSet());
        verify(selector,atLeastOnce()).getAvailableGoals();verifyNoMoreInteractions(selector);
    }
    @Test void selectorWorkBoundAndOriginalEmptyFacingContractRemain()throws Exception {
        assertTrue(accepts(selector(Set.of()),false,true));
        var goals=new LinkedHashSet<WrappedGoal>();
        for(int i=0;i<65;i++)goals.add(new WrappedGoal(i,new UpstreamTarget(Goal.Flag.TARGET)));
        assertFalse(accepts(selector(goals),true,false));assertFalse(accepts(selector(goals),true,true));
        assertEquals(65,goals.size());
    }
    @Test void portPinsPolicyAndProviderActuallyOverridesAllOptionalEntries()throws Exception {
        Class<?> port=Class.forName(NativeNavigationBridge.class.getName()+"$Port");
        var field=port.getDeclaredField("preserveTargets");assertEquals(boolean.class,field.getType());
        assertTrue(Modifier.isFinal(field.getModifiers()));assertNotNull(port.getDeclaredConstructor(Mob.class,boolean.class,boolean.class));
        assertNotNull(EntityNMSImpl.class.getDeclaredMethod("supportsTargetPreservingNavigation"));
        assertNotNull(EntityNMSImpl.class.getDeclaredMethod("supportsTargetPreservingRetainedHuskFlight"));
        assertNotNull(EntityNMSImpl.class.getDeclaredMethod("requireTargetPreservingFlightGoals",Plugin.class,org.bukkit.entity.Mob.class));
        assertNotNull(EntityNMSImpl.class.getDeclaredMethod("controlNavigationPreservingTargets",Plugin.class,org.bukkit.entity.Mob.class));
        assertNotNull(EntityNMSImpl.class.getDeclaredMethod("controlNavigationPreservingTargetsRetained",Plugin.class,org.bukkit.entity.Mob.class,Consumer.class));
        assertNotNull(EntityNMSImpl.class.getDeclaredMethod("prepareSkeletonPreservingTargets",Plugin.class,org.bukkit.entity.Mob.class));
    }
    @Test void pureFlightInspectionChecksMixedFlagsInEitherActualSelector() {
        for(boolean targetSelector:new boolean[]{false,true})for(Goal.Flag flag:new Goal.Flag[]{Goal.Flag.MOVE,Goal.Flag.LOOK,Goal.Flag.JUMP}){
            var primary=selector(Set.of());var targeting=selector(Set.of());
            var conflict=selector(Set.of(new WrappedGoal(1,new UpstreamTarget(Goal.Flag.TARGET,flag))));
            assertThrows(IllegalStateException.class,()->NativeNavigationBridge.requireFlightGoals(
                targetSelector?primary:conflict,targetSelector?conflict:targeting));
        }
        assertDoesNotThrow(()->NativeNavigationBridge.requireFlightGoals(
            selector(Set.of()),selector(Set.of(new WrappedGoal(1,new UpstreamTarget(Goal.Flag.TARGET))))));
    }
    @Test void liveTargetSelectorFlagJumpAndWrongSelectorPlacementRejectWithoutRepair() {
        var target=new UpstreamTarget(Goal.Flag.TARGET);var wrapped=new WrappedGoal(1,target);
        Set<WrappedGoal> goals=new LinkedHashSet<>();goals.add(wrapped);
        var primary=selector(Set.of());var targeting=selector(goals);
        assertDoesNotThrow(()->NativeNavigationBridge.requireFlightGoals(primary,targeting));
        for(Goal.Flag flag:new Goal.Flag[]{Goal.Flag.MOVE,Goal.Flag.LOOK,Goal.Flag.JUMP}){
            target.replace(Goal.Flag.TARGET,flag);
            assertThrows(IllegalStateException.class,()->NativeNavigationBridge.requireFlightGoals(primary,targeting));
            target.replace(flag); // A wrongly placed non-TARGET goal is not hidden by a typed Paper query.
            assertThrows(IllegalStateException.class,()->NativeNavigationBridge.requireFlightGoals(primary,targeting));
        }
        assertEquals(Set.of(wrapped),goals);assertSame(target,wrapped.getGoal());
        verify(targeting,atLeastOnce()).getAvailableGoals();verifyNoMoreInteractions(targeting);
    }
    @Test void strongerCapabilitiesRemainPaperOnlyAndRejectFolia(){
        try(var platform=mockStatic(PaperCompat.class)){
            platform.when(PaperCompat::isPaper).thenReturn(false);platform.when(PaperCompat::isFolia).thenReturn(false);
            assertFalse(NativeNavigationBridge.supportsTargetPreservingNavigation());
            assertFalse(NativeNavigationBridge.supportsTargetPreservingRetainedHuskFlight());
            platform.when(PaperCompat::isPaper).thenReturn(true);
            assertTrue(NativeNavigationBridge.supportsTargetPreservingNavigation());
            assertTrue(NativeNavigationBridge.supportsTargetPreservingRetainedHuskFlight());
            platform.when(PaperCompat::isFolia).thenReturn(true);
            assertFalse(NativeNavigationBridge.supportsTargetPreservingNavigation());
            assertFalse(NativeNavigationBridge.supportsTargetPreservingRetainedHuskFlight());
        }
    }
}
