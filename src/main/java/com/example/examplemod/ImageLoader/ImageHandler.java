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

/**
 * 画像ファイルを読み込み、
 * 各ピクセルの色に最も近いブロックをマッチングして
 * 「ブロックIDの2次元配列(JSON)」に変換するクラス。
 *
 * 全体の流れ：
 * - コンストラクタで MOD 内の画像リソースを読み込み BufferedImage として保持
 * - matchColorToBlock() で 1 ピクセルの RGB を最も近いブロックに変換
 * - convertToJson() で画像全体を走査し、ブロックIDの 2 次元 JSON 配列に変換
 *
 * 想定用途：
 * - 画像をブロックのモザイクアートとしてワールドに配置するためのデータ生成など
 */
public class ImageHandler {

    /** 読み込んだ画像データ */
    private final BufferedImage image;

    /**
     * コンストラクタ。
     * @param filePath MOD のリソースフォルダ内 "images/" 以下にある画像ファイル名
     *                 例: "minecraft_logo.png" → assets/<modid>/images/minecraft_logo.png
     * 処理：
     * - loadImageFromResource() を呼び出して BufferedImage を取得
     * - 読み込みに失敗した場合は RuntimeException を投げる
     */
    public ImageHandler(String filePath) {
        this.image = loadImageFromResource(filePath);
        if (this.image == null) {
            // null のまま進めると NPE になるため、コンストラクタで即座に落とす
            throw new RuntimeException("Failed to load image: " + filePath);
        }
    }

    /**
     * MOD のリソースから画像を読み込む。
     *
     * リソースの場所：
     * - ResourceLocation(ExampleMod.MODID, "images/" + filePath)
     *   → assets/<modid>/images/<filePath> に対応する
     *
     * 手順：
     * - Minecraft の ResourceManager から Resource を取得
     * - InputStream を開いて ImageIO.read() で BufferedImage に変換
     *
     * @param filePath "minecraft_logo.png" のようなファイル名
     * @return 読み込んだ BufferedImage
     * @throws RuntimeException 読み込みに失敗した場合（IOException をラップ）
     */
    private BufferedImage loadImageFromResource(String filePath) {
        try {
            // MODID: ExampleMod.MODID, パス: images/<filePath>
            ResourceLocation rl =
                    new ResourceLocation(ExampleMod.MODID, "images/" + filePath);
            // リソースを取得
            Resource res =
                    Minecraft.getInstance().getResourceManager().getResource(rl);

            // try-with-resources で InputStream を安全にクローズ
            try (InputStream stream = res.getInputStream()) {
                // 画像として読み込む
                return ImageIO.read(stream);
            }
        } catch (IOException e) {
            // IOException が起きたら RuntimeException として投げ直す
            throw new RuntimeException("Image load failure: " + filePath, e);
        }
    }

    /**
     * 1ピクセルの RGB 値に最も近いブロックをパレットから探す。
     *
     * 処理の流れ：
     * 1. 引数 rgb から R,G,B 成分を取り出す
     * 2. ColorUtils.rgbToLab() でピクセルの色を Lab に変換
     * 3. AllBlocksPalette.PALETTE に登録されている全ブロックについて、
     *    そのブロックの平均Lab色との色差 ΔE2000 を計算
     * 4. ΔE2000 が最小のブロックを「最も近い色のブロック」として返す
     * 5. 一致するものが見つからなかった場合は minecraft:air を返す（保険）
     *
     * @param rgb 32bit ARGB または RGB 値 (image.getRGB で取得したもの)
     * @return 最も近い色を持つブロック
     */
    private Block matchColorToBlock(int rgb) {
        // rgb から R,G,B 各成分を抽出（ここではアルファは無視）
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // ピクセル色を Lab に変換
        double[] pixelLab = ColorUtils.rgbToLab(r, g, b);

        Block bestBlock = null;                 // 一番近いブロック
        double bestDistance = Double.MAX_VALUE; // 最小の色差（初期値は非常に大きく）

        // 事前に構築されている全ブロックのパレットを走査
        for (AllBlocksPalette.Entry entry : AllBlocksPalette.PALETTE.values()) {

            // ピクセル色とブロック平均色との色差 ΔE2000 を計算
            double dist = ColorUtils.deltaE2000(pixelLab, entry.lab);

            // より小さい色差のブロックが見つかったら更新
            if (dist < bestDistance) {
                bestDistance = dist;
                bestBlock = entry.block;
            }
        }

        // 何らかの理由で見つからない場合は AIR を返す（フォールバック）
        return bestBlock != null ? bestBlock : net.minecraft.world.level.block.Blocks.AIR;
    }

    /**
     * 読み込んだ画像全体を「ブロックIDの2次元配列(JSON)」に変換する。
     *
     * JSON の構造：
     * [
     *   [ "minecraft:stone", "minecraft:dirt", ... ], // y=0行目
     *   [ "minecraft:oak_planks", "minecraft:air", ... ], // y=1行目
     *   ...
     * ]
     *
     * 処理の流れ：
     * 1. 画像の高さ(height)ぶん、行ループ (y)
     * 2. 行ごとに JsonArrayBuilder row を作る
     * 3. 横方向(width)ぶん、列ループ (x)
     * 4. image.getRGB(x, y) でピクセルの色を取得
     * 5. matchColorToBlock() を使って最も近いブロックを求める
     * 6. ForgeRegistries.BLOCKS.getKey(block) から "namespace:path" 文字列を取得
     * 7. その文字列を row に add していく
     * 8. 行が完成したら outer に add する
     * 9. 最終的に outer.build() で JsonArray を返す
     *
     * @return 画像をブロックIDに変換した 2 次元配列の JsonArray
     */
    public JsonArray convertToJson() {
        // 外側の配列（= 行のリスト）を作るビルダー
        JsonArrayBuilder outer = Json.createArrayBuilder();

        // y方向に画像を走査（行ごと）
        for (int y = 0; y < image.getHeight(); y++) {
            // 1行分のブロックIDを詰める配列
            JsonArrayBuilder row = Json.createArrayBuilder();

            // x方向に走査（列ごと）
            for (int x = 0; x < image.getWidth(); x++) {
                // ピクセルの色を取得
                int rgb = image.getRGB(x, y);

                // ピクセル色に最も近いブロックを取得
                Block block = matchColorToBlock(rgb);

                // ブロックの ResourceLocation (namespace:path) を取得
                ResourceLocation key =
                        ForgeRegistries.BLOCKS.getKey(block);

                // 文字列として配列に追加
                // key が null の場合は "minecraft:air" にフォールバック
                row.add(key != null ? key.toString() : "minecraft:air");
            }

            // 1行分を outer（全体の配列）に追加
            outer.add(row);
        }

        // JsonArray として完成させて返す
        return outer.build();
    }
}