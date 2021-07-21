package com.d0klabs.cryptowalt.data;

import java.util.Collection;
import java.util.HashSet;

public abstract class GraphNode {
    protected HashSet succs = new HashSet();
    protected HashSet preds = new HashSet();
    protected int preIndex = -1;
    protected int postIndex = -1;

    public GraphNode() {
    }

    int preOrderIndex() {
        return this.preIndex;
    }

    int postOrderIndex() {
        return this.postIndex;
    }

    void setPreOrderIndex(int index) {
        this.preIndex = index;
    }

    void setPostOrderIndex(int index) {
        this.postIndex = index;
    }

    protected Collection succs() {
        return this.succs;
    }

    protected Collection preds() {
        return this.preds;
    }
}
