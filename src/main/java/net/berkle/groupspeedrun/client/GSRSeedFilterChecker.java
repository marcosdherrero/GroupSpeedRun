package net.berkle.groupspeedrun.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;

/**
 * Placement-only seed check (no full world generation).
 *
 * <p>Spawn is estimated at overworld 0,0 (vanilla spawn search is near origin). Nether
 * portal origin is spawn/8, also 0,0 with that estimate.
 *
 * <p>Required:
 * <ul>
 *   <li>Village within {@link #OVERWORLD_RADIUS_BLOCKS} (256). All vanilla village types
 *       include weaponsmith/toolsmith/armorer jigsaw pieces, so any village counts as
 *       blacksmith-capable.</li>
 *   <li>Ruined portal within 256, or lava in noise-column samples around spawn
 *       (32-block grid). Ruined portal stands in for lava/portal when the lake scan misses.</li>
 *   <li>Bastion within {@link #BASTION_RADIUS_BLOCKS} (192) of nether origin.</li>
 *   <li>Fortress within {@link #FORTRESS_RADIUS_BLOCKS} (256) of nether origin.</li>
 * </ul>
 * Stronghold closeness is optional and never blocks acceptance.
 */
public final class GSRSeedFilterChecker {

    public static final int OVERWORLD_RADIUS_BLOCKS = 256;
    public static final int BASTION_RADIUS_BLOCKS = 192;
    public static final int FORTRESS_RADIUS_BLOCKS = 256;
    private static final int OVERWORLD_RADIUS_CHUNKS = OVERWORLD_RADIUS_BLOCKS / 16;
    private static final int NETHER_SCAN_CHUNKS = FORTRESS_RADIUS_BLOCKS / 16;
    private static final int LAVA_SAMPLE_STEP = 32;
    private static final int BIOME_QUART_Y = 16;

    private GSRSeedFilterChecker() {}

    public static boolean passes(WorldCreationContext ctx, long seed) {
        if (ctx == null) return false;
        try {
            ChunkGenerator overworld = ctx.selectedDimensions().overworld();
            var netherStem = ctx.selectedDimensions().get(LevelStem.NETHER);
            if (netherStem.isEmpty()) return false;
            ChunkGenerator nether = netherStem.get().generator();
            if (!(overworld instanceof NoiseBasedChunkGenerator owNoise)
                    || !(nether instanceof NoiseBasedChunkGenerator netherNoise)) {
                return true;
            }

            RegistryAccess.Frozen access = ctx.worldgenLoadContext();
            HolderLookup<StructureSet> sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            var noiseParams = access.lookupOrThrow(Registries.NOISE);

            RandomState owRandom = RandomState.create(noiseParams, seed, owNoise.generatorSettings().value());
            RandomState netherRandom = RandomState.create(noiseParams, seed, netherNoise.generatorSettings().value());
            ChunkGeneratorStructureState owState = overworld.createState(sets, owRandom, seed);
            ChunkGeneratorStructureState netherState = nether.createState(sets, netherRandom, seed);
            BiomeResolver owBiomes = overworld.getBiomeSource().createResolver(
                    owRandom.createClimateSampler(SamplerContext.EMPTY_UNCACHED));
            BiomeResolver netherBiomes = nether.getBiomeSource().createResolver(
                    netherRandom.createClimateSampler(SamplerContext.EMPTY_UNCACHED));

            Holder<StructureSet> villages = sets.getOrThrow(BuiltinStructureSets.VILLAGES);
            Holder<StructureSet> portals = sets.getOrThrow(BuiltinStructureSets.RUINED_PORTALS);
            Holder<StructureSet> netherComplexes = sets.getOrThrow(BuiltinStructureSets.NETHER_COMPLEXES);

            if (!hasMatchingStructure(owState, villages, seed, 0, 0, OVERWORLD_RADIUS_CHUNKS,
                    OVERWORLD_RADIUS_BLOCKS, owBiomes)) {
                return false;
            }
            boolean portal = hasMatchingStructure(owState, portals, seed, 0, 0, OVERWORLD_RADIUS_CHUNKS,
                    OVERWORLD_RADIUS_BLOCKS, owBiomes);
            if (!portal && !hasNearbyLava(owNoise, owRandom)) {
                return false;
            }

            boolean bastion = false;
            boolean fortress = false;
            for (ChunkPos pos : structureChunks(netherState, netherComplexes, seed, 0, 0, NETHER_SCAN_CHUNKS)) {
                Holder<Structure> picked = pickStructure(netherComplexes.value(), pos, seed, netherBiomes);
                if (picked == null) continue;
                double dist = blockDistance(pos, 0, 0);
                if (picked.is(BuiltinStructures.BASTION_REMNANT) && dist <= BASTION_RADIUS_BLOCKS) {
                    bastion = true;
                }
                if (picked.is(BuiltinStructures.FORTRESS) && dist <= FORTRESS_RADIUS_BLOCKS) {
                    fortress = true;
                }
                if (bastion && fortress) return true;
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasMatchingStructure(
            ChunkGeneratorStructureState state,
            Holder<StructureSet> set,
            long seed,
            int originCx,
            int originCz,
            int rangeChunks,
            int maxBlockDist,
            BiomeResolver biomes
    ) {
        for (ChunkPos pos : structureChunks(state, set, seed, originCx, originCz, rangeChunks)) {
            if (blockDistance(pos, originCx * 16, originCz * 16) > maxBlockDist) continue;
            if (pickStructure(set.value(), pos, seed, biomes) != null) return true;
        }
        return false;
    }

    private static List<ChunkPos> structureChunks(
            ChunkGeneratorStructureState state,
            Holder<StructureSet> set,
            long seed,
            int originCx,
            int originCz,
            int rangeChunks
    ) {
        List<ChunkPos> out = new ArrayList<>();
        StructurePlacement placement = set.value().placement();
        if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
            return out;
        }
        int spacing = Math.max(1, spread.spacing());
        int minRegX = Math.floorDiv(originCx - rangeChunks, spacing) - 1;
        int maxRegX = Math.floorDiv(originCx + rangeChunks, spacing) + 1;
        int minRegZ = Math.floorDiv(originCz - rangeChunks, spacing) - 1;
        int maxRegZ = Math.floorDiv(originCz + rangeChunks, spacing) + 1;
        for (int rx = minRegX; rx <= maxRegX; rx++) {
            for (int rz = minRegZ; rz <= maxRegZ; rz++) {
                ChunkPos pos = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                if (pos.getChessboardDistance(originCx, originCz) > rangeChunks) continue;
                if (!placement.isStructureChunk(state, pos.x(), pos.z())) continue;
                out.add(pos);
            }
        }
        return out;
    }

    private static Holder<Structure> pickStructure(
            StructureSet set,
            ChunkPos pos,
            long seed,
            BiomeResolver biomes
    ) {
        var biome = biomes.getNoiseBiome((pos.getMinBlockX() + 8) >> 2, BIOME_QUART_Y, (pos.getMinBlockZ() + 8) >> 2);
        List<StructureSet.StructureSelectionEntry> valid = new ArrayList<>();
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            if (entry.structure().value().biomes().contains(biome)) {
                valid.add(entry);
            }
        }
        if (valid.isEmpty()) return null;
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, pos.x(), pos.z());
        int total = 0;
        for (StructureSet.StructureSelectionEntry entry : valid) {
            total += entry.weight();
        }
        int roll = random.nextInt(Math.max(1, total));
        for (StructureSet.StructureSelectionEntry entry : valid) {
            roll -= entry.weight();
            if (roll < 0) return entry.structure();
        }
        return valid.get(valid.size() - 1).structure();
    }

    private static boolean hasNearbyLava(NoiseBasedChunkGenerator generator, RandomState randomState) {
        LevelHeightAccessor height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
        int yMin = Math.max(generator.getMinY(), 0);
        int yMax = Math.min(64, generator.getMinY() + generator.getGenDepth());
        for (int x = -OVERWORLD_RADIUS_BLOCKS; x <= OVERWORLD_RADIUS_BLOCKS; x += LAVA_SAMPLE_STEP) {
            for (int z = -OVERWORLD_RADIUS_BLOCKS; z <= OVERWORLD_RADIUS_BLOCKS; z += LAVA_SAMPLE_STEP) {
                if (Math.hypot(x, z) > OVERWORLD_RADIUS_BLOCKS) continue;
                NoiseColumn column = generator.getBaseColumn(x, z, height, randomState);
                for (int y = yMin; y < yMax; y++) {
                    if (column.getBlock(y).getBlock() == Blocks.LAVA) return true;
                }
            }
        }
        return false;
    }

    private static double blockDistance(ChunkPos pos, int originX, int originZ) {
        int x = pos.getMinBlockX() + 8 - originX;
        int z = pos.getMinBlockZ() + 8 - originZ;
        return Math.hypot(x, z);
    }
}
