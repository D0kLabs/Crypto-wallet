package com.d0klabs.cryptowalt.data;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface ClassInfoLoader {
    ClassInfo loadClass(String var1) throws ClassNotFoundException;

    ClassInfo newClass(int var1, int var2, int var3, int[] var4, List var5);

    OutputStream outputStreamFor(ClassInfo var1) throws IOException;
}
