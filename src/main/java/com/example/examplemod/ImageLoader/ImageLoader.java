package com.example.examplemod.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class ImageLoader {

    private static ImageLoader INSTANCE;

    private final List<ImageHandler> imageHandlers = new ArrayList<>();

    private ImageLoader() {}

    public static void initClient() {
        if (INSTANCE == null) {
            INSTANCE = new ImageLoader();
            INSTANCE.loadImages();
        }
    }

    private void loadImages() {
        imageHandlers.clear();
        imageHandlers.add(new ImageHandler("minecraft-logo.png"));
        imageHandlers.add(new ImageHandler("aporo.png"));
        imageHandlers.add(new ImageHandler("cobble.png"));
    }

    public List<ImageHandler> getImageHandlers() {
        return imageHandlers;
    }

    public static ImageLoader getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ImageLoader has not been init. Call initClient() in client setup.");
        }
        return INSTANCE;
    }
}
