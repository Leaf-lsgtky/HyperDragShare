package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FrameworkBinderTransactionResolverTest {
    private static final String STUB_DESCRIPTOR =
            "Landroid/hardware/input/IInputManager$Stub;";

    @Test
    public void resolvesTransactionFromCurrentDexStaticFieldData() throws Exception {
        assertEquals(
                71,
                FrameworkBinderTransactionResolver.findStaticInt(
                        dexWithCancelCurrentTouch(71),
                        STUB_DESCRIPTOR,
                        "TRANSACTION_cancelCurrentTouch"));
    }

    @Test
    public void missingTransactionFieldDoesNotProduceACode() throws Exception {
        assertEquals(
                -1,
                FrameworkBinderTransactionResolver.findStaticInt(
                        dexWithCancelCurrentTouch(71),
                        STUB_DESCRIPTOR,
                        "TRANSACTION_notPresent"));
    }

    private static byte[] dexWithCancelCurrentTouch(int transactionCode) {
        byte[] dex = new byte[320];
        dex[0] = 'd';
        dex[1] = 'e';
        dex[2] = 'x';
        dex[3] = '\n';
        dex[4] = '0';
        dex[5] = '3';
        dex[6] = '9';

        int stringIdsOffset = 112;
        int typeIdsOffset = 124;
        int fieldIdsOffset = 132;
        int classDefsOffset = 140;
        int stringsOffset = 172;
        int descriptorOffset = stringsOffset;
        int typeOffset = writeString(dex, descriptorOffset, STUB_DESCRIPTOR);
        int fieldNameOffset = writeString(dex, typeOffset, "I");
        writeString(dex, fieldNameOffset, "TRANSACTION_cancelCurrentTouch");

        putInt(dex, 0x38, 3);
        putInt(dex, 0x3c, stringIdsOffset);
        putInt(dex, 0x40, 2);
        putInt(dex, 0x44, typeIdsOffset);
        putInt(dex, 0x50, 1);
        putInt(dex, 0x54, fieldIdsOffset);
        putInt(dex, 0x60, 1);
        putInt(dex, 0x64, classDefsOffset);

        putInt(dex, stringIdsOffset, descriptorOffset);
        putInt(dex, stringIdsOffset + 4, typeOffset);
        putInt(dex, stringIdsOffset + 8, fieldNameOffset);
        putInt(dex, typeIdsOffset, 0);
        putInt(dex, typeIdsOffset + 4, 1);
        putShort(dex, fieldIdsOffset, 0);
        putShort(dex, fieldIdsOffset + 2, 1);
        putInt(dex, fieldIdsOffset + 4, 2);

        int classDataOffset = 300;
        int staticValuesOffset = 306;
        putInt(dex, classDefsOffset, 0);
        putInt(dex, classDefsOffset + 24, classDataOffset);
        putInt(dex, classDefsOffset + 28, staticValuesOffset);
        dex[classDataOffset] = 1;
        dex[classDataOffset + 4] = 0;
        dex[classDataOffset + 5] = 0x18;
        dex[staticValuesOffset] = 1;
        dex[staticValuesOffset + 1] = 0x04;
        dex[staticValuesOffset + 2] = (byte) transactionCode;
        return dex;
    }

    private static int writeString(byte[] target, int offset, String value) {
        target[offset++] = (byte) value.length();
        for (int index = 0; index < value.length(); index++) {
            target[offset++] = (byte) value.charAt(index);
        }
        target[offset++] = 0;
        return offset;
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static void putShort(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }
}
