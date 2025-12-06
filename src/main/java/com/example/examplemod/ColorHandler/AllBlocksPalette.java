package com.example.examplemod.ColorHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AllBlocksPalette {

    public static final Map<Block, Entry> PALETTE = new HashMap<>();
    private static final BlockGetter DUMMY_LEVEL = EmptyBlockGetter.INSTANCE;
    private static final BlockPos DUMMY_POS = BlockPos.ZERO;

    public static void buildPalette() {
        Minecraft minecraft = Minecraft.getInstance();

        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null) continue;

            BlockState state = block.defaultBlockState();
            if (!isFullCube(state)) {
                continue;
            }
            if (state.isAir()) {
                continue;
            }

            String path = "textures/block/" + id.getPath() + ".png";
            ResourceLocation textureLoc = new ResourceLocation(id.getNamespace(), path);

            try {
                Resource resource = minecraft.getResourceManager().getResource(textureLoc);

                try (InputStream inputStream = resource.getInputStream()) {
                    BufferedImage bufferedImage = ImageIO.read(inputStream);
                    double[] averageLab = computeAverageLab(bufferedImage);
                    PALETTE.put(block, new Entry(block, averageLab));
                }
            } catch (Exception e) {
            }
        }

        System.out.println("Palette size = " + PALETTE.size());
    }

    private static boolean isFullCube(BlockState state) {
        VoxelShape shape = state.getCollisionShape(DUMMY_LEVEL, DUMMY_POS);
        if (shape.isEmpty()) {
            return false;
        }
        AABB box = shape.bounds();
        final double eps = 1e-6;

        return Math.abs(box.minX) < eps &&
                Math.abs(box.minY) < eps &&
                Math.abs(box.minZ) < eps &&
                Math.abs(box.maxX - 1.0) < eps &&
                Math.abs(box.maxY - 1.0) < eps &&
                Math.abs(box.maxZ - 1.0) < eps;
    }


    private static double[] computeAverageLab(BufferedImage img) {
        long sr = 0, sg = 0, sb = 0, count = 0;

        int w = img.getWidth();
        int h = img.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 128) continue;

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                sr += r;
                sg += g;
                sb += b;
                count++;
            }
        }

        if (count == 0) {
            return ColorUtils.rgbToLab(255, 0, 255);
        }

        double r = sr / (double) count;
        double g = sg / (double) count;
        double b = sb / (double) count;

        return ColorUtils.rgbToLab((int) r, (int) g, (int) b);
    }

    public static class Entry {
        public final Block block;
        public final double[] lab;

        public Entry(Block b, double[] lab) {
            this.block = b;
            this.lab = lab;
        }
    }
}