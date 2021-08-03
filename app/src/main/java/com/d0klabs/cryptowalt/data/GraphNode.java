package com.d0klabs.cryptowalt.data;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public abstract class GraphNode {
    protected HashSet succs = new HashSet();
    protected HashSet preds = new HashSet();
    protected int preIndex = -1;
    protected int postIndex = -1;

    public GraphNode() {
    } //TODO: return key and its index on tree

    int preOrderIndex() {
        return this.preIndex;
    } //TODO: use it for GraphNode

    int postOrderIndex() {
        return this.postIndex;
    }//TODO: use it for GraphNode

    void setPreOrderIndex(int index) {
        this.preIndex = index;
    } //TODO: use it for GraphNode

    void setPostOrderIndex(int index) {
        this.postIndex = index;
    }//TODO: use it for GraphNode

    protected Collection succs() {
        return this.succs;
    }

    protected Collection preds() {
        return this.preds;
    }

    public abstract boolean contains(Block pred); //TODO: use it for GraphNode

    public abstract void retainAll(Collection nodes);

    public Iterator iterator() {
        return null;
    } //TODO: use it for GraphNode as visiting index
}
