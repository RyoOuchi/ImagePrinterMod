package com.example.examplemod.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 画像をまとめて管理するシングルトンクラス。
 *
 * 役割：
 * - MOD クライアント側の初期化時に一度だけインスタンス化される
 * - 使用したい画像を ImageHandler として読み込んで保持する
 * - 他のクラスからは getInstance() 経由でアクセスし、
 *   getImageHandlers() で読み込まれた画像群を取得できる
 *
 * 想定タイミング：
 * - FMLClientSetupEvent などの「クライアント初期化処理」の中で
 *   ImageLoader.initClient() を一度だけ呼び出す
 */
public class ImageLoader {

    /** シングルトンインスタンスを保持する静的フィールド */
    private static ImageLoader INSTANCE;

    /** 読み込んだ画像ごとのハンドラーを格納するリスト */
    private final List<ImageHandler> imageHandlers = new ArrayList<>();

    /**
     * コンストラクタは外部から呼べないように private。
     * シングルトンパターンを実現するため、
     * インスタンス生成は initClient() の内部だけに限定する。
     */
    private ImageLoader() {}

    /**
     * クライアント側の初期化用メソッド。
     *
     * 処理内容：
     * - INSTANCE が null の場合のみ新しく ImageLoader を生成
     * - 生成直後に loadImages() を呼び出して画像を読み込む
     *
     * 注意：
     * - クライアントのセットアップ（例：FMLClientSetupEvent）で
     *   1回だけ呼ぶ想定
     * - 複数回呼ばれても、2回目以降は何もしない（INSTANCE == null チェック）
     */
    public static void initClient() {
        if (INSTANCE == null) {
            INSTANCE = new ImageLoader();
            INSTANCE.loadImages();
        }
    }

    /**
     * 実際に画像を読み込んで imageHandlers に登録する内部メソッド。
     *
     * 処理内容：
     * - いったんリストを clear() してから
     * - 使用したい画像ファイルごとに new ImageHandler(...) して追加
     *
     * 画像のパスについて：
     * - ImageHandler 側で "images/<ファイル名>" として
     *   MOD リソースから読み込むようにしているため、
     *   ここでは純粋にファイル名だけ渡せばよい。
     *
     * 例：
     *   assets/<modid>/images/minecraft-logo.png
     *   assets/<modid>/images/aporo.png
     *   assets/<modid>/images/cobble.png
     */
    private void loadImages() {
        imageHandlers.clear();

        // 必要な画像をここに列挙して登録
        imageHandlers.add(new ImageHandler("minecraft-logo.png"));
        imageHandlers.add(new ImageHandler("aporo.png"));
        imageHandlers.add(new ImageHandler("cobble.png"));
    }

    /**
     * 読み込まれた ImageHandler の一覧を取得する。
     *
     * @return 画像ごとの ImageHandler のリスト
     *
     * 注意：
     * - 返すのは内部リストの参照なので、
     *   外部で add/remove するとここにも影響が出る。
     *   読み取り専用で使うことを前提にしている。
     */
    public List<ImageHandler> getImageHandlers() {
        return imageHandlers;
    }

    /**
     * シングルトンインスタンスを取得する。
     *
     * @return ImageLoader の唯一のインスタンス
     * @throws IllegalStateException initClient() がまだ呼ばれていない場合
     *
     * 使い方：
     * - 画像を使いたい側のクラスで
     *   ImageLoader loader = ImageLoader.getInstance();
     *   List<ImageHandler> handlers = loader.getImageHandlers();
     *   のようにしてアクセスする。
     */
    public static ImageLoader getInstance() {
        if (INSTANCE == null) {
            // initClient() が呼ばれていない状態でアクセスするとここで落ちる
            throw new IllegalStateException("ImageLoader has not been init. Call initClient() in client setup.");
        }
        return INSTANCE;
    }
}