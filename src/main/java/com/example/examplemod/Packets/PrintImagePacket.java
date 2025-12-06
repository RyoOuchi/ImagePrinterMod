package com.example.examplemod.Packets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.json.Json;
import javax.json.JsonArray;
import java.util.function.Supplier;

public class PrintImagePacket {

    private final BlockPos pos;
    private final JsonArray jsonArray;

    public PrintImagePacket(BlockPos pos, JsonArray json) {
        this.pos = pos;
        this.jsonArray = json;
    }


    public BlockPos getPos() {
        return pos;
    }

    public JsonArray getJson() {
        return jsonArray;
    }

    public static void encode(PrintImagePacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);

        JsonArray json = pkt.jsonArray;
        int height = json.size();
        buf.writeVarInt(height);

        if (height == 0) {
            return;
        }

        int width = json.getJsonArray(0).size();
        buf.writeVarInt(width);

        for (int y = 0; y < height; y++) {
            JsonArray row = json.getJsonArray(y);
            for (int x = 0; x < width; x++) {
                String blockId = row.getString(x);
                buf.writeResourceLocation(new ResourceLocation(blockId));
            }
        }
    }

    public static PrintImagePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();

        int height = buf.readVarInt();
        int width = buf.readVarInt();

        javax.json.JsonArrayBuilder imageBuilder = Json.createArrayBuilder();

        for (int y = 0; y < height; y++) {
            javax.json.JsonArrayBuilder rowBuilder = Json.createArrayBuilder();
            for (int x = 0; x < width; x++) {
                ResourceLocation rl = buf.readResourceLocation();
                rowBuilder.add(rl.toString());
            }
            imageBuilder.add(rowBuilder);
        }

        JsonArray json = imageBuilder.build();
        return new PrintImagePacket(pos, json);
    }

    public static void handle(PrintImagePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Level level = player.getLevel();
            JsonArray json = pkt.getJson();

            placeImage(level, pkt.getPos().above(), json);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void placeImage(Level level, BlockPos origin, JsonArray json) {
        int height = json.size();
        int width = json.getJsonArray(0).size();

        for (int y = 0; y < height; y++) {
            JsonArray row = json.getJsonArray(y);

            for (int x = 0; x < width; x++) {
                String blockId = row.getString(x);

                ResourceLocation rl = new ResourceLocation(blockId);
                Block block = ForgeRegistries.BLOCKS.getValue(rl);

                if (block == null) continue;

                BlockPos placePos = origin.offset(x, 0, y);

                level.setBlock(placePos, block.defaultBlockState(), 3);
            }
        }
    }
}