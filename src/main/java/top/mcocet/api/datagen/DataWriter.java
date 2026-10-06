package top.mcocet.api.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据生成写入器——provider 的输出句柄。
 * <p>
 * 根路径由加载器为每个 provider 分配
 * ({@code <output>/<modId>/<provider 名>}),写入路径相对于该根;
 * 路径逃逸出输出根(含 {@code ..} 片段)时抛出
 * {@link IllegalArgumentException}。所有写入自动创建父目录。
 */
public final class DataWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path root;
    private int writes;

    public DataWriter(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** provider 输出根目录 */
    public Path root() {
        return root;
    }

    /** 已写入的文件数 */
    public int writes() {
        return writes;
    }

    /** 写入原始字节 */
    public void writeBytes(String path, byte[] data) throws IOException {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        writes++;
    }

    /** 写入 UTF-8 文本 */
    public void writeText(String path, String text) throws IOException {
        writeBytes(path, text.getBytes(StandardCharsets.UTF_8));
    }

    /** 以 2 空格缩进写入 JSON(末尾补换行) */
    public void writeJson(String path, JsonElement json) throws IOException {
        writeText(path, GSON.toJson(json) + "\n");
    }

    private Path resolve(String path) {
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Data provider path escapes its output root: " + path);
        }
        return target;
    }
}
