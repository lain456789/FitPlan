package com.example.fitplan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.net.Uri;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.FutureTarget;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

final class CustomMedia {
    final File photo, gif, thumb;

    CustomMedia(Context context, String id) {
        File dir = new File(context.getFilesDir(), "custom_exercises");
        String stem = UUID.fromString(id).toString();
        photo = new File(dir, stem + ".webp");
        gif = new File(dir, stem + ".gif");
        thumb = new File(dir, stem + "_thumb.webp");
    }

    static CustomMedia existing(Context context, String id) {
        CustomMedia files = savedFiles(context, id);
        return files != null && files.thumb.isFile() && (files.photo.isFile() || files.gif.isFile())
                ? files
                : null;
    }

    static CustomMedia savedFiles(Context context, String id) {
        try {
            CustomMedia files = new CustomMedia(context, id);
            return files.photo.exists() || files.gif.exists() || files.thumb.exists()
                    ? files
                    : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Called off the UI thread. The database row is published only after both files exist.
    void importFrom(Context context, Uri uri) throws Exception {
        File dir = thumb.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException();
        File input = File.createTempFile("import-", ".tmp", context.getCacheDir());
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                    FileOutputStream out = new FileOutputStream(input)) {
                if (in == null) throw new IOException();
                byte[] block = new byte[32768];
                int count;
                long total = 0;
                while ((count = in.read(block)) != -1) {
                    total += count;
                    if (total > 64L * 1024 * 1024) throw new IOException();
                    out.write(block, 0, count);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(input.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException();
            if ("image/gif".equals(bounds.outMimeType)) {
                if ((long) bounds.outWidth * bounds.outHeight > 8000000) throw new IOException();
                FutureTarget<GifDrawable> animation =
                        Glide.with(context)
                                .asGif()
                                .load(input)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .submit(320, 320);
                try {
                    Bitmap first = animation.get().getFirstFrame();
                    Bitmap poster = representativeFrame(input, first);
                    writeThumbnail(poster);
                    if (!input.renameTo(gif)) throw new IOException();
                } finally {
                    Glide.with(context).clear(animation);
                }
            } else {
                FutureTarget<Bitmap> request =
                        Glide.with(context)
                                .asBitmap()
                                .load(input)
                                .disallowHardwareConfig()
                                .fitCenter()
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .submit(1280, 1280);
                try {
                    Bitmap bitmap = request.get();
                    write(bitmap, photo);
                    writeThumbnail(bitmap);
                } finally {
                    Glide.with(context).clear(request);
                }
            }
        } finally {
            input.delete();
        }
    }

    private static Bitmap representativeFrame(File input, Bitmap first) {
        int base = first.getPixel(0, 0);
        for (int y = 0; y < first.getHeight(); y += Math.max(1, first.getHeight() / 32))
            for (int x = 0; x < first.getWidth(); x += Math.max(1, first.getWidth() / 32))
                if (first.getPixel(x, y) != base) return first;
        Movie movie = Movie.decodeFile(input.getPath());
        if (movie == null || movie.duration() <= 0) return first;
        Bitmap alternate =
                Bitmap.createBitmap(first.getWidth(), first.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(alternate);
        canvas.scale(
                (float) alternate.getWidth() / movie.width(),
                (float) alternate.getHeight() / movie.height());
        movie.setTime(movie.duration() / 2);
        movie.draw(canvas, 0, 0);
        return alternate;
    }

    private void writeThumbnail(Bitmap image) throws IOException {
        float ratio = Math.min(1f, 320f / Math.max(image.getWidth(), image.getHeight()));
        write(
                Bitmap.createScaledBitmap(
                        image,
                        Math.max(1, Math.round(image.getWidth() * ratio)),
                        Math.max(1, Math.round(image.getHeight() * ratio)),
                        true),
                thumb);
    }

    private static void write(Bitmap image, File path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(path)) {
            if (!image.compress(Bitmap.CompressFormat.WEBP, 88, out)) throw new IOException();
        }
    }

    boolean discard() {
        boolean photoRemoved = deleteIfPresent(photo);
        boolean gifRemoved = deleteIfPresent(gif);
        boolean thumbnailRemoved = deleteIfPresent(thumb);
        return photoRemoved && gifRemoved && thumbnailRemoved;
    }

    private static boolean deleteIfPresent(File file) {
        return !file.exists() || file.delete();
    }
}
