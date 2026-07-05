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
    private static final long COOLDOWN_MS = 600;
    private long lastUseTime = 0;

    // State for water recycling after landing
    private BlockPos pendingRecyclePos = null;
    private int recycleTicks = 0;

    private boolean isSafeLiquid(BlockState state) {
        var b = state.getBlock();
        return b == Blocks.WATER || b == Blocks.BUBBLE_COLUMN
            || b == Blocks.KELP || b == Blocks.KELP_PLANT
            || b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS;
    }

    /** Try to pick up a water source with an empty bucket. Returns true if succeeded. */
    private boolean tryRecycleWater(Minecraft mc, BlockPos waterPos) {
        var p = mc.player;
        if (p == null) return false;

        BlockState state = mc.level.getBlockState(waterPos);
        if (state.getBlock() != Blocks.WATER && state.getBlock() != Blocks.BUBBLE_COLUMN) {
            return false; // water gone
        }

        Inventory inv = p.getInventory();
        int bucketSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(Items.BUCKET)) {
                bucketSlot = i;
                break;
            }
        }
        if (bucketSlot < 0) return false;

        int prevSlot = inv.getSelectedSlot();
        if (bucketSlot != prevSlot) inv.setSelectedSlot(bucketSlot);

        Vec3 hitVec = Vec3.atBottomCenterOf(waterPos);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, waterPos, false);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);

        if (bucketSlot != prevSlot) inv.setSelectedSlot(prevSlot);
        System.out.println("[ABCDwater] Recycled water at " + waterPos);
        return true;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var p = mc.player;
        var level = mc.level;

        // === RECYCLE PHASE ===
        // After a water clutch, try to pick up the water when the player is on ground.
        if (pendingRecyclePos != null) {
            recycleTicks++;
            // Check if the water we placed is still there
            BlockState waterState = mc.level.getBlockState(pendingRecyclePos);
            boolean waterExists = waterState.getBlock() == Blocks.WATER || waterState.getBlock() == Blocks.BUBBLE_COLUMN;
            
            // After landing, wait a few ticks then try to pick up
            if (p.onGround() && recycleTicks > 3) {
                if (!waterExists) {
                    System.out.println("[ABCDwater] Recycle: water already gone at " + pendingRecyclePos);
                    pendingRecyclePos = null;
                    recycleTicks = 0;
                } else if (tryRecycleWater(mc, pendingRecyclePos)) {
                    System.out.println("[ABCDwater] Recycle: success! Water picked up.");
                    pendingRecyclePos = null;
                    recycleTicks = 0;
                } else {
                    System.out.println("[ABCDwater] Recycle: failed at " + pendingRecyclePos
                        + " onGround=" + p.onGround() + " tick=" + recycleTicks);
                }
            } else if (!p.onGround() && recycleTicks > 100) {
                // Player never landed? Timeout
                System.out.println("[ABCDwater] Recycle: timeout (never landed)");
                pendingRecyclePos = null;
                recycleTicks = 0;
            }
        }

        // === CLUTCH PHASE ===
        if (p.onGround()) return;
        if (p.isCreative() || p.isSpectator()) return;
        if (p.getAbilities().flying) return;
        if (p.isFallFlying()) return;
        if (p.isInWater() || p.isInLava()) return;

        // ❌ Nether check: water evaporates instantly, so never clutch here
        if (level.dimension() == Level.NETHER) return;

        long now = System.currentTimeMillis();
        if (now - lastUseTime < COOLDOWN_MS) return;
        if (p.fallDistance < 3.5f) return;

        // Scan downward: find the first block the player will actually hit
        // Water is NOT replaceable for our purposes — it breaks the fall.
        // But 'canBeReplaced()' returns true for water, so we must check liquids first.
        var cursor = p.blockPosition().mutable();
        int blocksDown = 0;
        boolean landingOnLiquid = false;

        for (int i = 0; i < 16; i++) {
            cursor.move(Direction.DOWN);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) continue;

            // First non-air block: check if it's liquid that can break the fall
            if (isSafeLiquid(state)) {
                blocksDown = i + 1;
                landingOnLiquid = true;
                System.out.println("[ABCDwater] Water below at " + cursor.getY() + " — no clutch needed");
                break;
            }
            // Solid or replaceable non-liquid block (grass, snow, etc.)
            if (!state.canBeReplaced()) {
                blocksDown = i + 1;
                landingOnLiquid = false;
                break;
            }
            // Replaceable non-liquid — keep scanning (tall grass, etc.)
        }

        if (blocksDown == 0) return;
        if (landingOnLiquid) return;

        // Check if the ground block face is within reach (looking straight down)
        double eyeY = p.getEyeY();
        double faceY = cursor.getY() + 1.0;
        double distToFace = eyeY - faceY;

        if (distToFace > 4.4 || distToFace < 0.3) return;

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

        // Look down and right-click — server raytraces from eye → hits ground face → places water
        float prevXRot = p.getXRot();
        float prevYRot = p.getYRot();
        p.setXRot(90.0f);

        // Method 1: right-click (look down) — simulates player MLG
        var result = mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        boolean success = result.consumesAction();

        if (!success) {
            // Method 2: if raytrace failed, try direct block interaction
            // Only works if the ground block center is within 4.5 reach
            double centerDist = p.getEyeY() - (cursor.getY() + 0.5);
            if (centerDist <= 4.4) {
                Vec3 hitVec = Vec3.atBottomCenterOf(cursor).add(0, 1, 0);
                BlockHitResult bh = new BlockHitResult(hitVec, Direction.UP, cursor.immutable(), false);
                result = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
                success = result.consumesAction();
            }
        }

        p.setXRot(prevXRot);
        p.setYRot(prevYRot);

        if (waterSlot != prevSlot) inv.setSelectedSlot(prevSlot);

        if (success) {
            pendingRecyclePos = cursor.immutable().above();
            System.out.println("[ABCDwater] Clutch OK! dist=" + String.format("%.2f", distToFace)
                + " waterAt=" + pendingRecyclePos + " method=" + (result.consumesAction() ? "useItem" : "useItemOn"));
        } else {
            System.out.println("[ABCDwater] Clutch FAILED! dist=" + String.format("%.2f", distToFace)
                + " result=" + result);
        }
        recycleTicks = 0;
        lastUseTime = now;
    }
}
