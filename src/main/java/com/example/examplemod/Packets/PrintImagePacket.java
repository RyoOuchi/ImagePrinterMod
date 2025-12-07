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

/**
 * クライアント側で生成した「画像データ（ブロックIDの2次元配列）」を
 * サーバー側に送信し、指定位置にブロックとして配置させるためのパケット。
 *
 * 役割：
 * - pos：画像（ブロック配置）を開始する基準座標
 * - jsonArray：画像をブロックIDに変換した JsonArray（2次元配列）
 *
 * パケットの流れ：
 * - クライアント：PrintImagePacket を生成して送信
 * - サーバー：decode() で復元 → handle() で placeImage() を呼び出し、実際にブロックを設置
 */
public class PrintImagePacket {

    /** 画像を配置する基準となるブロック座標（サーバー側では origin の一つ下が基準） */
    private final BlockPos pos;
    /** 画像をブロックIDに変換した 2 次元配列の JSON データ */
    private final JsonArray jsonArray;

    /**
     * パケットのデータコンストラクタ。
     *
     * @param pos  サーバー側での配置基準座標
     * @param json ブロックIDの2次元配列を表す JsonArray
     */
    public PrintImagePacket(BlockPos pos, JsonArray json) {
        this.pos = pos;
        this.jsonArray = json;
    }

    /** 画像配置の基準座標を返す。 */
    public BlockPos getPos() {
        return pos;
    }

    /** 画像のブロックID 2 次元配列を返す。 */
    public JsonArray getJson() {
        return jsonArray;
    }

    /**
     * パケットをネットワークバッファ（FriendlyByteBuf）に書き込む処理。
     *
     * データ形式：
     * - BlockPos（基準座標）
     * - int: 画像の高さ (height)
     * - int: 画像の幅 (width)
     * - その後、行ごとに (y)
     *   - 列ごとに (x) ResourceLocation（ブロックID）の列を順番に書き込む
     *
     * @param pkt  書き込むパケットインスタンス
     * @param buf  Forge のネットワークバッファ
     */
    public static void encode(PrintImagePacket pkt, FriendlyByteBuf buf) {
        // まず配置座標を書き込み
        buf.writeBlockPos(pkt.pos);

        // 画像データを取得
        JsonArray json = pkt.jsonArray;
        int height = json.size();
        // 高さを書き込み
        buf.writeVarInt(height);

        // 高さ0（空画像）の場合はここで終了
        if (height == 0) {
            return;
        }

        // 幅は 0 行目の配列サイズから取得
        int width = json.getJsonArray(0).size();
        buf.writeVarInt(width);

        // 行ごと・列ごとにブロックIDを ResourceLocation として書き込む
        // JsonArrayをStringに変換してencodeすると大きいイメージだとエラー出る。
        // EncodeできるStringの最大データ量は32767バイトまでらしい。
        for (int y = 0; y < height; y++) {
            JsonArray row = json.getJsonArray(y);
            for (int x = 0; x < width; x++) {
                String blockId = row.getString(x);
                buf.writeResourceLocation(new ResourceLocation(blockId));
            }
        }
    }

    /**
     * ネットワークバッファからパケットデータを読み取り、
     * PrintImagePacket インスタンスを復元する処理。
     *
     * encode() で書き込んだ順番と同じ順で読み取る必要がある。
     *
     * @param buf 受信バッファ
     * @return 復元した PrintImagePacket
     */
    public static PrintImagePacket decode(FriendlyByteBuf buf) {
        // 配置座標を読み取り
        BlockPos pos = buf.readBlockPos();

        // 画像の高さ・幅を読み取り
        int height = buf.readVarInt();
        int width = buf.readVarInt();

        // JSON の 2 次元配列を組み立てるビルダー
        javax.json.JsonArrayBuilder imageBuilder = Json.createArrayBuilder();

        // 行ごとに JsonArray を組み立てる
        for (int y = 0; y < height; y++) {
            javax.json.JsonArrayBuilder rowBuilder = Json.createArrayBuilder();
            for (int x = 0; x < width; x++) {
                // ブロックID(ResourceLocation) を読み取り
                ResourceLocation rl = buf.readResourceLocation();
                // 文字列形式 "namespace:path" として行に追加
                rowBuilder.add(rl.toString());
            }
            // 行を外側の配列に追加
            imageBuilder.add(rowBuilder);
        }

        // 完成した 2 次元 JSON 配列
        JsonArray json = imageBuilder.build();
        return new PrintImagePacket(pos, json);
    }

    /**
     * 受信したパケットを処理するメソッド。
     *
     * Forge のネットワークでは、handle() はネットワークスレッド上で呼ばれるため、
     * 実際のワールド操作は ctx.get().enqueueWork(...) 内で
     * メインスレッドに投げて実行する必要がある。
     *
     * 処理の流れ：
     * 1. 送信者の ServerPlayer を取得（null チェック）
     * 2. そのプレイヤーが属する Level を取得
     * 3. パケット内の json データと pos を元に placeImage() を呼び出し
     * 4. 最後に setPacketHandled(true) を呼んで処理完了を通知
     *
     * @param pkt パケット
     * @param ctx ネットワークコンテキスト供給元
     */
    public static void handle(PrintImagePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 送信してきたプレイヤー（サーバー側）
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Level level = player.getLevel();
            JsonArray json = pkt.getJson();

            // 実際にブロックを配置する。
            // pos の 1 ブロック上 (above) を画像の左上起点とする。
            placeImage(level, pkt.getPos().above(), json);
        });
        // パケットが正常に処理されたことを通知
        ctx.get().setPacketHandled(true);
    }

    /**
     * 画像(JsonArray) の内容に従って、ワールドにブロックを配置する。
     *
     * JSON 構造（例）:
     * [
     *   ["minecraft:stone", "minecraft:dirt"],
     *   ["minecraft:oak_planks", "minecraft:air"]
     * ]
     *
     * 配置イメージ：
     * - origin を左手前(0,0) として、x を東西、y(ループのy)を南北方向にオフセットして設置
     * - 高さ方向は固定（origin.y の高さに置く）
     *
     * @param level  ブロックを設置するワールド
     * @param origin 画像配置の左上の基準座標
     * @param json   ブロックIDの2次元配列を表す JsonArray
     */
    private static void placeImage(Level level, BlockPos origin, JsonArray json) {
        int height = json.size();
        int width = json.getJsonArray(0).size();

        // 行方向（画像の y）
        for (int y = 0; y < height; y++) {
            JsonArray row = json.getJsonArray(y);

            // 列方向（画像の x）
            for (int x = 0; x < width; x++) {
                String blockId = row.getString(x);

                // 文字列から ResourceLocation を作成
                ResourceLocation rl = new ResourceLocation(blockId);
                // ブロックをレジストリから取得
                Block block = ForgeRegistries.BLOCKS.getValue(rl);

                // 不明なブロックIDの場合はスキップ
                if (block == null) continue;

                // origin から x,z をずらした位置にブロックを置く
                BlockPos placePos = origin.offset(x, 0, y);

                // 実際にブロックを設置 (flag = 3 は更新通知など付き)
                level.setBlock(placePos, block.defaultBlockState(), 3);
            }
        }
    }
}