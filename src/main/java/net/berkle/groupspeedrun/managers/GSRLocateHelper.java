package net.berkle.groupspeedrun.managers;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.chunk.LevelChunk;
import net.berkle.groupspeedrun.mixin.accessors.GSRSimpleStructurePieceAccessor;
import net.berkle.groupspeedrun.parameter.GSRLocatorParameters;
import net.berkle.groupspeedrun.parameter.GSRServerParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side helper to locate structures for the locator HUD.
 * Uses ServerLevel.findNearestMapStructure with structure tags (from GSR data pack).
 * Stronghold uses EYE_OF_ENDER_LOCATED and points to the portal room.
 * Ship locator prefers already-loaded structure pieces in the End, then falls back to worldgen locate.
 * Template id is the vanilla path {@code end_city/ship} (stored as templateName {@code ship}).
 */
public final class GSRLocateHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Locate");

    public static final String MISS_DIMENSION_UNLOADED = "dimension_unloaded";
    public static final String MISS_WRONG_DIMENSION = "wrong_dimension";
    public static final String MISS_NOT_FOUND = "not_found";

    /** Vanilla End City ship piece template: Identifier {@code minecraft:end_city/ship}, templateName {@code ship}. */
    public static final String END_SHIP_TEMPLATE_NAME = "ship";
    public static final String END_SHIP_TEMPLATE_PATH = "end_city/ship";

    private GSRLocateHelper() {}

    public record LocateResult(BlockPos pos, String missReason) {
        public boolean found() {
            return pos != null;
        }

        public static LocateResult found(BlockPos pos) {
            return new LocateResult(pos, null);
        }

        public static LocateResult miss(String reason) {
            return new LocateResult(null, reason != null ? reason : MISS_NOT_FOUND);
        }
    }

    /**
     * Locates the nearest structure of the given type from the given position.
     * For stronghold, returns the portal room center. For ship, returns the ship piece center (elytra).
     *
     * @param playerInTargetDimension true when the player is currently in the dimension being searched
     */
    public static LocateResult locate(ServerLevel world, String structureType, BlockPos from, boolean playerInTargetDimension) {
        if (world == null) return LocateResult.miss(MISS_DIMENSION_UNLOADED);
        if (from == null) return LocateResult.miss(MISS_NOT_FOUND);
        TagKey<Structure> tag = tagFor(structureType);
        if (tag == null) return LocateResult.miss(MISS_NOT_FOUND);
        try {
            BlockPos found;
            if ("ship".equalsIgnoreCase(structureType)) {
                found = locateNearestEndShip(world, from, tag);
            } else {
                found = world.findNearestMapStructure(tag, from, GSRLocatorParameters.LOCATE_RADIUS_CHUNKS, false);
                if (found != null && "stronghold".equalsIgnoreCase(structureType)) {
                    BlockPos portal = locateStrongholdPortal(world, found);
                    if (portal != null) found = portal;
                }
            }
            if (found != null) return LocateResult.found(found);
            return LocateResult.miss(playerInTargetDimension ? MISS_NOT_FOUND : MISS_WRONG_DIMENSION);
        } catch (Exception e) {
            LOGGER.warn("[GSR] Locate failed for {}: {}", structureType, e.getMessage());
            return LocateResult.miss(playerInTargetDimension ? MISS_NOT_FOUND : MISS_WRONG_DIMENSION);
        }
    }

    /**
     * Finds the nearest end city that has a ship piece and returns the ship (elytra) position.
     * Loaded pieces in the current dimension are searched first so a ship already on screen is found.
     */
    private static BlockPos locateNearestEndShip(ServerLevel world, BlockPos from, TagKey<Structure> endCityTag) {
        StructureManager accessor = world.structureManager();
        Structure endCityStructure = resolveEndCity(world);

        BlockPos loaded = locateShipInLoadedChunks(world, from, accessor, endCityStructure);
        if (loaded != null) return loaded;

        try {
            StructureStart atPlayer = endCityStructure != null
                    ? accessor.getStructureWithPieceAt(from.getX(), from.getY(), from.getZ(), endCityStructure)
                    : accessor.getStructureWithPieceAt(from, endCityTag);
            if (isUsableStart(atPlayer)) {
                BlockPos shipPos = extractShipPosition(atPlayer);
                if (shipPos != null) return shipPos;
            }
        } catch (Exception e) {
            LOGGER.debug("[GSR] Locate ship at player failed: {}", e.getMessage());
        }

        try {
            int radiusChunks = Math.min(GSRLocatorParameters.LOCATE_RADIUS_CHUNKS, GSRLocatorParameters.SHIP_LOCATE_SEARCH_RADIUS_CHUNKS);
            BlockPos firstCity = world.findNearestMapStructure(endCityTag, from, radiusChunks, false);
            if (firstCity != null) {
                world.getChunk(firstCity.getX() >> 4, firstCity.getZ() >> 4);
                StructureStart start = endCityStructure != null
                        ? accessor.getStructureWithPieceAt(firstCity.getX(), firstCity.getY(), firstCity.getZ(), endCityStructure)
                        : accessor.getStructureWithPieceAt(firstCity, endCityTag);
                if (isUsableStart(start)) {
                    BlockPos shipPos = extractShipPosition(start);
                    if (shipPos != null) return shipPos;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[GSR] Locate ship worldgen fallback failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Scans currently loaded chunks around {@code from} for end-city ship pieces.
     * Uses getChunkNow so already-visible ships are found without generating new terrain.
     */
    private static BlockPos locateShipInLoadedChunks(ServerLevel world, BlockPos from, StructureManager accessor, Structure endCityStructure) {
        int fromChunkX = from.getX() >> 4;
        int fromChunkZ = from.getZ() >> 4;
        int view = 12;
        try {
            var server = world.getServer();
            if (server != null) {
                view = Math.max(server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance());
            }
        } catch (Exception ignored) {
        }
        int radius = Math.min(Math.max(view + 4, 8), 32);
        double bestDistSq = Double.MAX_VALUE;
        BlockPos bestShip = null;
        Set<Long> seenStarts = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = world.getChunkSource().getChunkNow(fromChunkX + dx, fromChunkZ + dz);
                if (chunk == null) continue;
                ChunkPos chunkPos = chunk.getPos();
                List<StructureStart> starts;
                try {
                    if (endCityStructure != null) {
                        starts = accessor.startsForStructure(chunkPos.x(), chunkPos.z(), structure -> structure == endCityStructure);
                    } else {
                        starts = accessor.startsForStructure(chunkPos.x(), chunkPos.z(), structure -> true);
                    }
                } catch (Exception e) {
                    continue;
                }
                for (StructureStart start : starts) {
                    if (!isUsableStart(start)) continue;
                    BoundingBox box = start.getBoundingBox();
                    long key = ((long) box.minX() << 32) | (box.minZ() & 0xFFFFFFFFL);
                    if (!seenStarts.add(key)) continue;
                    BlockPos shipPos = extractShipPosition(start);
                    if (shipPos == null) continue;
                    double distSq = from.distSqr(shipPos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestShip = shipPos;
                    }
                }
            }
        }
        return bestShip;
    }

    private static Structure resolveEndCity(ServerLevel world) {
        try {
            return world.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(Identifier.withDefaultNamespace("end_city"));
        } catch (Exception e) {
            LOGGER.debug("[GSR] Could not resolve minecraft:end_city: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isUsableStart(StructureStart start) {
        return start != null && start != StructureStart.INVALID_START && start.isValid();
    }

    /**
     * Extracts ship piece center from an end city StructureStart, or null if no ship.
     * Matches templateName {@code ship} and Identifier path {@code end_city/ship}.
     */
    private static BlockPos extractShipPosition(StructureStart start) {
        if (!isUsableStart(start)) return null;
        for (StructurePiece piece : start.getPieces()) {
            if (!isEndShipPiece(piece)) continue;
            BoundingBox box = piece.getBoundingBox();
            int cx = (box.minX() + box.maxX()) / 2;
            int cy = (box.minY() + box.maxY()) / 2;
            int cz = (box.minZ() + box.maxZ()) / 2;
            return new BlockPos(cx, cy, cz);
        }
        return null;
    }

    private static boolean isEndShipPiece(StructurePiece piece) {
        if (piece instanceof TemplateStructurePiece simple) {
            try {
                String templateId = ((GSRSimpleStructurePieceAccessor) simple).gsr$getTemplateName();
                if (isEndShipTemplateName(templateId)) return true;
            } catch (Exception ignored) {
            }
        }
        String className = piece.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return className.contains("ship") && !className.contains("shipwreck");
    }

    /** True for {@code ship}, {@code end_city/ship}, or {@code minecraft:end_city/ship}. */
    static boolean isEndShipTemplateName(String name) {
        if (name == null || name.isEmpty()) return false;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("shipwreck")) return false;
        return n.equals(END_SHIP_TEMPLATE_NAME)
                || n.endsWith("/" + END_SHIP_TEMPLATE_NAME)
                || n.contains(END_SHIP_TEMPLATE_PATH);
    }

    private static TagKey<Structure> tagFor(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "fortress" -> TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("gsr", "fortress"));
            case "bastion" -> TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("gsr", "bastion_remnant"));
            case "stronghold" -> StructureTags.EYE_OF_ENDER_LOCATED;
            case "ship" -> TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("gsr", "end_city"));
            default -> null;
        };
    }

    /**
     * Finds the portal room center within the stronghold at the given position.
     * @return BlockPos at portal room center, or null if portal room not found
     */
    private static BlockPos locateStrongholdPortal(ServerLevel world, BlockPos strongholdPos) {
        try {
            StructureManager accessor = world.structureManager();
            StructureStart start = accessor.getStructureWithPieceAt(strongholdPos, StructureTags.EYE_OF_ENDER_LOCATED);
            if (!isUsableStart(start)) return null;
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof StrongholdPieces.PortalRoom portalRoom) {
                    BoundingBox box = portalRoom.getBoundingBox();
                    int cx = (box.minX() + box.maxX()) / 2;
                    int cy = (box.minY() + box.maxY()) / 2;
                    int cz = (box.minZ() + box.maxZ()) / 2;
                    return new BlockPos(cx, cy, cz);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[GSR] Could not find stronghold portal room: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Checks if the given position is inside any structure of the given type.
     * Used for split detection (fortress, bastion) when no locator is active.
     * Verifies the position is inside at least one structure piece's bounding box,
     * not just in a chunk with a structure reference (avoids false positives on Nether entry).
     */
    public static boolean isInStructure(ServerLevel world, BlockPos pos, String structureType) {
        if (world == null || pos == null) return false;
        TagKey<Structure> tag = tagFor(structureType);
        if (tag == null) return false;
        try {
            StructureStart start = world.structureManager().getStructureWithPieceAt(pos, tag);
            if (!isUsableStart(start)) return false;
            return isPosInsideStructurePiece(pos, start);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if the position is inside or within proximity of any child piece of the structure.
     * Uses SPLIT_STRUCTURE_PROXIMITY_BLOCKS so players near the structure edge still trigger.
     */
    private static boolean isPosInsideStructurePiece(BlockPos pos, StructureStart start) {
        int margin = GSRServerParameters.SPLIT_STRUCTURE_PROXIMITY_BLOCKS;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (box.minX() - margin <= x && x <= box.maxX() + margin
                    && box.minY() - margin <= y && y <= box.maxY() + margin
                    && box.minZ() - margin <= z && z <= box.maxZ() + margin) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given position is inside a structure of the given type that contains the stored target.
     * For ship: uses spherical distance (3D) – player within 100 blocks of ship position.
     */
    public static boolean isInTrackedStructure(ServerLevel world, BlockPos playerPos, String structureType, int storedX, int storedY, int storedZ) {
        if (world == null || playerPos == null) return false;
        if ("ship".equalsIgnoreCase(structureType)) {
            int dx = playerPos.getX() - storedX;
            int dy = playerPos.getY() - storedY;
            int dz = playerPos.getZ() - storedZ;
            int r = GSRLocatorParameters.SHIP_LOCATOR_TRIGGER_RADIUS_BLOCKS;
            return (long) dx * dx + (long) dy * dy + (long) dz * dz <= (long) r * r;
        }
        TagKey<Structure> tag = tagFor(structureType);
        if (tag == null) return false;
        try {
            var accessor = world.structureManager();
            StructureStart start = accessor.getStructureWithPieceAt(playerPos, tag);
            if (!isUsableStart(start)) return false;
            BoundingBox structureBox = start.getBoundingBox();
            return structureBox.minX() <= storedX && storedX <= structureBox.maxX()
                && structureBox.minZ() <= storedZ && storedZ <= structureBox.maxZ();
        } catch (Exception e) {
            return false;
        }
    }

    /** Overload for structures that only need XZ (fortress, bastion, stronghold). Pass 0 for storedY. */
    public static boolean isInTrackedStructure(ServerLevel world, BlockPos playerPos, String structureType, int storedX, int storedZ) {
        return isInTrackedStructure(world, playerPos, structureType, storedX, 0, storedZ);
    }
}
