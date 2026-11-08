package net.fitclient;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

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

    public static void tick(Minecraft mc) {
        if (!MODULE.enabled || mc.level == null || mc.player == null) return;

        ChunkPos center = mc.player.chunkPosition();
        int side = radius * 2 + 1;
        int total = side * side;
        for (int i = 0; i < perTick; i++) {
            cursor = (cursor + 1) % total;
            int cx = cx(center) - radius + cursor % side;
            int cz = cz(center) - radius + cursor / side;
            ChunkAccess access = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            long key = ChunkPos.asLong(cx, cz);
            if (!(access instanceof LevelChunk chunk)) { results.remove(key); continue; }
            results.put(key, scan(chunk));
        }

        if (++sinceRefresh >= 20) { // refresh clusters once per second
            sinceRefresh = 0;
            refresh(mc, center);
        }
    }

    private static Result scan(LevelChunk chunk) {
        int storage = 0;
        boolean spawner = false;
        for (BlockPos p : chunk.getBlockEntitiesPos()) {
            BlockEntity be = chunk.getBlockEntity(p);
            if (be == null) continue;
            BlockEntityType<?> t = be.getType();
            if (t == BlockEntityType.SPAWNER) spawner = true;
            else if (STORAGE.contains(t)) storage++;
        }
        if (spawner) storage = Math.max(0, storage - 3); // dungeon / mineshaft chests

        int flags = 0;
        for (LevelChunkSection s : chunk.getSections()) {
            if (s == null || s.hasOnlyAir()) continue;
            if (s.maybeHas(UNNATURAL_PRED)) flags++;
        }
        int score = storage * 3 + flags * 2;
        return new Result(storage, flags, spawner, score);
    }

    private static void refresh(Minecraft mc, ChunkPos center) {
        List<Hit> out = new ArrayList<>();
        for (Map.Entry<Long, Result> e : results.entrySet()) {
            Result r = e.getValue();
            if (r.score() <= 0) continue;
            ChunkPos cp = new ChunkPos(e.getKey());
            double cluster = r.score();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Result n = results.get(ChunkPos.asLong(cx(cp) + dx, cz(cp) + dz));
                    if (n != null) cluster += n.score() * 0.5;
                }
            }
            if (cluster >= threshold) out.add(new Hit(cp, (int) cluster, r.storage()));
        }
        out.sort((a, b) -> Integer.compare(b.clusterScore(), a.clusterScore()));
        hits = out.size() > 50 ? out.subList(0, 50) : out;

        for (Hit h : hits) {
            if (notified.add(ChunkPos.asLong(cx(h.pos()), cz(h.pos()))) && mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[Fit] sus chunk at " + (cx(h.pos()) * 16 + 8)
                        + ", " + (cz(h.pos()) * 16 + 8) + "  score " + h.clusterScore()), false);
            }
        }
    }

    /** ChunkPos component access in one place (record accessor in recent versions). */
    public static int cx(ChunkPos p) { return p.x(); }
    public static int cz(ChunkPos p) { return p.z(); }

    public static List<Hit> hits() { return hits; }

    public static Result resultAt(int cx, int cz) { return results.get(ChunkPos.asLong(cx, cz)); }

    public static void clear() {
        results.clear(); notified.clear(); hits = List.of(); cursor = 0;
    }
}
