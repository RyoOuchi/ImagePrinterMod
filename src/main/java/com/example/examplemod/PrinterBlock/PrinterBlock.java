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

/**
 * 画像をブロックとして「印刷」するプリンタブロック。
 *
 * 役割：
 * - 右クリックされたときに、クライアント側で画像→ブロックID配列(JSON) に変換
 * - その JSON を PrintImagePacket に乗せてサーバーへ送信
 * - サーバー側では PrintImagePacket.handle() → placeImage() が呼ばれ、
 *   実際にブロックが設置される
 */
public class PrinterBlock extends Block {
    public PrinterBlock() {
        super(Properties.of(Material.STONE));
    }

    /**
     * プレイヤーがこのブロックを右クリックしたときに呼ばれる。
     *
     * 処理の流れ：
     * 1. サーバー側なら即 SUCCESS を返して終了（処理はクライアントのみで行う）
     * 2. クライアント側では ImageLoader から画像ハンドラ一覧を取得
     * 3. 画像ハンドラが無ければエラーメッセージを出して FAIL
     * 4. とりあえず handlers.get(1)（2枚目の画像）を使って JSON 化
     * 5. 画像をブロックID配列に変換（convertToJson）
     * 6. PrintImagePacket を生成してサーバーへ送信
     *
     * @return 処理結果（SUCCESS / FAIL）
     */
    @Override
    public InteractionResult use(BlockState pState,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hit) {

        // サーバー側ではここで終了。
        // 実際のパケット送信はクライアントで行うため、
        // サーバーでは何もせず SUCCESS を返す。
        if (!level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // シングルトン ImageLoader から画像一覧を取得
        ImageLoader loader = ImageLoader.getInstance();
        List<ImageHandler> handlers = loader.getImageHandlers();

        // 画像が1枚もロードされていない場合
        if (handlers == null || handlers.isEmpty()) {
            System.out.println("[PrinterBlock] No images");
            return InteractionResult.FAIL;
        }

        // とりあえず 2 枚目（index 1）の画像を使用して印刷する。
        // 実際には index を切り替えたり、GUI で選択させたりしてもよい。
        ImageHandler handler = handlers.get(1);

        // 画像を「ブロックIDの2次元配列 JSON」に変換
        JsonArray json = handler.convertToJson();

        // クライアント → サーバーへパケット送信
        // サーバー側では PrintImagePacket.handle() で受け取り、
        // pos を基準としてブロックを設置する。
        ExampleMod.CHANNEL.sendToServer(new PrintImagePacket(pos, json));

        return InteractionResult.SUCCESS;
    }

}