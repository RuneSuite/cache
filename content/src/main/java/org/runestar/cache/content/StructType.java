package org.runestar.cache.content;

import java.nio.ByteBuffer;
import java.util.Map;

public final class StructType {

    public Map<Integer, Object> params = null;

    public void decode(ByteBuffer buffer) {
        while (true) {
            int opcode = Buf.getUnsignedByte(buffer);
            switch (opcode) {
                case 0:
                    return;
                case 249:
                    params = Buf.decodeParams(buffer);
                    break;
                default:
                    throw new UnsupportedOperationException(Integer.toString(opcode));
            }
        }
    }
}
