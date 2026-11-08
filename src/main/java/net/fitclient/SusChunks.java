package net.fitclient;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.function.Predicate;

/**
 * Sus Chunks: scores loaded chunks by how likely they are to contain player-built stuff.
 *
 * Signals (all read from chunk data the server already sent you):
 *  - storage-type block entities (chests, barrels, shulkers, furnaces, hoppers, ...) weigh most
 *  - blocks that almost never generate naturally (glass, anvils, crafting tables, beds, ...)
 *  - spawners are treated as dungeons: a few chests in a spawner chunk are ignored
 *  - neighbouring chunks add to each other, so a spread-out base ranks above one lone chest
 *
 * Scores are heuristics. Villages and other structures also trigger them, so expect false positives.
 */
public class SusChunks {
    public static final Module MODULE = new Module("Sus Chunks", "Finds chunks that look player built", "F7");
    public static final Module HUD = new Module("Sus HUD", "Top suspicious chunks on screen", "");

    // settings (editable from the Settings tab)
    public static int radius = 16;      // chunks around you
    public static int perTick = 6;      // chunks scanned per tick
    public static int threshold = 12;   // cluster score that counts as "sus"

    public record Result(int storage, int flags, boolean spawner, int score) {}
    public record Hit(ChunkPos pos, int clusterScore, int storage) {}

    private static final Map<Long, Result> results = new HashMap<>();
    private static final Set<Long> notified = new HashSet<>();
    private static List<Hit> hits = List.of();
    private static int cursor = 0;
    private static int sinceRefresh = 0;

    private static final Set<BlockEntityType<?>> STORAGE = new HashSet<>(List.of(
            BlockEntityType.CHEST, BlockEntityType.TRAPPED_CHEST, BlockEntityType.BARREL,
            BlockEntityType.ENDER_CHEST, BlockEntityType.SHULKER_BOX, BlockEntityType.FURNACE,
            BlockEntityType.BLAST_FURNACE, BlockEntityType.SMOKER, BlockEntityType.HOPPER,
            BlockEntityType.DISPENSER, BlockEntityType.DROPPER, BlockEntityType.BREWING_STAND));

    private static final Set<Block> UNNATURAL = new HashSet<>(List.of(
            Blocks.GLASS, Blocks.GLASS_PANE, Blocks.CRAFTING_TABLE, Blocks.ANVIL, Blocks.ENCHANTING_TABLE,
            Blocks.BEACON, Blocks.TNT, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON,
            Blocks.REDSTONE_LAMP, Blocks.NOTE_BLOCK, Blocks.JUKEBOX, Blocks.LECTERN));

    private static final Predicate<BlockState> UNNATURAL_PRED = s -> UNNATURAL.contains(s.getBlock());

    public static void tick(MinecraftClient mc) {
        if (!MODULE.enabled || mc.world == null || mc.player == null) return;

        ChunkPos center = mc.player.getChunkPos();
        int side = radius * 2 + 1;
        int total = side * side;
        for (int i = 0; i < perTick; i++) {
            cursor = (cursor + 1) % total;
            int cx = center.x - radius + cursor % side;
            int cz = center.z - radius + cursor / side;
            WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
            long key = ChunkPos.toLong(cx, cz);
            if (chunk == null) { results.remove(key); continue; }
            results.put(key, scan(chunk));
        }

        if (++sinceRefresh >= 20) { // refresh clusters once per second
            sinceRefresh = 0;
            refresh(mc, center);
        }
    }

    private static Result scan(WorldChunk chunk) {
        int storage = 0;
        boolean spawner = false;
        for (BlockPos p : chunk.getBlockEntityPositions()) {
            BlockEntity be = chunk.getBlockEntity(p);
            if (be == null) continue;
            BlockEntityType<?> t = be.getType();
            if (t == BlockEntityType.MOB_SPAWNER) spawner = true;
            else if (STORAGE.contains(t)) storage++;
        }
        if (spawner) storage = Math.max(0, storage - 3); // dungeon / mineshaft chests

        int flags = 0;
        for (ChunkSection s : chunk.getSectionArray()) {
            if (s == null || s.isEmpty()) continue;
            if (s.hasAny(UNNATURAL_PRED)) flags++;
        }
        int score = storage * 3 + flags * 2;
        return new Result(storage, flags, spawner, score);
    }

    private static void refresh(MinecraftClient mc, ChunkPos center) {
        List<Hit> out = new ArrayList<>();
        for (Map.Entry<Long, Result> e : results.entrySet()) {
            Result r = e.getValue();
            if (r.score() <= 0) continue;
            ChunkPos cp = new ChunkPos(e.getKey());
            double cluster = r.score();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Result n = results.get(ChunkPos.toLong(cp.x + dx, cp.z + dz));
                    if (n != null) cluster += n.score() * 0.5;
                }
            }
            if (cluster >= threshold) out.add(new Hit(cp, (int) cluster, r.storage()));
        }
        out.sort((a, b) -> Integer.compare(b.clusterScore(), a.clusterScore()));
        hits = out.size() > 50 ? out.subList(0, 50) : out;

        for (Hit h : hits) {
            if (notified.add(h.pos().toLong()) && mc.player != null) {
                mc.player.sendMessage(Text.literal("[Fit] sus chunk at " + (h.pos().x * 16 + 8)
                        + ", " + (h.pos().z * 16 + 8) + "  score " + h.clusterScore()), false);
            }
        }
    }

    public static List<Hit> hits() { return hits; }

    public static Result resultAt(int cx, int cz) { return results.get(ChunkPos.toLong(cx, cz)); }

    public static void clear() {
        results.clear(); notified.clear(); hits = List.of(); cursor = 0;
    }
}
