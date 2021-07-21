package com.d0klabs.cryptowalt.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResizeableArrayList extends ArrayList implements List, Cloneable, Serializable {
    public ResizeableArrayList(int initialCapacity) {
        super(initialCapacity);
    }

    public ResizeableArrayList() {
    }

    public ResizeableArrayList(Collection c) {
        super(c);
    }

    public void ensureSize(int size) {
        this.ensureCapacity(size);

        while(this.size() < size) {
            this.add((Object)null);
        }

    }

    public Object clone() {
        return super.clone();
    }
}
