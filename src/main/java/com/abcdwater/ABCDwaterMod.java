package com.abcdwater;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ABCDwaterMod.MODID)
public class ABCDwaterMod {
    public static final String MODID = "abcdwater";

    public ABCDwaterMod() {
        System.out.println("[ABCDwater] Loaded!");
        NeoForge.EVENT_BUS.register(new WaterClutchHandler());
    }
}

class WaterClutchHandler {
    private static final long CLUTCH_COOLDOWN_MS = 800;
    private long lastClutchTime = 0;

    // Water recycle state
    private BlockPos recycleTarget = null;
    private int recycleAttempts = 0;

    private boolean isSolidGround(BlockState state) {
        return !state.isAir() && !state.canBeReplaced();
    }
    private boolean isWaterBlock(BlockState state) {
        var b = state.getBlock();
        return b == Blocks.WATER || b == Blocks.BUBBLE_COLUMN;
    }
    private boolean isSafeLanding(BlockState state) {
        var b = state.getBlock();
        return b == Blocks.WATER || b == Blocks.BUBBLE_COLUMN
            || b == Blocks.KELP || b == Blocks.KELP_PLANT || b == Blocks.LAVA
            || b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS;
    }

    /**
     * Simulate one tick of entity motion to predict where the player will land.
     * Minecraft physics: gravity = 0.08/tick², drag = 0.98 vertical, 0.91 horizontal.
     */
    private double predictNextMotionY(double currentMotionY) {
        return (currentMotionY - 0.08) * 0.98;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var p = mc.player;
        var level = mc.level;

        // ========= RECYCLE: pick up water after landing =========
        if (recycleTarget != null) {
            boolean waterStillThere = isWaterBlock(level.getBlockState(recycleTarget));
            if (!waterStillThere) {
                recycleTarget = null;
                recycleAttempts = 0;
            } else if (p.onGround()) {
                // Player is on ground and water is nearby — try to pick it up
                Inventory inv = p.getInventory();
                int bucketSlot = -1;
                for (int i = 0; i < 9; i++) {
                    if (inv.getItem(i).is(Items.BUCKET)) {
                        bucketSlot = i;
                        break;
                    }
                }
                if (bucketSlot >= 0) {
                    // Debug: log block state at target
                    BlockState targetState = mc.level.getBlockState(recycleTarget);
                    boolean isWater = targetState.getBlock() == Blocks.WATER;

                    int prev = inv.getSelectedSlot();
                    if (bucketSlot != prev) inv.setSelectedSlot(bucketSlot);

                    // Try useItemOn with the BLOCK CENTER as hit location
                    Vec3 hitVec = Vec3.atCenterOf(recycleTarget);
                    // Try different directions - UP means the water surface
                    BlockHitResult bh = new BlockHitResult(hitVec, Direction.UP, recycleTarget, false);
                    InteractionResult result = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);

                    if (!result.consumesAction()) {
                        // Also try looking down (for when player is on ground next to water)
                        float px = p.getXRot(), py = p.getYRot();
                        p.setXRot(90.0f);
                        result = mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                        p.setXRot(px); p.setYRot(py);
                    }

                    if (result.consumesAction()) {
                        System.out.println("[ABCDwater] Water recycled! isWater=" + isWater);
                    } else {
                        Vec3 e = p.getEyePosition();
                        Vec3 bc = Vec3.atCenterOf(recycleTarget);
                        double dist = e.distanceTo(bc);
                        System.out.println("[ABCDwater] Recycle " + recycleAttempts
                            + " result=" + result
                            + " isWater=" + isWater
                            + " eye2center=" + String.format("%.2f", dist)
                            + " held=" + inv.getSelectedSlot());
                    }

                    if (bucketSlot != prev) inv.setSelectedSlot(prev);
                }

                if (++recycleAttempts > 40) { // ~2 seconds of trying
                    System.out.println("[ABCDwater] Recycle gave up after " + recycleAttempts + " attempts");
                    recycleTarget = null;
                    recycleAttempts = 0;
                }
            }
            // If !onGround, keep waiting — player hasn't landed yet
        }

        // ========= CLUTCH: predict fall damage and place water =========

        // Skip irrelevant states
        if (p.onGround()) return;
        if (p.isCreative() || p.isSpectator()) return;
        if (p.getAbilities().flying) return;
        if (p.isFallFlying()) return;
        if (p.isInWater() || p.isInLava()) return;
        if (p.fallDistance < 2.0f) return; // not enough fall to bother predicting yet

        // Dimension check: water only works in select dimensions
        if (level.dimension() == Level.NETHER) return;

        // Cooldown
        long now = System.currentTimeMillis();
        if (now - lastClutchTime < CLUTCH_COOLDOWN_MS) return;

        // — Predict next-tick landing and fall damage —
        // Minecraft tick physics:
        //   1. drag: motionY *= 0.98
        //   2. gravity: motionY -= 0.08
        //   3. move: position += motionY
        //   4. collision check → onGround, fallDistance
        //
        // At ClientTickEvent.Post, motionY is post-gravity (step 2 above),
        // and position is post-movement (step 3 above).

        double mY = p.getDeltaMovement().y;

        // Simulate next tick's physics:
        // Next tick will apply gravity then drag, then move
        double nextMY = (mY - 0.08) * 0.98;
        double nextY = p.getY() + nextMY;
        BlockPos nextFeet = BlockPos.containing(p.getX(), nextY, p.getZ());
        BlockPos belowFeet = nextFeet.below();

        // Will the player land next tick?
        boolean willLand = isSolidGround(level.getBlockState(belowFeet));
        if (!willLand && isSolidGround(level.getBlockState(nextFeet))) {
            willLand = true;
            belowFeet = nextFeet;
        }
        if (!willLand) return;

        // Is the landing spot water/lava?
        BlockState landingState = level.getBlockState(belowFeet);
        if (isSafeLanding(landingState)) {
            System.out.println("[ABCDwater] Landing on liquid, skip");
            return;
        }

        // Predicted fallDistance when landing:
        // fallDistance accumulates each tick: fallDistance += max(0, -next_motionY)
        double accumFall = p.fallDistance;
        if (nextMY < 0) accumFall += -nextMY;

        // Fall damage when fallDistance > 3.0
        if (accumFall <= 3.5) {
            System.out.println("[ABCDwater] Next-land fallDist="
                + String.format("%.2f", accumFall) + " < 3.5, safe");
            return;
        }

        // Check distance: can we reach the ground face by looking down?
        double eyeY = p.getEyeY();
        double faceY = belowFeet.getY() + 1.0;
        double distToFace = eyeY - faceY;
        if (distToFace > 4.4 || distToFace < 0.3) {
            System.out.println("[ABCDwater] Can't reach: " + String.format("%.2f", distToFace));
            return;
        }

        // Find water bucket
        Inventory inv = p.getInventory();
        int waterSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(Items.WATER_BUCKET)) {
                waterSlot = i;
                break;
            }
        }
        if (waterSlot < 0) return;

        int prevSlot = inv.getSelectedSlot();
        if (waterSlot != prevSlot) inv.setSelectedSlot(waterSlot);

        // Method 1: look down + useItem (raytrace to block face)
        float prevXRot = p.getXRot();
        float prevYRot = p.getYRot();
        p.setXRot(90.0f);
        InteractionResult result = mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        boolean success = result.consumesAction();

        if (!success) {
            // Method 2: fallback — direct block interaction
            double centerDist = p.getEyeY() - (belowFeet.getY() + 0.5);
            if (centerDist <= 4.4) {
                Vec3 hitVec = Vec3.atBottomCenterOf(belowFeet).add(0, 1, 0);
                BlockHitResult bh = new BlockHitResult(hitVec, Direction.UP, belowFeet, false);
                result = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
                success = result.consumesAction();
            }
        }

        p.setXRot(prevXRot);
        p.setYRot(prevYRot);
        if (waterSlot != prevSlot) inv.setSelectedSlot(prevSlot);

        if (success) {
            // Water placed — remember where for recycling (1 block above ground)
            recycleTarget = belowFeet.above();
            recycleAttempts = 0;
            System.out.println("[ABCDwater] CLUTCH! predicted fall="
                + String.format("%.2f", accumFall)
                + " waterAt=" + recycleTarget);
        } else {
            System.out.println("[ABCDwater] Clutch FAILED: " + result);
        }

        lastClutchTime = now;
    }
}
