package com.example.examplemod.PrinterBlock;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.ImageLoader.ImageHandler;
import com.example.examplemod.ImageLoader.ImageLoader;
import com.example.examplemod.Packets.PrintImagePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

import javax.json.JsonArray;
import java.util.List;

public class PrinterBlock extends Block {
    public PrinterBlock() {
        super(Properties.of(Material.STONE));
    }

    @Override
    public InteractionResult use(BlockState pState, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {

        if (!level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ImageLoader loader = ImageLoader.getInstance();
        List<ImageHandler> handlers = loader.getImageHandlers();

        if (handlers == null || handlers.isEmpty()) {
            System.out.println("[PrinterBlock] No images");
            return InteractionResult.FAIL;
        }

        ImageHandler handler = handlers.get(1);
        JsonArray json = handler.convertToJson();
        ExampleMod.CHANNEL.sendToServer(new PrintImagePacket(pos, json));
        return InteractionResult.SUCCESS;
    }

}
