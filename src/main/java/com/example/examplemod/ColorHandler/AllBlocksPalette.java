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

/**
 * 全てのブロックに対して
 * 「テクスチャ画像から平均色(Lab)を計算したパレット」を作るクラス。
 *
 * - ForgeRegistries.BLOCKS から全ブロックを列挙
 * - 「1x1x1を完全に占有するフルブロック」だけを対象にする（ドア・フェンス・ロッドなど除外）
 * - block のテクスチャ (textures/block/xxx.png) を読み込み
 * - 画像の平均色を RGB→Lab に変換して保存
 */
public class AllBlocksPalette {

    /** ブロック → 平均Lab色 の対応表（パレット） */
    public static final Map<Block, Entry> PALETTE = new HashMap<>();

    /**
     * BlockState から当たり判定（VoxelShape）を取るためのダミーのレベル。
     * 実際のワールドは不要なので EmptyBlockGetter を使っている。
     */
    private static final BlockGetter DUMMY_LEVEL = EmptyBlockGetter.INSTANCE;

    /**
     * VoxelShape を取得するときに使うダミー座標。
     * 位置は意味を持たないので BlockPos.ZERO でよい。
     */
    private static final BlockPos DUMMY_POS = BlockPos.ZERO;

    /**
     * 全ブロックを走査して、パレット(PALETTE)を構築する。
     *
     * 処理の流れ：
     * 1. ForgeRegistries.BLOCKS から全ブロックを取得
     * 2. defaultBlockState() を使ってフルキューブかどうか判定（isFullCube）
     * 3. フルキューブかつ空気ブロックでないものだけを対象にする
     * 4. textures/block/<block_path>.png を探して読み込み
     * 5. 画像の平均色を Lab に変換し、PALETTE に登録
     */
    public static void buildPalette() {
        // クライアント側の Minecraft インスタンス
        Minecraft minecraft = Minecraft.getInstance();

        // 全ブロックを列挙
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            // ブロックの ResourceLocation (namespace:path) を取得
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null) continue; // 念のため null チェック

            // ブロックのデフォルト状態
            BlockState state = block.defaultBlockState();

            // フルキューブでないブロック（ドア・フェンス・ロッドなど）はスキップ
            if (!isFullCube(state)) {
                continue;
            }

            // 空気ブロックも対象外
            if (state.isAir()) {
                continue;
            }

            // ブロックテクスチャの場所を決める
            // 例: minecraft:stone → textures/block/stone.png
            String path = "textures/block/" + id.getPath() + ".png";
            ResourceLocation textureLoc = new ResourceLocation(id.getNamespace(), path);

            try {
                // リソースマネージャからテクスチャを取得
                Resource resource = minecraft.getResourceManager().getResource(textureLoc);

                // try-with-resources で InputStream を安全に開く
                try (InputStream inputStream = resource.getInputStream()) {
                    // PNG 画像を読み込む
                    BufferedImage bufferedImage = ImageIO.read(inputStream);
                    // 平均Lab色を計算
                    double[] averageLab = computeAverageLab(bufferedImage);
                    // ブロックと平均色をパレットに登録
                    PALETTE.put(block, new Entry(block, averageLab));
                }
            } catch (Exception e) {
                // テクスチャが存在しない、壊れている等のケースは無視して次へ
                // （静かにスキップしたいのでログは出していない）
            }
        }

        // 構築されたパレットの要素数をログに出す
        System.out.println("Palette size = " + PALETTE.size());
    }

    /**
     * 与えられた BlockState が「1x1x1 を完全に占めるフルキューブかどうか」を判定する。
     *
     * 判定方法：
     * - state.getCollisionShape(DUMMY_LEVEL, DUMMY_POS) で当たり判定の VoxelShape を取得
     * - その境界ボックス(AABB)を取得し、
     *   [minX,minY,minZ] = [0,0,0], [maxX,maxY,maxZ] = [1,1,1] かどうかを誤差付きで判定
     *
     * これにより、ドア・フェンス・階段・スラブなどの「フルブロックではない形状」を除外できる。
     */
    private static boolean isFullCube(BlockState state) {
        // 当たり判定の形状を取得（ダミーレベル・ダミー座標でOK）
        VoxelShape shape = state.getCollisionShape(DUMMY_LEVEL, DUMMY_POS);

        // 当たり判定が空（形状なし）の場合はフルブロックではない
        if (shape.isEmpty()) {
            return false;
        }

        // VoxelShape の境界ボックスを取得
        AABB box = shape.bounds();
        final double eps = 1e-6; // 浮動小数の誤差を吸収するための許容値

        // 境界ボックスが [0,0,0] ～ [1,1,1] とほぼ一致しているかをチェック
        return Math.abs(box.minX) < eps &&
                Math.abs(box.minY) < eps &&
                Math.abs(box.minZ) < eps &&
                Math.abs(box.maxX - 1.0) < eps &&
                Math.abs(box.maxY - 1.0) < eps &&
                Math.abs(box.maxZ - 1.0) < eps;
    }

    /**
     * 与えられた BufferedImage から「透過していないピクセル」の平均色を計算し、
     * その平均RGBを CIE Lab 色空間に変換して返す。
     *
     * 手順：
     * 1. 全ピクセルを走査
     * 2. アルファ値 a < 128 のピクセルは「ほぼ透明」とみなして無視
     * 3. 残りのピクセルの r,g,b の合計を取り、最後に平均を出す
     * 4. 平均RGB を ColorUtils.rgbToLab で Lab に変換
     * 5. 完全に透明（カウント0）の場合は、例外的にマゼンタ (255,0,255) を返す
     */
    private static double[] computeAverageLab(BufferedImage img) {
        long sr = 0, sg = 0, sb = 0; // 各色の合計
        long count = 0;              // 有効ピクセル数

        int w = img.getWidth();
        int h = img.getHeight();

        // 全ピクセルを走査
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF; // アルファ値

                // 半透明（a < 128）は背景とみなして無視
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

        // 有効なピクセルが1つもない場合のフォールバック
        // （完全透過テクスチャなど）。ここではマゼンタを返す。
        if (count == 0) {
            return ColorUtils.rgbToLab(255, 0, 255);
        }

        // 平均RGBを計算
        double r = sr / (double) count;
        double g = sg / (double) count;
        double b = sb / (double) count;

        // 平均RGBを Lab に変換
        return ColorUtils.rgbToLab((int) r, (int) g, (int) b);
    }

    /**
     * パレットの 1 エントリを表すクラス。
     * 「どのブロック」かと「そのブロックの平均Lab色」を保持する。
     */
    public static class Entry {
        /** 対象のブロック */
        public final Block block;

        /** ブロックテクスチャの平均色（CIE Lab, 要素数3の配列: [L, a, b]） */
        public final double[] lab;

        public Entry(Block b, double[] lab) {
            this.block = b;
            this.lab = lab;
        }
    }
}