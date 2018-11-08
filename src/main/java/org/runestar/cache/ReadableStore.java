package org.runestar.cache;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface ReadableStore extends Closeable {

    CompletableFuture<ByteBuffer> getArchive(int index, int archive) throws IOException;

    default CompletableFuture<Void> transferTo(WritableStore dst) throws IOException {
        return getArchive(0xFF, 0xFF).thenCompose(archive -> {
            var ivs = IndexVersion.readAll(Compressor.decompress(archive, null));
            var fs = new CompletableFuture[ivs.length];
            try {
                for (var i = 0; i < ivs.length; i++) {
                    fs[i] = transferTo(dst, i);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return CompletableFuture.allOf(fs);
        });
    }

    default CompletableFuture<Void> transferTo(WritableStore dst, int index) throws IOException {
        return getArchive(0xFF, index).thenCompose(archive -> {
            var ia = IndexAttributes.read(Compressor.decompress(archive, null));
            var fs = new CompletableFuture[ia.archiveIds.length];
            try {
                dst.setArchive(0xFF, index, archive);
                for (var i = 0; i < ia.archiveIds.length; i++) {
                    fs[i] = transferTo(dst, index, ia.archiveIds[i]);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return CompletableFuture.allOf(fs);
        });
    }

    default CompletableFuture<Void> transferTo(WritableStore dst, int index, int archive) throws IOException {
        return getArchive(index, archive).thenAccept(a -> {
            try {
                dst.setArchive(index, archive, a);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}