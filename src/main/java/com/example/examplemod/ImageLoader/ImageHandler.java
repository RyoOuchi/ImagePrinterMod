package com.example.examplemod.ImageLoader;

import com.example.examplemod.ColorHandler.AllBlocksPalette;
import com.example.examplemod.ColorHandler.ColorUtils;
import com.example.examplemod.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import javax.imageio.ImageIO;
import javax.json.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class ImageHandler {

    private final BufferedImage image;

    public ImageHandler(String filePath) {
        this.image = loadImageFromResource(filePath);
        if (this.image == null) {
            throw new RuntimeException("Failed to load image: " + filePath);
        }
    }

    private BufferedImage loadImageFromResource(String filePath) {
        try {
            ResourceLocation rl =
                    new ResourceLocation(ExampleMod.MODID, "images/" + filePath);
            Resource res =
                    Minecraft.getInstance().getResourceManager().getResource(rl);

            try (InputStream stream = res.getInputStream()) {
                return ImageIO.read(stream);
            }
        } catch (IOException e) {
            throw new RuntimeException("Image load failure: " + filePath, e);
        }
    }

    private Block matchColorToBlock(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        double[] pixelLab = ColorUtils.rgbToLab(r, g, b);

        Block bestBlock = null;
        double bestDistance = Double.MAX_VALUE;

        for (AllBlocksPalette.Entry entry : AllBlocksPalette.PALETTE.values()) {

            double dist = ColorUtils.deltaE2000(pixelLab, entry.lab);

            if (dist < bestDistance) {
                bestDistance = dist;
                bestBlock = entry.block;
            }
        }

        return bestBlock != null ? bestBlock : net.minecraft.world.level.block.Blocks.AIR;
    }

    public JsonArray convertToJson() {
        JsonArrayBuilder outer = Json.createArrayBuilder();

        for (int y = 0; y < image.getHeight(); y++) {
            JsonArrayBuilder row = Json.createArrayBuilder();

            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);

                Block block = matchColorToBlock(rgb);

                ResourceLocation key =
                        ForgeRegistries.BLOCKS.getKey(block);

                row.add(key != null ? key.toString() : "minecraft:air");
            }
            outer.add(row);
        }

        return outer.build();
    }
}