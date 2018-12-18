package com.github.intellectualsites.plotsquared.bukkit.util.block;

import com.github.intellectualsites.plotsquared.bukkit.util.LegacyMappings;
import com.github.intellectualsites.plotsquared.plot.object.LegacyPlotBlock;
import com.github.intellectualsites.plotsquared.plot.object.PlotBlock;
import com.github.intellectualsites.plotsquared.plot.object.StringPlotBlock;
import com.github.intellectualsites.plotsquared.plot.util.MainUtil;
import com.github.intellectualsites.plotsquared.plot.util.StringMan;
import com.github.intellectualsites.plotsquared.plot.util.block.BasicLocalBlockQueue;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class BukkitLocalQueue<T> extends BasicLocalBlockQueue<T> {

    private Field fieldNeighbors;
    private Method chunkGetHandle;

    public BukkitLocalQueue(String world) {
        super(world);
    }

    @Override public LocalChunk<T> getLocalChunk(int x, int z) {
        return (LocalChunk<T>) new BasicLocalChunk(this, x, z) {
            // Custom stuff?
        };
    }

    @Override public void optimize() {

    }

    @Override public PlotBlock getBlock(int x, int y, int z) {
        World worldObj = Bukkit.getWorld(getWorld());
        Block block = worldObj.getBlockAt(x, y, z);
        if (block == null) {
            return PlotBlock.get(0, 0);
        }
        // int id = block.getTypeId();
        // if (id == 0) {
        //     return PlotBlock.get(0, 0);
        // }
        // return PlotBlock.get(id, block.getData());
        return PlotBlock.get(block.getType().toString());
    }

    @Override public void refreshChunk(int x, int z) {
        World worldObj = Bukkit.getWorld(getWorld());
        worldObj.refreshChunk(x, z);
    }

    @Override public void fixChunkLighting(int x, int z) {
        // Do nothing
    }

    @Override public final void regenChunk(int x, int z) {
        World worldObj = Bukkit.getWorld(getWorld());
        worldObj.regenerateChunk(x, z);
    }

    @Override public final void setComponents(LocalChunk<T> lc) {
        setBlocks(lc);
        setBiomes(lc);
    }

    public World getBukkitWorld() {
        return Bukkit.getWorld(getWorld());
    }

    public Chunk getChunk(int x, int z) {
        return getBukkitWorld().getChunkAt(x, z);
    }

    public void setBlocks(LocalChunk<T> lc) {
        World worldObj = Bukkit.getWorld(getWorld());
        Chunk chunk = worldObj.getChunkAt(lc.getX(), lc.getZ());
        chunk.load(true);
        for (int layer = 0; layer < lc.blocks.length; layer++) {
            PlotBlock[] blocksLayer = (PlotBlock[]) lc.blocks[layer];
            if (blocksLayer != null) {
                for (int j = 0; j < blocksLayer.length; j++) {
                    PlotBlock block = blocksLayer[j];
                    if (block != null) {
                        int x = MainUtil.x_loc[layer][j];
                        int y = MainUtil.y_loc[layer][j];
                        int z = MainUtil.z_loc[layer][j];
                        Block existing = chunk.getBlock(x, y, z);
                        // int existingId = existing.getTypeId();
                        // if (existingId == block.id) {
                        //    if (existingId == 0) {
                        //        continue;
                        //    }
                        //    if (existing.getData() == block.data) {
                        //        continue;
                        //    }
                        //}
                        if (equals(block, existing)) {
                            continue;
                        }
                        setMaterial(block, existing);
                    }
                }
            }
        }
    }

    private void setMaterial(@NonNull final PlotBlock plotBlock, @NonNull final Block block) {
        if (plotBlock instanceof StringPlotBlock) {
            final Material material = Material.getMaterial(((StringPlotBlock) plotBlock).getItemId().toLowerCase(
                Locale.ENGLISH));
            if (material == null) {
                throw new IllegalStateException(String.format("Could not find material that matches %s", block.toString()));
            }
            block.setType(material, false);
        } else {
            final LegacyPlotBlock legacyPlotBlock = (LegacyPlotBlock) plotBlock;
            block.setType(LegacyMappings.fromIdAndData(legacyPlotBlock.getId(), legacyPlotBlock.getData()).getMaterial());
            // block.setTypeIdAndData(legacyPlotBlock.getId(), legacyPlotBlock.getData(), false);
        }
    }

    private boolean equals(@NonNull final PlotBlock plotBlock, @NonNull final Block block) {
        if (plotBlock instanceof StringPlotBlock) {
            return ((StringPlotBlock) plotBlock).idEquals(block.getType().name());
        }
        final LegacyPlotBlock legacyPlotBlock = (LegacyPlotBlock) plotBlock;
        return LegacyMappings.fromLegacyId(((LegacyPlotBlock) plotBlock).id).getMaterial() == block.getType() && (legacyPlotBlock.id == 0 || legacyPlotBlock.data == block.getData());
    }

    public void setBiomes(LocalChunk<T> lc) {
        if (lc.biomes != null) {
            World worldObj = Bukkit.getWorld(getWorld());
            int bx = lc.getX() << 4;
            int bz = lc.getX() << 4;
            String last = null;
            Biome biome = null;
            for (int x = 0; x < lc.biomes.length; x++) {
                String[] biomes2 = lc.biomes[x];
                if (biomes2 != null) {
                    for (int y = 0; y < biomes2.length; y++) {
                        String biomeStr = biomes2[y];
                        if (biomeStr != null) {
                            if (last == null || !StringMan.isEqual(last, biomeStr)) {
                                biome = Biome.valueOf(biomeStr.toUpperCase());
                            }
                            worldObj.setBiome(bx, bz, biome);
                        }
                    }
                }
            }
        }
    }

    /**
     * Exploiting a bug in the vanilla lighting algorithm for faster block placement
     * - Could have been achieved without reflection by force unloading specific chunks
     * - Much faster just setting the variable manually though
     *
     * @param chunk
     * @return
     */
    protected Object[] disableLighting(Chunk chunk) {
        try {
            if (chunkGetHandle == null) {
                chunkGetHandle = chunk.getClass().getDeclaredMethod("getHandle");
                chunkGetHandle.setAccessible(true);
            }
            Object nmsChunk = chunkGetHandle.invoke(chunk);
            if (fieldNeighbors == null) {
                fieldNeighbors = nmsChunk.getClass().getDeclaredField("neighbors");
                fieldNeighbors.setAccessible(true);
            }
            Object value = fieldNeighbors.get(nmsChunk);
            fieldNeighbors.set(nmsChunk, 0);
            return new Object[] {nmsChunk, value};
        } catch (Throwable ignore) {
        }
        return null;
    }

    protected void disableLighting(Object[] disableResult) {
        if (disableResult != null) {
            try {
                fieldNeighbors.set(disableResult[0], 0);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    protected void resetLighting(Object[] disableResult) {
        if (disableResult != null) {
            try {
                fieldNeighbors.set(disableResult[0], disableResult[1]);
            } catch (Throwable ignore) {
                ignore.printStackTrace();
            }
        }
    }

    protected void enableLighting(Object[] disableResult) {
        if (disableResult != null) {
            try {
                fieldNeighbors.set(disableResult[0], 0x739C0);
            } catch (Throwable ignore) {
            }
        }
    }
}