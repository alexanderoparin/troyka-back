package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Сервис для сжатия изображений перед отправкой в провайдеры генерации.
 * Используется для соблюдения лимитов размера запросов (например, Gemini API: 7MB на файл, 20MB на запрос).
 */
@Slf4j
@Service
public class ImageCompressionService {

    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    /** Максимальный размер одного изображения (7MB для Gemini API). */
    private static final long MAX_SINGLE_IMAGE_SIZE = 7 * 1024 * 1024;

    /** Целевой размер после сжатия (6.5MB с запасом). */
    private static final long TARGET_IMAGE_SIZE = (long) (6.5 * 1024 * 1024);

    /** Максимальное разрешение по длинной стороне (px). */
    private static final int MAX_DIMENSION = 2048;

    /** Минимальное качество JPEG (0.0–1.0). */
    private static final float MIN_JPEG_QUALITY = 0.6f;

    /** Начальное качество JPEG; при переполнении лимита понижаем шагом {@link #QUALITY_STEP}. */
    private static final float INITIAL_JPEG_QUALITY = 0.95f;

    /** Шаг понижения/повышения качества при подборе. */
    private static final float QUALITY_STEP = 0.05f;

    private static String formatMb(long bytes) {
        return String.format("%.2f", bytes / BYTES_PER_MB);
    }

    /**
     * Сжать изображение до целевого размера.
     * Если изображение уже меньше лимита, возвращается без изменений.
     *
     * @param imageBytes исходные байты изображения
     * @param mimeType   MIME тип изображения (image/jpeg, image/png)
     * @return сжатые байты изображения
     * @throws IOException если произошла ошибка при обработке изображения
     */
    public byte[] compressImage(byte[] imageBytes, String mimeType) throws IOException {
        if (imageBytes.length <= MAX_SINGLE_IMAGE_SIZE) {
            log.debug("Изображение уже соответствует лимиту ({} MB), сжатие не требуется", formatMb(imageBytes.length));
            return imageBytes;
        }

        log.info("Начало сжатия изображения: размер={} MB, mimeType={}", formatMb(imageBytes.length), mimeType);

        BufferedImage image = readImage(imageBytes);
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        log.debug("Исходные размеры изображения: {}x{}", originalWidth, originalHeight);

        image = ensureRgb(image);

        // Шаг 1: Пробуем сжать в полном разрешении (PNG→JPEG уже сильно уменьшает размер)
        byte[] compressed = compressWithQuality(image, INITIAL_JPEG_QUALITY);
        if (compressed.length <= TARGET_IMAGE_SIZE) {
            log.debug("Уложились в лимит в полном разрешении {}x{}, сжатие без ресайза", image.getWidth(), image.getHeight());
        } else {
            // Шаг 2: Не влезло — уменьшаем разрешение до MAX_DIMENSION
            if (originalWidth > MAX_DIMENSION || originalHeight > MAX_DIMENSION) {
                image = resizeImage(image, MAX_DIMENSION);
                log.debug("Изображение уменьшено до: {}x{}", image.getWidth(), image.getHeight());
            }
            compressed = compressWithQuality(image, INITIAL_JPEG_QUALITY);

            // Шаг 3: Подбираем качество — либо понижаем, пока влезет, либо поднимаем до максимума в лимите
            if (compressed.length > TARGET_IMAGE_SIZE) {
                compressed = reduceQualityUntilFits(image, compressed);
            } else {
                compressed = increaseQualityUpToLimit(image, compressed);
            }

            if (compressed.length > TARGET_IMAGE_SIZE) {
                log.warn("Изображение все еще слишком большое после сжатия quality, уменьшаем разрешение");
                image = resizeImage(image, (int) (MAX_DIMENSION * 0.75));
                compressed = compressWithQuality(image, MIN_JPEG_QUALITY);
            }
        }

        logCompressionResult(imageBytes.length, compressed.length);

        return compressed;
    }

    private BufferedImage readImage(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Не удалось прочитать изображение");
        }
        return image;
    }

    private BufferedImage ensureRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private byte[] reduceQualityUntilFits(BufferedImage image, byte[] compressed) throws IOException {
        float quality = INITIAL_JPEG_QUALITY;
        while (compressed.length > TARGET_IMAGE_SIZE && quality >= MIN_JPEG_QUALITY) {
            quality -= QUALITY_STEP;
            compressed = compressWithQuality(image, quality);
            log.debug("Попытка сжатия с quality={}, размер={} MB", quality, formatMb(compressed.length));
        }
        return compressed;
    }

    private byte[] increaseQualityUpToLimit(BufferedImage image, byte[] compressed) throws IOException {
        for (float q = 1.0f; q >= MIN_JPEG_QUALITY; q -= QUALITY_STEP) {
            byte[] candidate = compressWithQuality(image, q);
            if (candidate.length <= TARGET_IMAGE_SIZE) {
                log.debug("Подобрано качество {} для лучшего вида при лимите 7 MB, размер={} MB", String.format("%.2f", q), formatMb(candidate.length));
                return candidate;
            }
        }
        return compressed;
    }

    private void logCompressionResult(int originalBytes, int compressedBytes) {
        float ratio = (float) compressedBytes / originalBytes;
        log.info("Сжатие завершено: исходный размер={} MB, сжатый размер={} MB, коэффициент={}",
                formatMb(originalBytes), formatMb(compressedBytes), String.format("%.2f", ratio));
    }

    /**
     * Сжать изображение до заданного максимального размера (в байтах).
     * Используется, когда общий размер запроса должен уложиться в лимит (например, 20 MB на весь body).
     *
     * @param imageBytes исходные байты изображения
     * @param mimeType   MIME тип (image/jpeg, image/png и т.д.)
     * @param maxBytes   максимальный размер результата в байтах
     * @return сжатые байты (JPEG)
     * @throws IOException если не удалось прочитать или записать изображение
     */
    public byte[] compressImageToMaxSize(byte[] imageBytes, String mimeType, long maxBytes) throws IOException {
        if (imageBytes.length <= maxBytes) {
            return imageBytes;
        }
        long target = Math.min(maxBytes, TARGET_IMAGE_SIZE);
        return compressImageToTarget(imageBytes, mimeType, target);
    }

    /**
     * Внутренний метод: сжать до целевого размера в байтах.
     */
    private byte[] compressImageToTarget(byte[] imageBytes, String mimeType, long targetSize) throws IOException {
        BufferedImage image = readImage(imageBytes);
        image = ensureRgb(image);
        if (image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
            image = resizeImage(image, MAX_DIMENSION);
        }
        byte[] compressed = compressWithQuality(image, INITIAL_JPEG_QUALITY);
        if (compressed.length > targetSize) {
            float quality = INITIAL_JPEG_QUALITY;
            while (compressed.length > targetSize && quality >= MIN_JPEG_QUALITY) {
                quality -= QUALITY_STEP;
                compressed = compressWithQuality(image, quality);
            }
            if (compressed.length > targetSize) {
                image = resizeImage(image, (int) (MAX_DIMENSION * 0.75));
                compressed = compressWithQuality(image, MIN_JPEG_QUALITY);
            }
        }
        return compressed;
    }

    /**
     * Изменить размер изображения, сохраняя пропорции.
     *
     * @param image        исходное изображение
     * @param maxDimension максимальный размер по большей стороне
     * @return измененное изображение
     */
    private BufferedImage resizeImage(BufferedImage image, int maxDimension) {
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= maxDimension && height <= maxDimension) {
            return image;
        }

        double scale;
        if (width > height) {
            scale = (double) maxDimension / width;
        } else {
            scale = (double) maxDimension / height;
        }

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    /**
     * Сжать изображение с указанным качеством JPEG.
     *
     * @param image  изображение
     * @param quality качество (0.0 - 1.0)
     * @return сжатые байты
     * @throws IOException если произошла ошибка
     */
    private byte[] compressWithQuality(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Используем ImageWriter для контроля качества
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);

        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        ios.close();

        return baos.toByteArray();
    }
}
